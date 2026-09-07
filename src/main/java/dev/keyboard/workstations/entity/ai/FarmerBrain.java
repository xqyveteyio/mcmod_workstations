package dev.keyboard.workstations.entity.ai;

import dev.keyboard.workstations.block.FarmBlockEntity;
import dev.keyboard.workstations.entity.FarmerEntity;
import dev.keyboard.workstations.work.Crops;
import dev.keyboard.workstations.work.FarmSettings;
import dev.keyboard.workstations.work.PlotSurvey;
import dev.keyboard.workstations.work.WorkArea;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
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
 * The farmer's whole decision loop, as one explicit state machine driven from {@code mobTick()}
 * rather than from a {@code Goal}, for the same reasons as {@link RancherBrain}: a goal is only
 * asked whether it wants to start on every other tick, and it has to win movement control back
 * from the idle wander goal before it is even asked.
 *
 * <p>Work is grouped into phases and each is carried through to the end before the next is picked
 * up, so the farmer visibly finishes hoeing a field before it starts sowing it, instead of doing
 * one of each in whatever order they happen to be nearest.
 *
 * <p>Unlike the rancher, almost every target here is a block rather than an entity, and doing the
 * work changes that block: a hoed plot is no longer tillable, a sown one no longer bare. A round is
 * therefore finite on its own, and the survey shrinking is what ends a phase.
 */
public class FarmerBrain {
	/** What the farmer is doing with itself right now. */
	public enum State {
		NO_STATION,
		IDLE,
		/** Stood where the last job finished, seeing whether another one turns up. */
		WAITING,
		RETURNING,
		WALKING,
		WORKING
	}

	/** The kind of work being done, one at a time and each carried through to the end. */
	public enum Phase {
		/** Every plot that has been trampled back to bare ground, hoed again. */
		TILL,
		/** Every bare plot, sown with whatever the mix calls for next. */
		SOW,
		/** Every crop that has finished growing. */
		HARVEST,
		/** Every drop on the ground, then what was gathered emptied into the station. */
		COLLECT
	}

	/** A single errand inside a phase. */
	public enum Job {
		TILL,
		SOW,
		HARVEST,
		COLLECT,
		DEPOSIT
	}

	/**
	 * The order phases are worked through. Tilling comes before sowing so a plot put right this
	 * round is sown in the same round rather than the next one, and harvesting comes before the
	 * sweep that clears up after it.
	 */
	private static final Phase[] ROTATION = {Phase.TILL, Phase.SOW, Phase.HARVEST, Phase.COLLECT};

	/**
	 * How near a plot counts as being at it, squared. Two and a half blocks: enough to work the
	 * plot next door without stepping onto it, and well inside a player's own reach.
	 */
	private static final double PLOT_REACH_SQUARED = 6.25;
	/**
	 * Reach for picking things up, matching the rancher's: enough for a drop in a neighbouring cell
	 * wherever in that cell it has settled, and no further.
	 */
	private static final double COLLECT_REACH_SQUARED = 6.25;
	/** How near a path has to get a drop to count as reaching it, in blocks. */
	private static final int COLLECT_PATH_DISTANCE = 2;
	private static final double STATION_REACH_SQUARED = 6.25;
	/** Ticks without getting meaningfully closer before a target is given up on. */
	private static final int STALL_LIMIT = 60;
	/** Blocks of closing distance that count as progress rather than jitter. */
	private static final double STALL_PROGRESS = 0.25;
	private static final int REPATH_INTERVAL = 10;
	/** How long the farmer chases one target before writing it off, in ticks. */
	private static final int JOB_TIMEOUT = 400;
	/** How long a target that turned out to be unreachable is passed over, in ticks. */
	private static final int BLOCKED_COOLDOWN = 200;
	/** Paths tried per scan before the farmer gives up and waits for the next one. */
	private static final int MAX_PATH_CHECKS = 3;
	/** Ticks between swings, so hoeing a field is watchable rather than instant. */
	private static final int SWING_INTERVAL = 6;
	/** How near the station counts as being at its post, squared. */
	private static final double POST_REACH_SQUARED = 4.0;
	/** How long the farmer stands where it finished before setting off back to its post, in ticks. */
	private static final int SETTLE_TICKS = 60;
	private static final double WALK_SPEED = 0.6;
	private static final double RETURN_SPEED = 0.45;
	/** Ticks in water to allow pathfinding before the farmer is steered home by hand. */
	private static final int SWIM_PATIENCE = 40;

