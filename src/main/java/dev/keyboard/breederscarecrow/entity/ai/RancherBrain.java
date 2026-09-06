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
		STROLLING,
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
	private static final double STATION_REACH_SQUARED = 6.25;
	private static final int REPATH_INTERVAL = 10;
	/** How long the rancher chases one target before writing it off, in ticks. */
	private static final int JOB_TIMEOUT = 400;
	/** How long a target that turned out to be unreachable is passed over, in ticks. */
	private static final int BLOCKED_COOLDOWN = 200;
	/** Paths tried per scan before the rancher gives up and waits for the next one. */
	private static final int MAX_PATH_CHECKS = 3;
	private static final int ATTACK_INTERVAL = 12;
	private static final int STROLL_CHANCE_TICKS = 160;
	private static final double WALK_SPEED = 0.6;
	private static final double STROLL_SPEED = 0.45;

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
			case STROLLING -> "strolling";
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

	private void idle(RancherEntity rancher, WorkArea area) {
		if (!rancher.getNavigation().isIdle()) {
			return;
		}

		BlockPos center = area.getCenter();

		// A rancher that drifted out of its area walks home before it does anything else.
		if (!area.contains(rancher)) {
			state = State.RETURNING;
			rancher.getNavigation().startMovingTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, STROLL_SPEED);
			return;
		}

		state = State.IDLE;

		if (rancher.getRandom().nextInt(STROLL_CHANCE_TICKS) != 0) {
			return;
		}

		int span = area.getRadius() * 2 + 1;
		state = State.STROLLING;
		rancher.getNavigation().startMovingTo(
				center.getX() + 0.5 + rancher.getRandom().nextInt(span) - area.getRadius(),
				center.getY(),
				center.getZ() + 0.5 + rancher.getRandom().nextInt(span) - area.getRadius(),
				STROLL_SPEED);
	}

	private boolean chooseJob(RancherEntity rancher, ServerWorld world, WorkArea area, ScarecrowBlockEntity station) {
		ModConfig config = ModConfig.get();
		note = "";

		// A full pack first, otherwise the rancher would keep killing animals it cannot carry.
		if (isPackFull(rancher)) {
			return takeDeposit(area);
		}

		ItemEntity drop = nearestReachable(rancher, world, world.getEntitiesByClass(ItemEntity.class, area.getBox(),
				item -> item.isAlive() && !item.cannotPickup() && rancher.getCarried().canInsert(item.getStack())));

		if (drop != null) {
			return take(Job.COLLECT, drop);
		}

		if (config.enableBreeding || config.enableCulling) {
			HerdSurvey survey = HerdSurvey.of(world, area);

			if (config.enableCulling && rancher.canCullNow()) {
				AnimalEntity victim = nearestReachable(rancher, world, survey.cullCandidates(config));

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
							filterFeedable(survey.babyCandidates(), station, config));

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

		AnimalEntity head = nearestReachable(rancher, world, heads);

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
	private <T extends Entity> T nearestReachable(RancherEntity rancher, ServerWorld world, List<T> candidates) {
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
			Path path = rancher.getNavigation().findPathTo(candidate, 0);

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

		return target != null && rancher.squaredDistanceTo(target) <= REACH_SQUARED;
	}

	private void navigate(RancherEntity rancher) {
		if (job == Job.DEPOSIT) {
			if (targetPos != null) {
				rancher.getNavigation().startMovingTo(targetPos.getX() + 0.5, targetPos.getY(),
						targetPos.getZ() + 0.5, WALK_SPEED);
			}
		} else if (target != null) {
			rancher.getNavigation().startMovingTo(target, WALK_SPEED);
		}
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
