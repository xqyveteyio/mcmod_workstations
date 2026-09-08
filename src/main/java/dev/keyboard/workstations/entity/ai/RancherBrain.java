package dev.keyboard.workstations.entity.ai;

import dev.keyboard.workstations.work.StationSettings;
import dev.keyboard.workstations.block.MilkBarrelBlockEntity;
import dev.keyboard.workstations.block.RanchBlockEntity;
import dev.keyboard.workstations.entity.RancherEntity;
import dev.keyboard.workstations.work.HerdSurvey;
import dev.keyboard.workstations.work.WorkArea;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.Shearable;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The rancher's whole decision loop, as one explicit state machine driven from
 * {@code mobTick()} rather than from a {@code Goal}.
 *
 * <p>It used to live in a goal, and vanilla's scheduling made it look half asleep.
 * {@code GoalSelector.tick()}, the only thing that ever calls {@code canStart()}, runs on every
 * other tick, so a countdown decremented once per call ticked at half speed and a one second scan
 * interval really meant two. On top of that the goal had to win its MOVE and LOOK controls back
 * from the idle wander goal before it was even asked whether it had work to do.
 *
 * <p>Running here instead means the loop is polled every tick, nothing competes for control, and
 * the current state is a plain field that {@link #describe} can put over the rancher's head.
 */
public class RancherBrain {
	/** What the rancher is doing with itself right now. */
	public enum State {
		NO_STATION,
		IDLE,
		/** Stood where the last job finished, seeing whether another one turns up. */
		WAITING,
		RETURNING,
		WALKING,
		WORKING
	}

	/**
	 * The kind of work being done, one at a time and each carried through to the end before the
	 * next is picked up.
	 *
	 * <p>Jobs used to be chosen one at a time on their own merits, always taking whatever was
	 * nearest. That reads badly from outside: the rancher abandons a half cleared pen to chase a
	 * drop, feeds one pair, wanders off to kill something, and never visibly finishes anything.
	 * A phase gives the work a shape, and it is the phase, not the individual job, that the
	 * rotation moves on from.
	 */
	public enum Phase {
		/** Every surplus adult, down to the herd's keep count. */
		CULL,
		/** Every drop on the ground, then what was gathered emptied into the station. */
		COLLECT,
		/** One pairing. Handing the rotation a turn between pairings keeps the pen from stampeding. */
		BREED,
		/** One helping for each baby in the area, in turn. */
		GROW,
		/** Shearing and milking, each animal getting one turn. */
		HARVEST
	}

	/** A single errand inside a phase. */
	public enum Job {
		/** Put an adult in love so vanilla's mate goal pairs it off. */
		FEED,
		/** Feed a baby to grow it up early. */
		GROW,
		CULL,
		SHEAR,
		MILK,
		COLLECT,
		DEPOSIT
	}

	/**
	 * The order phases are worked through.
	 *
	 * <p>A sweep is in the rotation in its own right as well as being forced after culling and
	 * harvesting, so drops that turn up on their own, an egg or a chicken something else killed,
	 * are not left lying there until the next slaughter.
	 */
	private static final Phase[] ROTATION = {
			Phase.CULL, Phase.COLLECT, Phase.BREED, Phase.GROW, Phase.HARVEST};

	/** Two blocks, which is roughly a player's reach. */
	private static final double REACH_SQUARED = 4.0;
	/**
	 * How near a path has to get a drop to count as reaching it, in blocks.
	 *
	 * <p>Not zero, because {@code findPathTo} aims at the target's own block and a drop that rolled
	 * up against a fence does not sit on a block anything can stand on. Wedged in the fence it gets
	 * squeezed about, so its centre lands either inside the fence itself or, once it settles a
	 * fraction below ground level, inside the solid block holding the fence up. Both are impossible
	 * to path into, which is why those drops were written off as unreachable.
	 *
	 * <p>Only drops get this, and even for them only as a second attempt after aiming at the exact
	 * block has failed: see {@link #pathTo}. An animal stands on ground the rancher could stand on
	 * too, so its own block is a perfectly good target, and loosening it anywhere it is not needed
	 * causes a deadlock: pathfinding calls the walk finished while the target is still further away
	 * than reach allows, leaving the rancher stood in the open looking at something it will not
	 * close the last stride on.
	 */
	private static final int COLLECT_PATH_DISTANCE = 2;
	/** An animal's own block is always somewhere the rancher can stand, so aim right at it. */
	private static final int ANIMAL_PATH_DISTANCE = 0;
	/**
	 * Reach for picking things up: enough for a drop in a neighbouring cell wherever in that cell it
	 * has settled, and no further. Diagonally that is 1.41 blocks between centres plus up to 0.7
	 * across the cell, so 2.5.
	 *
	 * <p>Longer than {@link #REACH_SQUARED} because a drop wedged against a fence can only ever be
	 * approached from the cell next door, and the last stretch of that walk is not always available
	 * either, which is what left those drops lying there.
	 *
	 * <p>Deliberately not longer than that. At three blocks the rancher could stand outside a pen
	 * and lean over the fence for drops two cells in, so it never had reason to go through the gate
	 * at all. Anything further away has to be walked to.
	 */
	private static final double COLLECT_REACH_SQUARED = 6.25;
	/**
	 * Ticks without getting meaningfully closer before a target is given up on.
	 *
	 * <p>Progress is the signal, not whether a path exists. A rancher can hold a perfectly good
	 * looking path whose last node it is never able to enter, and sit there the full
	 * {@link #JOB_TIMEOUT} without moving an inch.
	 */
	private static final int STALL_LIMIT = 60;
	/** Blocks of closing distance that count as progress rather than jitter. */
	private static final double STALL_PROGRESS = 0.25;
	private static final double STATION_REACH_SQUARED = 6.25;
	private static final int REPATH_INTERVAL = 10;
	/** How long the rancher chases one target before writing it off, in ticks. */
	private static final int JOB_TIMEOUT = 400;
	/** How long a target that turned out to be unreachable is passed over, in ticks. */
	private static final int BLOCKED_COOLDOWN = 200;
	/** Paths tried per scan before the rancher gives up and waits for the next one. */
	private static final int MAX_PATH_CHECKS = 3;
	private static final int ATTACK_INTERVAL = 12;
	/**
	 * How near the station counts as being at its post, squared. Loose enough that the rancher is
	 * not forever correcting its footing after being jostled, tight enough to be beside the block.
	 */
	private static final double POST_REACH_SQUARED = 4.0;
	/**
	 * How long the rancher stands where it finished before setting off back to its post, in ticks.
	 *
	 * <p>It used to leave the instant a job ended. The next scan for work is up to a whole work
	 * interval away, so the usual sight was the rancher getting half way home, finding something,
	 * and turning straight round: busy looking, and a lot of walking that came to nothing.
	 *
	 * <p>Standing still costs nothing and is strictly better than walking the wrong way. Whatever
	 * turns up next is started from where the rancher already is, rather than from wherever an
	 * abandoned walk home happened to leave it.
	 */
	private static final int SETTLE_TICKS = 60;
	private static final double WALK_SPEED = 0.6;
	private static final double RETURN_SPEED = 0.45;

	/**
	 * Ticks in water to allow pathfinding before the rancher is steered home by hand. Long enough
	 * that wading across a stream on a perfectly good path is left alone.
	 */
	private static final int SWIM_PATIENCE = 40;

	/**
	 * Targets that turned out to be unreachable, by entity id, each held until the world time it
	 * maps to. Without this a drop that landed outside the fence is an infinite loop: it is the
	 * nearest thing on every scan, so the rancher walks at the fence, times out, and picks it
	 * straight back up.
	 */
	private final Int2LongMap blocked = new Int2LongOpenHashMap();

	private State state = State.IDLE;
	@Nullable
	private Phase phase;
	/** Where in {@link #ROTATION} the next phase comes from. */
	private int rotationCursor;
	/** A sweep culling or harvesting has earned, taken before the rotation gets its turn back. */
	private boolean sweepOwed;
	/** Whether the current phase has actually accomplished anything, which is what earns a sweep. */
	private boolean phaseWorked;
	/** Ids already served this phase, so a round gives every animal one turn and no more. */
	private final IntSet served = new IntOpenHashSet();
	/** Set when the station had no room, so a sweep stops retrying a deposit that cannot land. */
	private boolean stationFull;
	/**
	 * Set when pathfinding declined to answer rather than saying no. That is a fact about the
	 * rancher, usually that it is mid stride, and must not be read as the phase having run out of
	 * work: doing so ended phases early and left culls half finished.
	 */
	private boolean pathPending;
	/** One look at the herd per scan, shared by whichever phases get asked during it. */
	@Nullable
	private HerdSurvey scanSurvey;
	@Nullable
	private Job job;
	@Nullable
	private Entity target;
	@Nullable
	private BlockPos targetPos;
	/** The other half of a committed pairing, served straight after the first without re-deciding. */
	@Nullable
	private AnimalEntity pairPartner;
	/** Why the last scan came up empty, for the label over the rancher's head. */
	private String note = "";
	private int scanCooldown;
	private int repathCooldown;
	private int actionCooldown;
	private int timeout;
	/** Consecutive ticks of getting no closer to the target. */
	private int stalled;
	/** Consecutive ticks touching water, for the label and for noticing a swim going nowhere. */
	private int swimming;
	/** Ticks since the last job ended, which is how long there has been nothing to do. */
	private int settling;
	/** Closest the rancher has been to the current target, for spotting a walk going nowhere. */
	private double closest = Double.MAX_VALUE;

	public void tick(RancherEntity rancher) {
		if (!(rancher.getWorld() instanceof ServerWorld world)) {
			return;
		}

		RanchBlockEntity station = rancher.getStation();
		WorkArea area = rancher.getWorkArea();

		if (station == null || area == null) {
			clearJob(rancher);
			// Whatever round it was part way through belongs to a station that is no longer there.
			phase = null;
			served.clear();
			state = State.NO_STATION;
			note = "";
			return;
		}

		long now = world.getTime();
		blocked.int2LongEntrySet().removeIf(entry -> entry.getLongValue() <= now);

		// The state machine has to keep running in water. SwimGoal takes the JUMP control alone and
		// never steers, so handing movement over to it leaves nothing at all moving the rancher.
		// What it does give us is a navigation set to allow swimming, so a path out can exist.
		swimming = rancher.isTouchingWater() ? swimming + 1 : 0;

		// Out of its depth there may be no node for pathfinding to offer. Steering by hand needs no
		// path and only has to reach a bank the navigation can work from again.
		if (swimming > SWIM_PATIENCE && rancher.getNavigation().isIdle()) {
			BlockPos post = area.getCenter();
			rancher.getMoveControl().moveTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, RETURN_SPEED);
		}

		if (job != null) {
			runJob(rancher, world, area, station);
			return;
		}

		if (scanCooldown > 0) {
			scanCooldown--;
		} else {
			scanCooldown = station.getSettings().workIntervalTicks;

			if (chooseJob(rancher, world, area, station)) {
				timeout = JOB_TIMEOUT;
				repathCooldown = 0;
				actionCooldown = 0;
				stalled = 0;
				settling = 0;
				closest = Double.MAX_VALUE;
				state = State.WALKING;
				navigate(rancher);
				return;
			}
		}

		idle(rancher, area, station.getSettings());
	}

	/** A one line summary of the state machine, short enough to sit over the rancher's head. */
	public String describe(RancherEntity rancher) {
		StringBuilder text = new StringBuilder(switch (state) {
			case NO_STATION -> "no station";
			case IDLE -> "idle";
			case WAITING -> "waiting";
			case RETURNING -> "heading back";
			case WALKING -> "walking";
			case WORKING -> "working";
		});

		// The countdown to giving up and walking home, so a rancher stood in a field can be told
		// apart from one that is stuck.
		if (state == State.WAITING) {
			text.append(' ').append(Math.max(0, settleTicks(rancher.getSettings()) - settling));
		}

		if (phase != null) {
			text.append(' ').append(phase.name().toLowerCase(Locale.ROOT));
		}

		// Only when it says something the phase has not already. Most jobs share their phase's
		// name, and "walking cull cull" reads like a bug.
		if (job != null && (phase == null || !job.name().equals(phase.name()))) {
			text.append(phase == null ? ' ' : '/').append(job.name().toLowerCase(Locale.ROOT));
		}

		if (job == Job.FEED) {
			text.append(pairPartner == null ? " 2/2" : " 1/2");
		}

		if (state == State.WALKING) {
			text.append(String.format(Locale.ROOT, " %.1fm t%d", distanceToTarget(rancher), timeout));

			// Which node of the path it is on: a target accepted as reachable but a walk that never
			// starts looks identical to plain sluggishness without this.
			Path path = rancher.getNavigation().getCurrentPath();
			text.append(path == null
					? " nopath"
					: String.format(Locale.ROOT, " n%d/%d", path.getCurrentNodeIndex(), path.getLength()));
		}

		// Water slows the rancher down, so it is the first thing to suspect when a walk takes longer
		// than it should.
		if (swimming > 0) {
			text.append(" | water");
		}

		if (job == null && !note.isEmpty()) {
			text.append(" | ").append(note);
		}

		if (!served.isEmpty()) {
			text.append(" | done ").append(served.size());
		}

		if (sweepOwed) {
			text.append(" | sweep due");
		}

		if (!blocked.isEmpty()) {
			text.append(" | skip ").append(blocked.size());
		}

		int feedCooldown = rancher.getFeedCooldown();
		int cullCooldown = rancher.getCullCooldown();

		if (job == null && feedCooldown > 0) {
			text.append(" | feed ").append(feedCooldown);
		}

		if (job == null && cullCooldown > 0) {
			text.append(" | cull ").append(cullCooldown);
		}

		int carried = countCarried(rancher);

		if (carried > 0) {
			text.append(" | pack ").append(carried).append('/').append(RancherEntity.CARRY_SLOTS);
		}

		return text.toString();
	}

	private void runJob(RancherEntity rancher, ServerWorld world, WorkArea area, RanchBlockEntity station) {
		if (!jobValid(area)) {
			note = "target gone";
			clearJob(rancher);
			return;
		}

		if (target != null) {
			rancher.getLookControl().lookAt(target, 30.0F, 30.0F);
		} else if (targetPos != null) {
			rancher.getLookControl().lookAt(Vec3d.ofCenter(targetPos));
		}

		if (actionCooldown > 0) {
			actionCooldown--;
		}

		if (withinReach(rancher)) {
			state = State.WORKING;
			rancher.getNavigation().stop();

			// Arriving settles the question the timeout and the stall detector were both asking,
			// so both start again from scratch. Without this, an animal that grazes a couple of
			// blocks off while the interval runs down is measured against how close the rancher
			// once stood to it, and gets written off as unreachable during the walk back.
			timeout = JOB_TIMEOUT;
			stalled = 0;
			closest = Double.MAX_VALUE;

			// The clock is deliberately not running here. It exists to give up on a walk that is
			// going nowhere, and having arrived, waiting out the configured interval is the rancher
			// doing as it was told. The interval can also be set longer than the timeout, which
			// otherwise meant standing over an animal until the job expired and then blocklisting
			// it for being unreachable, having been next to it the whole time.
			if (actionCooldown <= 0 && actionReady(rancher)) {
				perform(rancher, station);
			}

			return;
		}

		if (--timeout <= 0) {
			// Never got there. The target is passed over for a while so the rancher does not spend
			// every scan from here on walking at the same thing it cannot reach.
			if (target != null) {
				block(world, target);
			}

			note = "timed out";
			clearJob(rancher);
			return;
		}

		state = State.WALKING;

		if (--repathCooldown <= 0) {
			repathCooldown = REPATH_INTERVAL;
			navigate(rancher);
		}

		// Time spent waiting on a gate is not the target's fault. The gate gives up on a blocked
		// doorway and pauses before trying again, which on its own outlasts STALL_LIMIT, and
		// counting that was enough to make the rancher write off the animal it was walking to.
		if (rancher.isWorkingGate()) {
			return;
		}

		// Getting no closer means as close as the world allows, which is the point to give up rather
		// than stand there for the rest of the timeout.
		double distance = distanceToTarget(rancher);

		if (distance < closest - STALL_PROGRESS) {
			closest = distance;
			stalled = 0;
			return;
		}

		if (++stalled > STALL_LIMIT) {
			if (target != null) {
				block(world, target);
			}

			note = "out of reach";
			clearJob(rancher);
		}
	}

	private boolean jobValid(WorkArea area) {
		if (job == Job.DEPOSIT) {
			return targetPos != null;
		}

		return target != null && target.isAlive() && !target.isRemoved() && area.contains(target);
	}

	private void perform(RancherEntity rancher, RanchBlockEntity station) {
		Job current = job;

		if (current == null) {
			return;
		}

		boolean done = switch (current) {
			case FEED -> feed(rancher, station, false);
			case GROW -> feed(rancher, station, true);
			case CULL -> cull(rancher);
			case SHEAR -> shear(rancher);
			case MILK -> milk(rancher, station);
			case COLLECT -> collect(rancher);
			case DEPOSIT -> deposit(rancher, station);
		};

		if (done) {
			clearJob(rancher);
		}
	}

	/**
	 * Whether the configured pacing allows the job to go ahead. Asked on arrival rather than when
	 * the job was picked, so the walk over happens during the wait instead of after it.
	 */
	private boolean actionReady(RancherEntity rancher) {
		if (job == null) {
			return false;
		}

		return switch (job) {
			case CULL -> rancher.canCullNow();
			case FEED, GROW -> rancher.canFeedNow();
			default -> true;
		};
	}

	private void clearJob(RancherEntity rancher) {
		job = null;
		target = null;
		targetPos = null;
		pairPartner = null;
		actionCooldown = 0;

		if (state != State.NO_STATION) {
			state = State.IDLE;
			rancher.getNavigation().stop();
		}
	}

	/**
	 * With no work to do the rancher waits at its station rather than wandering the work area.
	 *
	 * <p>Standing still is not just cosmetic. A worker drifting around looks busy while doing
	 * nothing, it will not be in the same place twice when you go looking for it, and every stroll
	 * pushes livestock about, which for animals in love means being nudged away from the partner
	 * they were walking to.
	 */
	private void idle(RancherEntity rancher, WorkArea area, StationSettings config) {
		settling++;
		BlockPos post = area.getCenter();

		if (rancher.squaredDistanceTo(Vec3d.ofCenter(post)) <= POST_REACH_SQUARED) {
			state = State.IDLE;

			// Stopped explicitly, or the walk home would carry on pushing it past the station.
			if (!rancher.getNavigation().isIdle()) {
				rancher.getNavigation().stop();
			}

			return;
		}

		// Not on the way anywhere yet: the job only just ended and the ranch has not been looked
		// over since. Setting off now is what produced the half walk home and the about turn.
		if (settling < settleTicks(config)) {
			state = State.WAITING;

			if (!rancher.getNavigation().isIdle()) {
				rancher.getNavigation().stop();
			}

			return;
		}

		state = State.RETURNING;

		// Only issued once: reissuing every tick restarts the path and the rancher never sets off.
		// A finished hop leaves navigation idle again, which is what advances a staged walk home.
		if (rancher.getNavigation().isIdle()
				&& !WorkerMovement.approach(rancher, Vec3d.ofCenter(post), RETURN_SPEED)) {
			rancher.getNavigation().startMovingTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, RETURN_SPEED);
		}
	}

	/**
	 * How long to stand still before heading home, in ticks.
	 *
	 * <p>Scaled off the work interval as well as fixed, because "nothing to do" is only ever
	 * established by a scan coming up empty, and scans are what the interval paces. A flat wait
	 * shorter than the interval could send the rancher home without a single look around, which is
	 * the behaviour this is here to stop.
	 */
	private static int settleTicks(StationSettings config) {
		return Math.max(SETTLE_TICKS, config.workIntervalTicks * 2);
	}

	private boolean chooseJob(RancherEntity rancher, ServerWorld world, WorkArea area, RanchBlockEntity station) {
		StationSettings config = station.getSettings();
		note = "";
		scanSurvey = null;

		// A full pack interrupts whatever is running. Carrying on would mean killing animals and
		// shearing sheep whose drops there is nowhere left to put.
		if (isPackFull(rancher)) {
			return takeDeposit(area);
		}

		if (phase != null) {
			if (takeJobIn(rancher, world, area, station, config)) {
				return true;
			}

			if (pathPending) {
				return false;
			}

			endPhase();
		}

		// One turn round the rotation, with a spare go for the sweep that culling or harvesting
		// may have just earned. Every phase gets asked, so a quiet ranch still reaches the one
		// thing that does have work waiting.
		for (int attempt = 0; attempt <= ROTATION.length; attempt++) {
			startPhase(nextPhase(config));

			if (takeJobIn(rancher, world, area, station, config)) {
				return true;
			}

			if (pathPending) {
				return false;
			}

			endPhase();
		}

		if (!rancher.getCarried().isEmpty()) {
			return takeDeposit(area);
		}

		if (note.isEmpty()) {
			note = "nothing to do";
		}

		return false;
	}

	/** The next phase to try: a sweep if one is owed, otherwise the next enabled one in turn. */
	private Phase nextPhase(StationSettings config) {
		if (sweepOwed) {
			sweepOwed = false;
			return Phase.COLLECT;
		}

		for (int step = 0; step < ROTATION.length; step++) {
			Phase candidate = ROTATION[rotationCursor];
			rotationCursor = (rotationCursor + 1) % ROTATION.length;

			if (enabled(candidate, config)) {
				return candidate;
			}
		}

		// Sweeping is the one phase with no switch, so there is always somewhere to land.
		return Phase.COLLECT;
	}

	private static boolean enabled(Phase phase, StationSettings config) {
		return switch (phase) {
			case CULL -> config.enableCulling;
			case COLLECT -> true;
			case BREED -> config.enableBreeding;
			case GROW -> config.feedBabies;
			case HARVEST -> config.enableShearing || config.enableMilking;
		};
	}

	private void startPhase(Phase next) {
		phase = next;
		phaseWorked = false;
		stationFull = false;
		served.clear();
	}

	private void endPhase() {
		// A slaughtered animal leaves its drops where it fell and shearing scatters wool, so these
		// two hand over to a sweep rather than to whatever the rotation had lined up next. Only
		// when something actually happened: a phase that found nothing to do owes nothing.
		if (phaseWorked && (phase == Phase.CULL || phase == Phase.HARVEST)) {
			sweepOwed = true;
		}

		phase = null;
		phaseWorked = false;
		served.clear();
	}

	/** The next errand within the current phase, or false when the phase has nothing left. */
	private boolean takeJobIn(RancherEntity rancher, ServerWorld world, WorkArea area,
			RanchBlockEntity station, StationSettings config) {
		pathPending = false;

		if (phase == null) {
			return false;
		}

		return switch (phase) {
			case CULL -> takeCull(rancher, world, area, config);
			case COLLECT -> takeCollect(rancher, world, area);
			case BREED -> takeBreed(rancher, world, area, station, config);
			case GROW -> takeGrow(rancher, world, area, station, config);
			case HARVEST -> takeHarvest(rancher, world, area, station, config);
		};
	}

	private boolean takeCull(RancherEntity rancher, ServerWorld world, WorkArea area, StationSettings config) {
		AnimalEntity victim = nearestReachable(rancher, world,
				survey(world, area).cullCandidates(config), ANIMAL_PATH_DISTANCE);
		return victim != null && take(Job.CULL, victim);
	}

	private boolean takeCollect(RancherEntity rancher, ServerWorld world, WorkArea area) {
		ItemEntity drop = nearestReachable(rancher, world, world.getEntitiesByClass(ItemEntity.class, area.getBox(),
				item -> item.isAlive() && !item.cannotPickup() && rancher.getCarried().canInsert(item.getStack())),
				COLLECT_PATH_DISTANCE);

		if (drop != null) {
			return take(Job.COLLECT, drop);
		}

		// The sweep is not finished until what was picked up is in the station, so whatever phase
		// comes next starts with an empty pack and room for what it produces.
		if (!stationFull && !rancher.getCarried().isEmpty()) {
			return takeDeposit(area);
		}

		return false;
	}

	private boolean takeBreed(RancherEntity rancher, ServerWorld world, WorkArea area,
			RanchBlockEntity station, StationSettings config) {
		// One pairing is the whole phase. Breeding a pen out to its limit in a single stretch would
		// starve everything else, and the animals just put in love need time to find each other.
		if (phaseWorked) {
			return false;
		}

		HerdSurvey.FeedPlan plan = nearestPlan(rancher, world,
				survey(world, area).feedPlans(config), station, config);
		return plan != null && takeFeed(plan);
	}

	private boolean takeGrow(RancherEntity rancher, ServerWorld world, WorkArea area,
			RanchBlockEntity station, StationSettings config) {
		AnimalEntity baby = nearestReachable(rancher, world,
				filterFeedable(unserved(survey(world, area).babyCandidates()), station, config),
				ANIMAL_PATH_DISTANCE);
		return baby != null && take(Job.GROW, baby);
	}

	private boolean takeHarvest(RancherEntity rancher, ServerWorld world, WorkArea area,
			RanchBlockEntity station, StationSettings config) {
		HerdSurvey survey = survey(world, area);

		if (config.enableShearing) {
			AnimalEntity woolly = nearestReachable(rancher, world,
					unserved(survey.shearCandidates()), ANIMAL_PATH_DISTANCE);

			if (woolly != null) {
				return take(Job.SHEAR, woolly);
			}
		}

		// Milk with nowhere to go is milk poured away, so a cow is left unmilked until there is a
		// barrel with room in it. Asked here rather than on arrival so that the rancher spends the
		// phase on something useful instead of walking out to a cow it will have to turn down.
		if (config.enableMilking && MilkBarrelBlockEntity.adjoining(world, station.getPos()) != null) {
			AnimalEntity cow = nearestReachable(rancher, world,
					unserved(survey.milkCandidates()), ANIMAL_PATH_DISTANCE);

			if (cow != null) {
				return take(Job.MILK, cow);
			}
		}

		return false;
	}

	private HerdSurvey survey(ServerWorld world, WorkArea area) {
		if (scanSurvey == null) {
			scanSurvey = HerdSurvey.of(world, area);
		}

		return scanSurvey;
	}

	/** Whatever has not had its turn yet this phase, which is what makes a round finite. */
	private <T extends Entity> List<T> unserved(List<T> candidates) {
		List<T> waiting = new ArrayList<>(candidates.size());

		for (T candidate : candidates) {
			if (!served.contains(candidate.getId())) {
				waiting.add(candidate);
			}
		}

		return waiting;
	}

	private boolean take(Job newJob, Entity newTarget) {
		job = newJob;
		target = newTarget;
		targetPos = null;
		pairPartner = null;
		return true;
	}

	private boolean takeFeed(HerdSurvey.FeedPlan plan) {
		job = Job.FEED;
		target = plan.first();
		targetPos = null;
		pairPartner = plan.second();
		return true;
	}

	private boolean takeDeposit(WorkArea area) {
		job = Job.DEPOSIT;
		target = null;
		targetPos = area.getCenter();
		pairPartner = null;
		return true;
	}

	/**
	 * The closest pairing the rancher can both afford and walk to. Plans are taken whole or not at
	 * all, so a station down to its last wheat starts nothing rather than half a pairing.
	 */
	@Nullable
	private HerdSurvey.FeedPlan nearestPlan(RancherEntity rancher, ServerWorld world,
			List<HerdSurvey.FeedPlan> plans, Inventory station, StationSettings config) {
		List<AnimalEntity> heads = new ArrayList<>(plans.size());

		for (HerdSurvey.FeedPlan plan : plans) {
			if (affordable(plan, station, config)) {
				heads.add(plan.first());
			}
		}

		AnimalEntity head = nearestReachable(rancher, world, heads, ANIMAL_PATH_DISTANCE);

		if (head == null) {
			return null;
		}

		for (HerdSurvey.FeedPlan plan : plans) {
			if (plan.first() == head) {
				return plan;
			}
		}

		return null;
	}

	/** Whether the station holds a portion for every animal in the plan. */
	private static boolean affordable(HerdSurvey.FeedPlan plan, Inventory station, StationSettings config) {
		if (!config.requireFeedItems) {
			return true;
		}

		int found = 0;

		for (int slot = 0; slot < station.size(); slot++) {
			ItemStack stack = station.getStack(slot);

			// Both halves are the same species, so one animal's taste speaks for the pair.
			if (!stack.isEmpty() && plan.first().isBreedingItem(stack)) {
				found += stack.getCount();

				if (found >= plan.portions()) {
					return true;
				}
			}
		}

		return false;
	}

	/** Closest candidate the rancher can actually walk up to, nearest tried first. */
	@Nullable
	private <T extends Entity> T nearestReachable(RancherEntity rancher, ServerWorld world, List<T> candidates,
			int pathDistance) {
		long now = world.getTime();
		List<T> queue = new ArrayList<>(candidates.size());

		for (T candidate : candidates) {
			if (blocked.get(candidate.getId()) <= now) {
				queue.add(candidate);
			}
		}

		if (queue.isEmpty()) {
			return null;
		}

		queue.sort(Comparator.comparingDouble(rancher::squaredDistanceTo));

		// Each miss costs a pathfind, so a scan only probes the few nearest and leaves the rest for
		// later. Anything ruled out goes on the blocked list, so the next scan starts further down.
		for (int index = 0; index < Math.min(queue.size(), MAX_PATH_CHECKS); index++) {
			T candidate = queue.get(index);

			// Beyond pathfinding's reach the answer comes back "no" whatever the ground is like, so
			// there is nothing worth asking. Such a candidate is accepted and walked at in stages;
			// if it does turn out to be unreachable, the stall detector writes it off once the
			// rancher is near enough for a refusal to actually mean something.
			if (WorkerMovement.isFarOff(rancher, candidate.getPos())) {
				return candidate;
			}

			Path path = rancher.getNavigation().findPathTo(candidate, pathDistance);

			if (path == null) {
				// Pathfinding declines to answer at all while the rancher is off the ground, which
				// happens constantly to a walking mob. That is a fact about the rancher and not
				// about the target, so nothing may be written off here: blaming the target was what
				// made the rancher blocklist every drop in sight and then stand around doing
				// nothing for the ten seconds it took to expire.
				note = "no path yet";
				pathPending = true;
				return null;
			}

			if (path.reachesTarget()) {
				return candidate;
			}

			block(world, candidate);
			note = "unreachable";
		}

		return null;
	}

	private void block(ServerWorld world, Entity blockedTarget) {
		blocked.put(blockedTarget.getId(), world.getTime() + BLOCKED_COOLDOWN);
	}

	private boolean feed(RancherEntity rancher, RanchBlockEntity station, boolean growUp) {
		if (!(target instanceof AnimalEntity animal)) {
			return true;
		}

		StationSettings config = station.getSettings();

		if (config.requireFeedItems) {
			int slot = findFeedSlot(station, animal);

			if (slot < 0) {
				note = "no feed";
				return true;
			}

			ItemStack eaten = station.removeStack(slot, 1);
			Item remainder = eaten.getItem().getRecipeRemainder();

			if (remainder != null) {
				// Buckets and bottles come back rather than vanishing into the animal.
				keepOrDrop(rancher, new ItemStack(remainder));
			}

			station.markDirty();
		}

		rancher.swingHand(Hand.MAIN_HAND);

		if (growUp) {
			animal.growUp(PassiveEntity.toGrowUpAge(-animal.getBreedingAge()), true);
		} else {
			animal.lovePlayer(null);
		}

		// Its turn is used up. For babies that is what makes a round finite, and it also stops a
		// baby that grew to adulthood on this very helping from being served again as an adult.
		served.add(animal.getId());
		phaseWorked = true;
		celebrate(rancher, animal);

		// The animal just fed is on a 600 tick timer and is no use on its own, so the partner is
		// walked to inside this same job. Handing it back to the next scan is what lost the pairing:
		// by the time the scan came round the two could be past the eight block mate search range,
		// and a herd with nobody in range yields no candidates at all.
		if (pairPartner != null) {
			AnimalEntity partner = pairPartner;
			pairPartner = null;

			if (stillReady(partner)) {
				target = partner;
				timeout = JOB_TIMEOUT;
				repathCooldown = 0;
				actionCooldown = 0;
				stalled = 0;
				closest = Double.MAX_VALUE;
				state = State.WALKING;
				navigate(rancher);
				return false;
			}
		}

		// The interval paces whole pairings rather than single portions, so it starts once the pair
		// has been served.
		rancher.startFeedCooldown();
		return true;
	}

	private static boolean stillReady(AnimalEntity animal) {
		return animal.isAlive() && !animal.isRemoved() && animal.getBreedingAge() == 0 && animal.canEat();
	}

	private boolean cull(RancherEntity rancher) {
		if (!(target instanceof AnimalEntity animal)) {
			return true;
		}

		rancher.swingHand(Hand.MAIN_HAND);

		if (rancher.getSettings().instantKill) {
			// Still dealt as damage rather than by emptying the health bar, so the loot table, the
			// looting on the rancher's sword and the death animation all behave as they always do.
			// The headroom over max health is for anything wearing armour or under resistance.
			animal.damage(rancher.getDamageSources().mobAttack(rancher),
					animal.getMaxHealth() * 10.0F + animal.getAbsorptionAmount() + 10.0F);
		} else {
			rancher.tryAttack(animal);
		}

		actionCooldown = ATTACK_INTERVAL;

		if (animal.isAlive()) {
			return false;
		}

		// The interval paces one animal to the next, so it starts on the blow that finished this
		// one rather than on merely having swung at it.
		rancher.startCullCooldown();
		// What it dropped is left where it fell. Culling always hands over to a sweep, so the
		// carcass is collected as part of finishing the same piece of work.
		phaseWorked = true;
		return true;
	}

	/**
	 * Takes the coat off anything wearing one. No shears are spent or even carried: the rancher is
	 * equipment the station summons, and asking players to keep it stocked with tools would make a
	 * ranch stop working for a reason nothing on the screen explains.
	 */
	private boolean shear(RancherEntity rancher) {
		if (!(target instanceof AnimalEntity animal)) {
			return true;
		}

		// Marked as served whatever happens next, so an animal that turns out not to need it after
		// all cannot be picked again and stall the round.
		served.add(animal.getId());

		if (!(animal instanceof Shearable shearable) || !shearable.isShearable()) {
			return true;
		}

		rancher.swingHand(Hand.MAIN_HAND);
		// Vanilla's own routine, so the sound, the drop count and a mooshroom turning into a cow
		// all behave exactly as they do for a player holding shears.
		shearable.sheared(SoundCategory.NEUTRAL);
		actionCooldown = ATTACK_INTERVAL;
		phaseWorked = true;
		return true;
	}

	/**
	 * Empties a cow into the barrel standing against the station.
	 *
	 * <p>The milk goes to the barrel rather than into the rancher's pack, which is the whole point
	 * of the barrel: a bucket takes a slot each and a pen of cows would bury the station in them
	 * within a few rounds, while a barrel swallows the lot and hands it back a bucket at a time to
	 * whoever comes for it.
	 *
	 * <p>The barrel is looked up again here rather than remembered from when the job was taken. It
	 * is a walk away, and in the meantime it can be filled by another station, emptied by a player,
	 * or broken outright, so the only reading worth acting on is the one taken at the moment the
	 * milk needs somewhere to go.
	 */
	private boolean milk(RancherEntity rancher, RanchBlockEntity station) {
		if (!(target instanceof AnimalEntity animal)) {
			return true;
		}

		served.add(animal.getId());

		if (!(animal instanceof CowEntity) || animal.isBaby()) {
			return true;
		}

		MilkBarrelBlockEntity barrel = MilkBarrelBlockEntity.adjoining(rancher.getWorld(), station.getPos());

		if (barrel == null || !barrel.fill()) {
			note = "no room for milk";
			return true;
		}

		rancher.swingHand(Hand.MAIN_HAND);
		animal.playSound(SoundEvents.ENTITY_COW_MILK, 1.0F, 1.0F);
		actionCooldown = ATTACK_INTERVAL;
		phaseWorked = true;
		return true;
	}

	private boolean collect(RancherEntity rancher) {
		if (!(target instanceof ItemEntity item) || item.cannotPickup()) {
			return true;
		}

		ItemStack remainder = rancher.getCarried().addStack(item.getStack().copy());

		if (remainder.isEmpty()) {
			item.discard();
		} else {
			item.setStack(remainder);
		}

		rancher.getWorld().playSound(null, rancher.getBlockPos(), SoundEvents.ENTITY_ITEM_PICKUP,
				SoundCategory.NEUTRAL, 0.15F,
				(rancher.getRandom().nextFloat() - rancher.getRandom().nextFloat()) * 1.4F + 2.0F);
		phaseWorked = true;
		return true;
	}

	private boolean deposit(RancherEntity rancher, RanchBlockEntity station) {
		SimpleInventory carried = rancher.getCarried();
		boolean moved = false;

		for (int slot = 0; slot < carried.size(); slot++) {
			ItemStack stack = carried.getStack(slot);

			if (stack.isEmpty()) {
				continue;
			}

			int before = stack.getCount();
			ItemStack left = insert(station, stack);
			carried.setStack(slot, left.isEmpty() ? ItemStack.EMPTY : left);

			if (left.getCount() != before) {
				moved = true;
			}
		}

		if (moved) {
			rancher.swingHand(Hand.MAIN_HAND);
			station.markDirty();
		} else if (!carried.isEmpty()) {
			note = "station full";
			// Remembered for the rest of the phase, or a sweep that has cleared the ground would
			// keep taking the same deposit that has nowhere to go and never finish.
			stationFull = true;
		}

		// A full station leaves the pack loaded; the next pass tries again after the work interval.
		return true;
	}

	private boolean withinReach(RancherEntity rancher) {
		if (job == Job.DEPOSIT) {
			return targetPos != null
					&& rancher.squaredDistanceTo(Vec3d.ofCenter(targetPos)) <= STATION_REACH_SQUARED;
		}

		if (target == null) {
			return false;
		}

		return rancher.squaredDistanceTo(target)
				<= (job == Job.COLLECT ? COLLECT_REACH_SQUARED : REACH_SQUARED);
	}

	private void navigate(RancherEntity rancher) {
		if (job == Job.DEPOSIT) {
			if (targetPos == null) {
				return;
			}

			if (!WorkerMovement.approach(rancher, Vec3d.ofCenter(targetPos), WALK_SPEED)) {
				// This overload already settles for a block next to the target, which it has to:
				// the station itself is solid and can only ever be walked up to.
				rancher.getNavigation().startMovingTo(targetPos.getX() + 0.5, targetPos.getY(),
						targetPos.getZ() + 0.5, WALK_SPEED);
			}

			return;
		}

		if (target == null || WorkerMovement.approach(rancher, target.getPos(), WALK_SPEED)) {
			return;
		}

		// Pathed by hand rather than through startMovingTo(Entity, speed), which always asks for a
		// path right onto the target's own block. For a drop resting against a fence that block is
		// the fence, so the walk would silently never start, leaving the rancher stood still with a
		// job it had already accepted as reachable.
		Path path = pathTo(rancher, target);

		if (path != null) {
			rancher.getNavigation().startMovingAlong(path, WALK_SPEED);
		}
	}

	/**
	 * A path right up to {@code destination}, settling for a block near it only if that fails.
	 *
	 * <p>Two tries rather than one, because the slack a drop resting inside a fence needs is
	 * ruinous everywhere else. Offered a path that may stop {@link #COLLECT_PATH_DISTANCE} short,
	 * pathfinding takes that offer on open ground too, parking the rancher up to four blocks from a
	 * drop it could have walked right up to. That is outside the {@link #COLLECT_REACH_SQUARED} a
	 * pickup reaches, so it stood there until the stall detector wrote the drop off, waited out the
	 * backoff and did the whole thing again, forever. Asking for the exact block first means the
	 * slack is only ever spent where it is actually needed.
	 */
	@Nullable
	private Path pathTo(RancherEntity rancher, Entity destination) {
		Path direct = rancher.getNavigation().findPathTo(destination, 0);

		// A null path is pathfinding declining to answer rather than saying no, and a second ask
		// gets the same non answer. Animals stand on ground the rancher can stand on, so for them
		// the first ask is the only one that makes sense anyway.
		if (job != Job.COLLECT || direct == null || direct.reachesTarget()) {
			return direct;
		}

		return rancher.getNavigation().findPathTo(destination, COLLECT_PATH_DISTANCE);
	}

	private double distanceToTarget(RancherEntity rancher) {
		if (target != null) {
			return Math.sqrt(rancher.squaredDistanceTo(target));
		}

		return targetPos == null ? 0.0 : Math.sqrt(rancher.squaredDistanceTo(Vec3d.ofCenter(targetPos)));
	}

	private static int countCarried(RancherEntity rancher) {
		SimpleInventory carried = rancher.getCarried();
		int used = 0;

		for (int slot = 0; slot < carried.size(); slot++) {
			if (!carried.getStack(slot).isEmpty()) {
				used++;
			}
		}

		return used;
	}

	private static boolean isPackFull(RancherEntity rancher) {
		return countCarried(rancher) == rancher.getCarried().size();
	}

	/** Drops the rancher cannot pocket land at its feet rather than disappearing. */
	private static void keepOrDrop(RancherEntity rancher, ItemStack stack) {
		ItemStack remainder = rancher.getCarried().addStack(stack);

		if (!remainder.isEmpty()) {
			rancher.getWorld().spawnEntity(new ItemEntity(rancher.getWorld(), rancher.getX(),
					rancher.getY() + 0.5, rancher.getZ(), remainder));
		}
	}

	private static void celebrate(RancherEntity rancher, AnimalEntity animal) {
		if (!(rancher.getWorld() instanceof ServerWorld world)) {
			return;
		}

		if (rancher.getSettings().playFeedSound) {
			world.playSound(null, animal.getBlockPos(), SoundEvents.ENTITY_GENERIC_EAT, SoundCategory.NEUTRAL, 0.5F,
					world.random.nextFloat() * 0.2F + 0.9F);
		}

		Vec3d center = animal.getPos().add(0.0, animal.getHeight() * 0.5, 0.0);
		world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, center.x, center.y, center.z, 4, 0.3, 0.3, 0.3, 0.0);
	}

	private static List<AnimalEntity> filterFeedable(List<AnimalEntity> animals, Inventory station, StationSettings config) {
		if (!config.requireFeedItems) {
			return animals;
		}

		return animals.stream().filter(animal -> findFeedSlot(station, animal) >= 0).toList();
	}

	private static int findFeedSlot(Inventory station, AnimalEntity animal) {
		for (int slot = 0; slot < station.size(); slot++) {
			ItemStack stack = station.getStack(slot);

			if (!stack.isEmpty() && animal.isBreedingItem(stack)) {
				return slot;
			}
		}

		return -1;
	}

	/** Moves what fits into {@code target}, mutating and returning the leftover. */
	private static ItemStack insert(Inventory target, ItemStack stack) {
		for (int slot = 0; slot < target.size() && !stack.isEmpty(); slot++) {
			ItemStack existing = target.getStack(slot);

			if (existing.isEmpty()) {
				target.setStack(slot, stack.copy());
				stack.setCount(0);
				break;
			}

			if (!ItemStack.canCombine(existing, stack)) {
				continue;
			}

			int room = Math.min(existing.getMaxCount(), target.getMaxCountPerStack()) - existing.getCount();
			int moved = Math.min(room, stack.getCount());

			if (moved > 0) {
				existing.increment(moved);
				stack.decrement(moved);
			}
		}

		return stack;
	}
}
