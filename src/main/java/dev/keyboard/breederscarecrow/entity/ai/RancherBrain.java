package dev.keyboard.breederscarecrow.entity.ai;

import dev.keyboard.breederscarecrow.ModConfig;
import dev.keyboard.breederscarecrow.block.ScarecrowBlockEntity;
import dev.keyboard.breederscarecrow.entity.RancherEntity;
import dev.keyboard.breederscarecrow.work.HerdSurvey;
import dev.keyboard.breederscarecrow.work.WorkArea;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.passive.AnimalEntity;
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
		SWIMMING,
		IDLE,
		RETURNING,
		WALKING,
		WORKING
	}

	/** The job it is doing it for. */
	public enum Job {
		/** Put an adult in love so vanilla's mate goal pairs it off. */
		FEED,
		/** Feed a baby to grow it up early. */
		GROW,
		CULL,
		COLLECT,
		DEPOSIT
	}

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
	 * <p>Only drops get this. An animal stands on ground the rancher could stand on too, so its own
	 * block is a perfectly good target, and loosening it there causes a deadlock: pathfinding calls
	 * the walk finished while the animal is still further away than {@link #REACH_SQUARED} allows
	 * feeding, leaving the rancher stood next to a cow it will not close the last stride on.
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
	/**
	 * How far off something may be before it is walked at in stages rather than pathed to directly,
	 * in blocks.
	 *
	 * <p>Pathfinding cannot see beyond the mob's follow range: the search stops expanding at nodes
	 * further from the rancher than that, so a request for anything past it comes back as a path
	 * that does not reach, exactly as if a wall were in the way. Follow range is 32 while the work
	 * radius goes up to 64, which quietly wrote off everything in the outer ring of a wide area
	 * however open the ground was. Standing at its post the rancher measures from the middle, so
	 * the ring started at 32 blocks out, or nearer than that towards the corners.
	 *
	 * <p>Raising follow range instead is a trap, because it is also the search bound: one genuinely
	 * unreachable drop in a wide area would then have the search exhaust every node within tens of
	 * blocks before admitting defeat, on a scan that repeats. Walked in hops, each search stays the
	 * size it has always been no matter how large the area is.
	 */
	private static final double PATH_RADIUS = 24.0;
	/** How far ahead each hop of a staged approach aims, in blocks. */
	private static final double APPROACH_STEP = 16.0;
	private static final int ATTACK_INTERVAL = 12;
	/**
	 * How near the station counts as being at its post, squared. Loose enough that the rancher is
	 * not forever correcting its footing after being jostled, tight enough to be beside the block.
	 */
	private static final double POST_REACH_SQUARED = 4.0;
	private static final double WALK_SPEED = 0.6;
	private static final double RETURN_SPEED = 0.45;

	/**
	 * Targets that turned out to be unreachable, by entity id, each held until the world time it
	 * maps to. Without this a drop that landed outside the fence is an infinite loop: it is the
	 * nearest thing on every scan, so the rancher walks at the fence, times out, and picks it
	 * straight back up.
	 */
	private final Int2LongMap blocked = new Int2LongOpenHashMap();

	private State state = State.IDLE;
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
	/** Closest the rancher has been to the current target, for spotting a walk going nowhere. */
	private double closest = Double.MAX_VALUE;

	public void tick(RancherEntity rancher) {
		if (!(rancher.getWorld() instanceof ServerWorld world)) {
			return;
		}

		ScarecrowBlockEntity station = rancher.getStation();
		WorkArea area = rancher.getWorkArea();

		if (station == null || area == null) {
			clearJob(rancher);
			state = State.NO_STATION;
			note = "";
			return;
		}

		long now = world.getTime();
		blocked.int2LongEntrySet().removeIf(entry -> entry.getLongValue() <= now);

		// The swim goal owns movement in water, and steering against it would drown the rancher.
		// The clock still runs so a job cannot be held forever by a puddle.
		if (rancher.isTouchingWater()) {
			state = State.SWIMMING;

			if (job != null && --timeout <= 0) {
				note = "timed out";
				clearJob(rancher);
			}

			return;
		}

		if (job != null) {
			runJob(rancher, world, area, station);
			return;
		}

		if (scanCooldown > 0) {
			scanCooldown--;
		} else {
			scanCooldown = ModConfig.get().workIntervalTicks;

			if (chooseJob(rancher, world, area, station)) {
				timeout = JOB_TIMEOUT;
				repathCooldown = 0;
				actionCooldown = 0;
				stalled = 0;
				closest = Double.MAX_VALUE;
				state = State.WALKING;
				navigate(rancher);
				return;
			}
		}

		idle(rancher, area);
	}

	/** A one line summary of the state machine, short enough to sit over the rancher's head. */
	public String describe(RancherEntity rancher) {
		StringBuilder text = new StringBuilder(switch (state) {
			case NO_STATION -> "no station";
			case SWIMMING -> "swimming";
			case IDLE -> "idle";
			case RETURNING -> "heading back";
			case WALKING -> "walking";
			case WORKING -> "working";
		});

		if (job != null) {
			text.append(' ').append(job.name().toLowerCase(Locale.ROOT));
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

		if (job == null && !note.isEmpty()) {
			text.append(" | ").append(note);
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

	private void runJob(RancherEntity rancher, ServerWorld world, WorkArea area, ScarecrowBlockEntity station) {
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

			if (actionCooldown <= 0) {
				perform(rancher, station);
			}

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

	private void perform(RancherEntity rancher, ScarecrowBlockEntity station) {
		Job current = job;

		if (current == null) {
			return;
		}

		boolean done = switch (current) {
			case FEED -> feed(rancher, station, false);
			case GROW -> feed(rancher, station, true);
			case CULL -> cull(rancher);
			case COLLECT -> collect(rancher);
			case DEPOSIT -> deposit(rancher, station);
		};

		if (done) {
			clearJob(rancher);
		}
	}

	private void clearJob(RancherEntity rancher) {
		if (job == Job.CULL) {
			rancher.startCullCooldown();
		}

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
	private void idle(RancherEntity rancher, WorkArea area) {
		BlockPos post = area.getCenter();

		if (rancher.squaredDistanceTo(Vec3d.ofCenter(post)) <= POST_REACH_SQUARED) {
			state = State.IDLE;

			// Stopped explicitly, or the walk home would carry on pushing it past the station.
			if (!rancher.getNavigation().isIdle()) {
				rancher.getNavigation().stop();
			}

			return;
		}

		state = State.RETURNING;

		// Only issued once: reissuing every tick restarts the path and the rancher never sets off.
		// A finished hop leaves navigation idle again, which is what advances a staged walk home.
		if (rancher.getNavigation().isIdle()
				&& !approach(rancher, Vec3d.ofCenter(post), RETURN_SPEED)) {
			rancher.getNavigation().startMovingTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, RETURN_SPEED);
		}
	}

	private boolean chooseJob(RancherEntity rancher, ServerWorld world, WorkArea area, ScarecrowBlockEntity station) {
		ModConfig config = ModConfig.get();
		note = "";

		// A full pack first, otherwise the rancher would keep killing animals it cannot carry.
		if (isPackFull(rancher)) {
			return takeDeposit(area);
		}

		ItemEntity drop = nearestReachable(rancher, world, world.getEntitiesByClass(ItemEntity.class, area.getBox(),
				item -> item.isAlive() && !item.cannotPickup() && rancher.getCarried().canInsert(item.getStack())),
				COLLECT_PATH_DISTANCE);

		if (drop != null) {
			return take(Job.COLLECT, drop);
		}

		if (config.enableBreeding || config.enableCulling) {
			HerdSurvey survey = HerdSurvey.of(world, area);

			if (config.enableCulling && rancher.canCullNow()) {
				AnimalEntity victim = nearestReachable(rancher, world, survey.cullCandidates(config),
						ANIMAL_PATH_DISTANCE);

				if (victim != null) {
					return take(Job.CULL, victim);
				}
			}

			if (config.enableBreeding && rancher.canFeedNow()) {
				HerdSurvey.FeedPlan plan = nearestPlan(rancher, world, survey.feedPlans(config), station, config);

				if (plan != null) {
					return takeFeed(plan);
				}

				if (config.feedBabies) {
					AnimalEntity baby = nearestReachable(rancher, world,
							filterFeedable(survey.babyCandidates(), station, config), ANIMAL_PATH_DISTANCE);

					if (baby != null) {
						return take(Job.GROW, baby);
					}
				}
			}
		}

		if (!rancher.getCarried().isEmpty()) {
			return takeDeposit(area);
		}

		if (note.isEmpty()) {
			note = "nothing to do";
		}

		return false;
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
			List<HerdSurvey.FeedPlan> plans, Inventory station, ModConfig config) {
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
	private static boolean affordable(HerdSurvey.FeedPlan plan, Inventory station, ModConfig config) {
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
			if (rancher.squaredDistanceTo(candidate) > PATH_RADIUS * PATH_RADIUS) {
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

	private boolean feed(RancherEntity rancher, ScarecrowBlockEntity station, boolean growUp) {
		if (!(target instanceof AnimalEntity animal)) {
			return true;
		}

		ModConfig config = ModConfig.get();

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
		rancher.tryAttack(animal);
		actionCooldown = ATTACK_INTERVAL;

		// Drops land on the ground and get picked up as a COLLECT job on a later pass, which also
		// covers eggs and anything else that shows up inside the area.
		return !animal.isAlive();
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
		return true;
	}

	private boolean deposit(RancherEntity rancher, ScarecrowBlockEntity station) {
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

			if (!approach(rancher, Vec3d.ofCenter(targetPos), WALK_SPEED)) {
				// This overload already settles for a block next to the target, which it has to:
				// the station itself is solid and can only ever be walked up to.
				rancher.getNavigation().startMovingTo(targetPos.getX() + 0.5, targetPos.getY(),
						targetPos.getZ() + 0.5, WALK_SPEED);
			}

			return;
		}

		if (target == null || approach(rancher, target.getPos(), WALK_SPEED)) {
			return;
		}

		// Pathed by hand rather than through startMovingTo(Entity, speed), which always asks for a
		// path right onto the target's own block. For a drop resting against a fence that block is
		// the fence, so the walk would silently never start, leaving the rancher stood still with a
		// job it had already accepted as reachable.
		Path path = rancher.getNavigation().findPathTo(target, pathDistance());

		if (path != null) {
			rancher.getNavigation().startMovingAlong(path, WALK_SPEED);
		}
	}

	/**
	 * Walks one hop towards a destination too far away to path to, aiming at a point on the straight
	 * line to it. Called again on every repath, the hops carry the rancher along until the real
	 * destination comes into range and normal pathing takes over.
	 *
	 * @return whether the destination was far enough to need this, and a hop was therefore started
	 */
	private boolean approach(RancherEntity rancher, Vec3d destination, double speed) {
		if (rancher.squaredDistanceTo(destination) <= PATH_RADIUS * PATH_RADIUS) {
			return false;
		}

		Vec3d hop = rancher.getPos()
				.add(destination.subtract(rancher.getPos()).normalize().multiply(APPROACH_STEP));
		rancher.getNavigation().startMovingTo(hop.x, hop.y, hop.z, speed);
		return true;
	}

	private int pathDistance() {
		return job == Job.COLLECT ? COLLECT_PATH_DISTANCE : ANIMAL_PATH_DISTANCE;
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

		if (ModConfig.get().playFeedSound) {
			world.playSound(null, animal.getBlockPos(), SoundEvents.ENTITY_GENERIC_EAT, SoundCategory.NEUTRAL, 0.5F,
					world.random.nextFloat() * 0.2F + 0.9F);
		}

		Vec3d center = animal.getPos().add(0.0, animal.getHeight() * 0.5, 0.0);
		world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, center.x, center.y, center.z, 4, 0.3, 0.3, 0.3, 0.0);
	}

	private static List<AnimalEntity> filterFeedable(List<AnimalEntity> animals, Inventory station, ModConfig config) {
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