	/** Plots that turned out to be unreachable, by packed position, each held until a world time. */
	private final Long2LongMap blockedPlots = new Long2LongOpenHashMap();
	/** The same for drops, which are entities and so are keyed by id. */
	private final Int2LongMap blockedDrops = new Int2LongOpenHashMap();
	/** Plots already tried this phase, so a job that quietly fails cannot loop forever. */
	private final LongSet served = new LongOpenHashSet();

	private State state = State.IDLE;
	@Nullable
	private Phase phase;
	/** Where in {@link #ROTATION} the next phase comes from. */
	private int rotationCursor;
	/** A sweep harvesting has earned, taken before the rotation gets its turn back. */
	private boolean sweepOwed;
	/** Whether the current phase has actually accomplished anything, which is what earns a sweep. */
	private boolean phaseWorked;
	/** Set when the station had no room, so a sweep stops retrying a deposit that cannot land. */
	private boolean stationFull;
	/** Set when pathfinding declined to answer rather than saying no. */
	private boolean pathPending;
	/** One look at the field per scan, shared by whichever phases get asked during it. */
	@Nullable
	private PlotSurvey scanSurvey;
	@Nullable
	private Job job;
	@Nullable
	private Entity target;
	@Nullable
	private BlockPos targetPos;
	/** The seed committed to when the sowing job was taken, so the choice is made only once. */
	@Nullable
	private Item sowing;
	/** Why the last scan came up empty, for the label over the farmer's head. */
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
	/** Closest the farmer has been to the current target, for spotting a walk going nowhere. */
	private double closest = Double.MAX_VALUE;

	public void tick(FarmerEntity farmer) {
		if (!(farmer.getWorld() instanceof ServerWorld world)) {
			return;
		}

		FarmBlockEntity station = farmer.getStation();
		WorkArea area = farmer.getWorkArea();

		if (station == null || area == null) {
			clearJob(farmer);
			// Whatever round it was part way through belongs to a station that is no longer there.
			phase = null;
			served.clear();
			state = State.NO_STATION;
			note = "";
			return;
		}

		long now = world.getTime();
		blockedPlots.long2LongEntrySet().removeIf(entry -> entry.getLongValue() <= now);
		blockedDrops.int2LongEntrySet().removeIf(entry -> entry.getLongValue() <= now);

		// The state machine has to keep running in water. SwimGoal takes the JUMP control alone and
		// never steers, so handing movement over to it leaves nothing at all moving the farmer.
		swimming = farmer.isTouchingWater() ? swimming + 1 : 0;

		// Out of its depth there may be no node for pathfinding to offer. Steering by hand needs no
		// path and only has to reach a bank the navigation can work from again.
		if (swimming > SWIM_PATIENCE && farmer.getNavigation().isIdle()) {
			BlockPos post = area.getCenter();
			farmer.getMoveControl().moveTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, RETURN_SPEED);
		}

		if (job != null) {
			runJob(farmer, world, station);
			return;
		}

		if (scanCooldown > 0) {
			scanCooldown--;
		} else {
			scanCooldown = station.getSettings().workIntervalTicks;

			if (chooseJob(farmer, world, area, station)) {
				timeout = JOB_TIMEOUT;
				repathCooldown = 0;
				actionCooldown = 0;
				stalled = 0;
				settling = 0;
				closest = Double.MAX_VALUE;
				state = State.WALKING;
				navigate(farmer);
				return;
			}
		}

		idle(farmer, area, station.getSettings());
	}

	/** A one line summary of the state machine, short enough to sit over the farmer's head. */
	public String describe(FarmerEntity farmer) {
		StringBuilder text = new StringBuilder(switch (state) {
			case NO_STATION -> "no station";
			case IDLE -> "idle";
			case WAITING -> "waiting";
			case RETURNING -> "heading back";
			case WALKING -> "walking";
			case WORKING -> "working";
		});

		// The countdown to giving up and walking home, so a farmer stood in a field can be told
		// apart from one that is stuck.
		if (state == State.WAITING) {
			text.append(' ').append(Math.max(0, settleTicks(farmer.getSettings()) - settling));
		}

		if (phase != null) {
			text.append(' ').append(phase.name().toLowerCase(Locale.ROOT));
		}

		// Only when it says something the phase has not already. Most jobs share their phase's
		// name, and "walking sow sow" reads like a bug.
		if (job != null && (phase == null || !job.name().equals(phase.name()))) {
			text.append(phase == null ? ' ' : '/').append(job.name().toLowerCase(Locale.ROOT));
		}

		if (job == Job.SOW && sowing != null) {
			text.append(' ').append(Registries.ITEM.getId(sowing).getPath());
		}

		if (state == State.WALKING) {
			text.append(String.format(Locale.ROOT, " %.1fm t%d", distanceToTarget(farmer), timeout));

			// Which node of the path it is on: a target accepted as reachable but a walk that never
			// starts looks identical to plain sluggishness without this.
			Path path = farmer.getNavigation().getCurrentPath();
			text.append(path == null
					? " nopath"
					: String.format(Locale.ROOT, " n%d/%d", path.getCurrentNodeIndex(), path.getLength()));
		}

		if (swimming > 0) {
			text.append(" | water");
		}

		if (job == null && !note.isEmpty()) {
			text.append(" | ").append(note);
		}

		FarmBlockEntity station = farmer.getStation();

		if (station != null) {
			text.append(" | plots ").append(station.getPlotCount());
		}

		if (sweepOwed) {
			text.append(" | sweep due");
		}

		if (!blockedPlots.isEmpty() || !blockedDrops.isEmpty()) {
			text.append(" | skip ").append(blockedPlots.size() + blockedDrops.size());
		}

		int carried = countCarried(farmer);

		if (carried > 0) {
			text.append(" | pack ").append(carried).append('/').append(FarmerEntity.CARRY_SLOTS);
		}

		return text.toString();
	}

	private void runJob(FarmerEntity farmer, ServerWorld world, FarmBlockEntity station) {
		if (!jobValid(world)) {
			note = "target gone";
			clearJob(farmer);
			return;
		}

		if (target != null) {
			farmer.getLookControl().lookAt(target, 30.0F, 30.0F);
		} else if (targetPos != null) {
			farmer.getLookControl().lookAt(Vec3d.ofCenter(targetPos));
		}

		if (actionCooldown > 0) {
			actionCooldown--;
		}

		if (withinReach(farmer)) {
			state = State.WORKING;
			farmer.getNavigation().stop();

			// Arriving settles the question the timeout and the stall detector were both asking, so
			// both start again from scratch.
			timeout = JOB_TIMEOUT;
			stalled = 0;
			closest = Double.MAX_VALUE;

			// The clock is deliberately not running here. It exists to give up on a walk that is
			// going nowhere, and having arrived, waiting out the configured interval is the farmer
			// doing as it was told.
			if (actionCooldown <= 0 && actionReady(farmer)) {
				perform(farmer, world, station);
			}

			return;
		}

		if (--timeout <= 0) {
			// Never got there. The target is passed over for a while so the farmer does not spend
			// every scan from here on walking at the same thing it cannot reach.
			blockCurrentTarget(world);
			note = "timed out";
			clearJob(farmer);
			return;
		}

		state = State.WALKING;

		if (--repathCooldown <= 0) {
			repathCooldown = REPATH_INTERVAL;
			navigate(farmer);
		}

		// Time spent waiting on a gate is not the target's fault. The gate gives up on a blocked
		// doorway and pauses before trying again, which on its own outlasts STALL_LIMIT.
		if (farmer.isWorkingGate()) {
			return;
		}

		// Getting no closer means as close as the world allows, which is the point to give up
		// rather than stand there for the rest of the timeout.
		double distance = distanceToTarget(farmer);

		if (distance < closest - STALL_PROGRESS) {
			closest = distance;
			stalled = 0;
			return;
		}

		if (++stalled > STALL_LIMIT) {
			blockCurrentTarget(world);
			note = "out of reach";
			clearJob(farmer);
		}
	}

	/**
	 * Whether the job still makes sense. A plot job is checked against the world rather than merely
	 * against the plot still existing, because somebody may have hoed, sown or harvested it in the
	 * time the farmer spent walking over.
	 */
	private boolean jobValid(ServerWorld world) {
		if (job == Job.COLLECT) {
			return target != null && target.isAlive() && !target.isRemoved();
		}

		if (targetPos == null) {
			return false;
		}

		return switch (job) {
			case DEPOSIT -> true;
			case TILL -> Crops.isTillable(world.getBlockState(targetPos)) && Crops.isClearAbove(world, targetPos);
			case SOW -> Crops.isFarmland(world.getBlockState(targetPos)) && Crops.isClearAbove(world, targetPos);
			case HARVEST -> Crops.isRipe(world, targetPos);
			default -> false;
		};
	}

	private void perform(FarmerEntity farmer, ServerWorld world, FarmBlockEntity station) {
		Job current = job;

		if (current == null) {
			return;
		}

		boolean done = switch (current) {
			case TILL -> till(farmer, world);
			case SOW -> sow(farmer, world, station);
			case HARVEST -> harvest(farmer, world);
			case COLLECT -> collect(farmer);
			case DEPOSIT -> deposit(farmer, station);
		};

		if (done) {
			clearJob(farmer);
		}
	}

	/**
	 * Whether the configured pacing allows the job to go ahead. Asked on arrival rather than when
	 * the job was picked, so the walk over happens during the wait instead of after it.
	 */
	private boolean actionReady(FarmerEntity farmer) {
		if (job == null) {
			return false;
		}

		return switch (job) {
			case TILL, SOW, HARVEST -> farmer.canWorkNow();
			default -> true;
		};
	}

	private void clearJob(FarmerEntity farmer) {
		job = null;
		target = null;
		targetPos = null;
		sowing = null;
		actionCooldown = 0;

		if (state != State.NO_STATION) {
			state = State.IDLE;
			farmer.getNavigation().stop();
		}
	}

	/**
	 * With no work to do the farmer waits at its station rather than wandering the field.
	 *
	 * <p>Standing still is not just cosmetic. A worker drifting around looks busy while doing
	 * nothing, and it will not be in the same place twice when you go looking for it.
	 */
	private void idle(FarmerEntity farmer, WorkArea area, FarmSettings config) {
		settling++;
		BlockPos post = area.getCenter();

		if (farmer.squaredDistanceTo(Vec3d.ofCenter(post)) <= POST_REACH_SQUARED) {
			state = State.IDLE;

			// Stopped explicitly, or the walk home would carry on pushing it past the station.
			if (!farmer.getNavigation().isIdle()) {
				farmer.getNavigation().stop();
			}

			return;
		}

		// Not on the way anywhere yet: the job only just ended and the field has not been looked
		// over since. Setting off now is what produces a half walk home and an about turn.
		if (settling < settleTicks(config)) {
			state = State.WAITING;

			if (!farmer.getNavigation().isIdle()) {
				farmer.getNavigation().stop();
			}

			return;
		}

		state = State.RETURNING;

		// Only issued once: reissuing every tick restarts the path and the farmer never sets off.
		// A finished hop leaves navigation idle again, which is what advances a staged walk home.
		if (farmer.getNavigation().isIdle()
				&& !WorkerMovement.approach(farmer, Vec3d.ofCenter(post), RETURN_SPEED)) {
			farmer.getNavigation().startMovingTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, RETURN_SPEED);
		}
	}

	/**
	 * How long to stand still before heading home, in ticks. Scaled off the work interval as well
	 * as fixed, because "nothing to do" is only ever established by a scan coming up empty.
	 */
	private static int settleTicks(FarmSettings config) {
		return Math.max(SETTLE_TICKS, config.workIntervalTicks * 2);
	}

	private boolean chooseJob(FarmerEntity farmer, ServerWorld world, WorkArea area, FarmBlockEntity station) {
		FarmSettings config = station.getSettings();
		note = "";
		scanSurvey = null;

		// A full pack interrupts whatever is running. Carrying on would mean harvesting crops there
		// is nowhere left to put.
		if (isPackFull(farmer)) {
			return takeDeposit(area);
		}

		if (phase != null) {
			if (takeJobIn(farmer, world, area, station, config)) {
				return true;
			}

			if (pathPending) {
				return false;
			}

			endPhase();
		}

		// One turn round the rotation, with a spare go for the sweep that harvesting may have just
		// earned. Every phase gets asked, so a quiet farm still reaches the one thing that does
		// have work waiting.
		for (int attempt = 0; attempt <= ROTATION.length; attempt++) {
			startPhase(nextPhase(config));

			if (takeJobIn(farmer, world, area, station, config)) {
				return true;
			}

			if (pathPending) {
				return false;
			}

			endPhase();
		}

		if (!farmer.getCarried().isEmpty()) {
			return takeDeposit(area);
		}

		if (note.isEmpty()) {
			note = station.getPlotCount() == 0 ? "no plots" : "nothing to do";
		}

		return false;
	}

	/** The next phase to try: a sweep if one is owed, otherwise the next enabled one in turn. */
	private Phase nextPhase(FarmSettings config) {
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

	private static boolean enabled(Phase phase, FarmSettings config) {
		return switch (phase) {
			case TILL -> config.enableTilling;
			case SOW -> config.enableSowing;
			case HARVEST -> config.enableHarvesting;
			case COLLECT -> true;
		};
	}

	private void startPhase(Phase next) {
		phase = next;
		phaseWorked = false;
		stationFull = false;
		served.clear();
	}

	private void endPhase() {
		// A harvested crop leaves its drops where it fell, so harvesting hands over to a sweep
		// rather than to whatever the rotation had lined up next. Only when something actually
		// happened: a phase that found nothing to do owes nothing.
		if (phaseWorked && phase == Phase.HARVEST) {
			sweepOwed = true;
		}

		phase = null;
		phaseWorked = false;
		served.clear();
	}

	/** The next errand within the current phase, or false when the phase has nothing left. */
	private boolean takeJobIn(FarmerEntity farmer, ServerWorld world, WorkArea area,
			FarmBlockEntity station, FarmSettings config) {
		pathPending = false;

		if (phase == null) {
			return false;
		}

		return switch (phase) {
			case TILL -> takePlotJob(farmer, world, Job.TILL, survey(world, station).tillable());
			case SOW -> takeSow(farmer, world, station, config);
			case HARVEST -> takePlotJob(farmer, world, Job.HARVEST, survey(world, station).ripe());
			case COLLECT -> takeCollect(farmer, world, area);
		};
	}

	private boolean takePlotJob(FarmerEntity farmer, ServerWorld world, Job newJob, List<BlockPos> plots) {
		BlockPos plot = nearestReachablePlot(farmer, world, plots);

		if (plot == null) {
			return false;
		}

		job = newJob;
		target = null;
		targetPos = plot;
		sowing = null;
		return true;
	}

	/**
	 * Sowing needs a seed as well as a plot, and the seed is decided here rather than on arrival so
	 * the walk over cannot be wasted on a plot the mix turns out to have nothing for.
	 */
	private boolean takeSow(FarmerEntity farmer, ServerWorld world, FarmBlockEntity station, FarmSettings config) {
		List<Item> palette = Crops.palette(station);

		if (palette.isEmpty()) {
			note = "no seeds";
			return false;
		}

		Item seed = config.seedMix.choose(palette, station.getPlantedTally());

		if (seed == null) {
			note = "mix all zero";
			return false;
		}

		BlockPos plot = nearestReachablePlot(farmer, world, survey(world, station).bare());

		if (plot == null) {
			return false;
		}

		job = Job.SOW;
		target = null;
		targetPos = plot;
		sowing = seed;
		return true;
	}

	private boolean takeCollect(FarmerEntity farmer, ServerWorld world, WorkArea area) {
		ItemEntity drop = nearestReachableDrop(farmer, world, area);

		if (drop != null) {
			job = Job.COLLECT;
			target = drop;
			targetPos = null;
			sowing = null;
			return true;
		}

		// The sweep is not finished until what was picked up is in the station, so whatever phase
		// comes next starts with an empty pack and room for what it produces.
		if (!stationFull && !farmer.getCarried().isEmpty()) {
			return takeDeposit(area);
		}

		return false;
	}

	private boolean takeDeposit(WorkArea area) {
		job = Job.DEPOSIT;
		target = null;
		targetPos = area.getCenter();
		sowing = null;
		return true;
	}

	private PlotSurvey survey(ServerWorld world, FarmBlockEntity station) {
		if (scanSurvey == null) {
			scanSurvey = station.surveyPlots(world);
		}

		return scanSurvey;
	}

	/**
	 * Closest plot the farmer can actually walk up to, nearest tried first.
	 *
	 * <p>Paths aim at the block above the plot rather than at the plot itself. That block is where
	 * the farmer would be standing, and it is a node pathfinding can name; farmland is solid, so
	 * asking for a path into it is the same trap that used to write off drops resting in fences.
	 */
	@Nullable
	private BlockPos nearestReachablePlot(FarmerEntity farmer, ServerWorld world, List<BlockPos> plots) {
		long now = world.getTime();
		List<BlockPos> queue = new ArrayList<>(plots.size());

		for (BlockPos plot : plots) {
			if (blockedPlots.get(plot.asLong()) <= now && !served.contains(plot.asLong())) {
				queue.add(plot);
			}
		}

		if (queue.isEmpty()) {
			return null;
		}

		queue.sort(Comparator.comparingDouble(plot -> farmer.squaredDistanceTo(Vec3d.ofCenter(plot))));

		// Each miss costs a pathfind, so a scan only probes the few nearest and leaves the rest for
		// later. Anything ruled out goes on the blocked list, so the next scan starts further down.
		for (int index = 0; index < Math.min(queue.size(), MAX_PATH_CHECKS); index++) {
			BlockPos plot = queue.get(index);

			// Beyond pathfinding's reach the answer comes back "no" whatever the ground is like, so
			// there is nothing worth asking. Such a plot is accepted and walked at in stages; if it
			// does turn out to be unreachable, the stall detector writes it off once the farmer is
			// near enough for a refusal to actually mean something.
			if (WorkerMovement.isFarOff(farmer, Vec3d.ofCenter(plot))) {
				return plot;
			}

			Path path = farmer.getNavigation().findPathTo(plot.up(), 0);

			if (path == null) {
				// Pathfinding declines to answer at all while the farmer is off the ground, which
				// happens constantly to a walking mob. That is a fact about the farmer and not
				// about the plot, so nothing may be written off here.
				note = "no path yet";
				pathPending = true;
				return null;
			}

			if (path.reachesTarget()) {
				return plot;
			}

			blockedPlots.put(plot.asLong(), world.getTime() + BLOCKED_COOLDOWN);
			note = "unreachable";
		}

		return null;
	}

	@Nullable
	private ItemEntity nearestReachableDrop(FarmerEntity farmer, ServerWorld world, WorkArea area) {
		long now = world.getTime();
		List<ItemEntity> queue = new ArrayList<>();

		for (ItemEntity drop : world.getEntitiesByClass(ItemEntity.class, area.getBox(),
				item -> item.isAlive() && !item.cannotPickup() && farmer.getCarried().canInsert(item.getStack()))) {
			if (blockedDrops.get(drop.getId()) <= now) {
				queue.add(drop);
			}
		}

		if (queue.isEmpty()) {
			return null;
		}

		queue.sort(Comparator.comparingDouble(farmer::squaredDistanceTo));

		for (int index = 0; index < Math.min(queue.size(), MAX_PATH_CHECKS); index++) {
			ItemEntity drop = queue.get(index);

			if (WorkerMovement.isFarOff(farmer, drop.getPos())) {
				return drop;
			}

			// The exact block first and a block nearby only if that fails, because the slack a drop
			// resting inside a fence needs would otherwise be taken on open ground too, parking the
			// farmer several blocks from something it could have walked right up to.
			Path path = farmer.getNavigation().findPathTo(drop, 0);

			if (path == null) {
				note = "no path yet";
				pathPending = true;
				return null;
			}

			if (path.reachesTarget()) {
				return drop;
			}

			Path nearby = farmer.getNavigation().findPathTo(drop, COLLECT_PATH_DISTANCE);

			if (nearby != null && nearby.reachesTarget()) {
				return drop;
			}

			blockedDrops.put(drop.getId(), world.getTime() + BLOCKED_COOLDOWN);
			note = "unreachable";
		}

		return null;
	}

	private void blockCurrentTarget(ServerWorld world) {
		if (target != null) {
			blockedDrops.put(target.getId(), world.getTime() + BLOCKED_COOLDOWN);
		} else if (targetPos != null && job != Job.DEPOSIT) {
			blockedPlots.put(targetPos.asLong(), world.getTime() + BLOCKED_COOLDOWN);
		}
	}

	/**
	 * Hoes a trampled plot back into farmland. No hoe is spent or even carried: the farmer is
	 * equipment the station summons, and asking players to keep it stocked with tools would make a
	 * farm stop working for a reason nothing on the screen explains.
	 */
	private boolean till(FarmerEntity farmer, ServerWorld world) {
		BlockPos plot = targetPos;

		if (plot == null) {
			return true;
		}

		served.add(plot.asLong());
		farmer.swingHand(Hand.MAIN_HAND);
		world.setBlockState(plot, Blocks.FARMLAND.getDefaultState());
		world.playSound(null, plot, SoundEvents.ITEM_HOE_TILL, SoundCategory.BLOCKS, 1.0F, 1.0F);
		farmer.startWorkCooldown();
		actionCooldown = SWING_INTERVAL;
		phaseWorked = true;
		return true;
	}

	private boolean sow(FarmerEntity farmer, ServerWorld world, FarmBlockEntity station) {
		BlockPos plot = targetPos;
		Item seed = sowing;

		if (plot == null || seed == null) {
			return true;
		}

		served.add(plot.asLong());
		CropBlock crop = Crops.cropFor(seed);

		if (crop == null) {
			return true;
		}

		BlockPos above = plot.up();
		BlockState planted = crop.getDefaultState();

		// Vanilla's own check, so a crop that would not survive here is never put in the ground.
		if (!planted.canPlaceAt(world, above)) {
			note = "cannot plant";
			return true;
		}

		if (station.getSettings().consumeSeeds && !spendSeed(station, seed)) {
			note = "out of seeds";
			return true;
		}

		farmer.swingHand(Hand.MAIN_HAND);
		world.setBlockState(above, planted);
		world.playSound(null, above, SoundEvents.ITEM_CROP_PLANT, SoundCategory.BLOCKS, 1.0F, 1.0F);
		// Recorded whether or not the seed was paid for, because the tally is what turns the mix's
		// weights into real ratios and that has nothing to do with who owns the seed.
		station.notePlanted(seed);
		farmer.startWorkCooldown();
		actionCooldown = SWING_INTERVAL;
		phaseWorked = true;
		return true;
	}

	/** Takes one seed out of the station, or reports that there were none left after all. */
	private static boolean spendSeed(Inventory station, Item seed) {
		for (int slot = 0; slot < station.size(); slot++) {
			ItemStack stack = station.getStack(slot);

			if (!stack.isEmpty() && stack.getItem() == seed) {
				station.removeStack(slot, 1);
				station.markDirty();
				return true;
			}
		}

		return false;
	}

	/**
	 * Breaks a grown crop, leaving what it drops where it fell. Harvesting always hands over to a
	 * sweep, so the produce is collected as part of finishing the same piece of work.
	 */
	private boolean harvest(FarmerEntity farmer, ServerWorld world) {
		BlockPos plot = targetPos;

		if (plot == null) {
			return true;
		}

		served.add(plot.asLong());
		farmer.swingHand(Hand.MAIN_HAND);
		// Broken the vanilla way, so the crop's loot table decides what comes out of it and the
		// break particles and sound behave as they do for a player.
		world.breakBlock(plot.up(), true, farmer);
		farmer.startWorkCooldown();
		actionCooldown = SWING_INTERVAL;
		phaseWorked = true;
		return true;
	}

	private boolean collect(FarmerEntity farmer) {
		if (!(target instanceof ItemEntity item) || item.cannotPickup()) {
			return true;
		}

		ItemStack remainder = farmer.getCarried().addStack(item.getStack().copy());

		if (remainder.isEmpty()) {
			item.discard();
		} else {
			item.setStack(remainder);
		}

		farmer.getWorld().playSound(null, farmer.getBlockPos(), SoundEvents.ENTITY_ITEM_PICKUP,
				SoundCategory.NEUTRAL, 0.15F,
				(farmer.getRandom().nextFloat() - farmer.getRandom().nextFloat()) * 1.4F + 2.0F);
		phaseWorked = true;
		return true;
	}

	private boolean deposit(FarmerEntity farmer, FarmBlockEntity station) {
		SimpleInventory carried = farmer.getCarried();
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
			farmer.swingHand(Hand.MAIN_HAND);
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

	private boolean withinReach(FarmerEntity farmer) {
		if (job == Job.COLLECT) {
			return target != null && farmer.squaredDistanceTo(target) <= COLLECT_REACH_SQUARED;
		}

		if (targetPos == null) {
			return false;
		}

		double reach = job == Job.DEPOSIT ? STATION_REACH_SQUARED : PLOT_REACH_SQUARED;
		return farmer.squaredDistanceTo(Vec3d.ofCenter(targetPos)) <= reach;
	}

	private void navigate(FarmerEntity farmer) {
		if (job == Job.COLLECT) {
			if (target == null || WorkerMovement.approach(farmer, target.getPos(), WALK_SPEED)) {
				return;
			}

			// Pathed by hand rather than through startMovingTo(Entity, speed), which always asks
			// for a path right onto the target's own block. For a drop resting against a fence that
			// block is the fence, so the walk would silently never start.
			Path direct = farmer.getNavigation().findPathTo(target, 0);
			Path path = direct != null && !direct.reachesTarget()
					? farmer.getNavigation().findPathTo(target, COLLECT_PATH_DISTANCE)
					: direct;

			if (path != null) {
				farmer.getNavigation().startMovingAlong(path, WALK_SPEED);
			}

			return;
		}

		if (targetPos == null) {
			return;
		}

		if (WorkerMovement.approach(farmer, Vec3d.ofCenter(targetPos), WALK_SPEED)) {
			return;
		}

		if (job == Job.DEPOSIT) {
			// This overload already settles for a block next to the target, which it has to: the
			// station itself is solid and can only ever be walked up to.
			farmer.getNavigation().startMovingTo(targetPos.getX() + 0.5, targetPos.getY(),
					targetPos.getZ() + 0.5, WALK_SPEED);
			return;
		}

		// The block above the plot, which is where the farmer stands to work it.
		BlockPos stand = targetPos.up();
		Path path = farmer.getNavigation().findPathTo(stand, 0);

		if (path != null) {
			farmer.getNavigation().startMovingAlong(path, WALK_SPEED);
		}
	}

	private double distanceToTarget(FarmerEntity farmer) {
		if (target != null) {
			return Math.sqrt(farmer.squaredDistanceTo(target));
		}

		return targetPos == null ? 0.0 : Math.sqrt(farmer.squaredDistanceTo(Vec3d.ofCenter(targetPos)));
	}

	private static int countCarried(FarmerEntity farmer) {
		SimpleInventory carried = farmer.getCarried();
		int used = 0;

		for (int slot = 0; slot < carried.size(); slot++) {
			if (!carried.getStack(slot).isEmpty()) {
				used++;
			}
		}

		return used;
	}

	private static boolean isPackFull(FarmerEntity farmer) {
		return countCarried(farmer) == farmer.getCarried().size();
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
