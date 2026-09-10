package dev.keyboard.workstations.entity.ai;

import dev.keyboard.workstations.ModConfig;
import dev.keyboard.workstations.block.LumberBlockEntity;
import dev.keyboard.workstations.entity.LumberjackEntity;
import dev.keyboard.workstations.work.LumberSettings;
import dev.keyboard.workstations.work.Stock;
import dev.keyboard.workstations.work.WorkArea;
import dev.keyboard.workstations.work.Woods;
import dev.keyboard.workstations.work.WoodsSurvey;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.block.SaplingBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.BoneMealItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldEvents;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;

/**
 * The lumberjack's whole decision loop, as one explicit state machine driven from
 * {@code mobTick()} rather than from a {@code Goal}, for the same reasons as
 * {@link FarmerBrain}: a goal is only asked whether it wants to start on every other tick, and
 * it has to win movement control back from the idle wander goal before it is even asked.
 *
 * <p>Work is grouped into phases and each is carried through to the end before the next is
 * picked up, so the lumberjack visibly finishes felling a wood before it starts planting it,
 * instead of chopping one tree and planting one hole in whatever order they happen to be nearest.
 */
public class LumberjackBrain {
	/** What the lumberjack is doing with itself right now. */
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
		/** Every tree still standing in the area. */
		CHOP,
		/** Every stump, and with auto-planting on every open spot that could grow a tree. */
		PLANT,
		/** Bone meal on saplings already in the ground, when the station has been set to force them. */
		FERTILIZE,
		/** Every drop on the ground, then what was gathered emptied into the station. */
		COLLECT
	}

	public enum Job {
		CHOP,
		PLANT,
		FERTILIZE,
		COLLECT,
		DEPOSIT
	}

	/**
	 * The order phases are worked through. Chopping comes before planting so a stump left this
	 * round is planted in the same round rather than the next one, and fertilizing comes after
	 * planting so a sapling put down this pass can be forced on the next swing.
	 */
	private static final Phase[] ROTATION = {Phase.CHOP, Phase.PLANT, Phase.FERTILIZE, Phase.COLLECT};

	private static final double TREE_REACH_SQUARED = 6.25;
	private static final double COLLECT_REACH_SQUARED = 6.25;
	private static final int COLLECT_PATH_DISTANCE = 2;
	private static final double STATION_REACH_SQUARED = 6.25;
	private static final int STALL_LIMIT = 60;
	private static final double STALL_PROGRESS = 0.25;
	private static final int REPATH_INTERVAL = 10;
	private static final int JOB_TIMEOUT = 400;
	private static final int BLOCKED_COOLDOWN = 200;
	private static final int MAX_PATH_CHECKS = 3;
	private static final int SWING_INTERVAL = 6;
	/**
	 * Logs or leaves broken in one swing. A whole oak is a few swings; a giant is finished over
	 * several without holding the tick for every block at once.
	 */
	private static final int CHOP_BATCH = 8;
	private static final double POST_REACH_SQUARED = 4.0;
	private static final int SETTLE_TICKS = 60;
	private static final double WALK_SPEED = 0.6;
	private static final double RETURN_SPEED = 0.45;
	private static final int SWIM_PATIENCE = 40;

	private final Long2LongMap blockedPlots = new Long2LongOpenHashMap();
	private final Int2LongMap blockedDrops = new Int2LongOpenHashMap();
	private final LongSet served = new LongOpenHashSet();

	private State state = State.IDLE;
	@Nullable
	private Phase phase;
	private int rotationCursor;
	private boolean sweepOwed;
	private boolean phaseWorked;
	private boolean stationFull;
	private boolean pathPending;
	@Nullable
	private WoodsSurvey scanSurvey;
	@Nullable
	private Job job;
	@Nullable
	private Entity target;
	@Nullable
	private BlockPos targetPos;
	@Nullable
	private Woods.Tree felling;
	/** How far through {@link #felling} the current chop has got. */
	private int chopCursor;
	/**
	 * Square the lumberjack walks to for the tree it is felling, worked out when the job is taken
	 * so the reachability test and the walk itself cannot pick different sides of the trunk.
	 */
	@Nullable
	private BlockPos standPos;
	/** The sapling committed to when the planting job was taken, so the choice is made only once. */
	@Nullable
	private Item planting;
	private String note = "";
	private int scanCooldown;
	private int repathCooldown;
	private int actionCooldown;
	private int timeout;
	private int stalled;
	private int swimming;
	private int settling;
	private double closest = Double.MAX_VALUE;

	public void tick(LumberjackEntity lumberjack) {
		if (!(lumberjack.getWorld() instanceof ServerWorld world)) {
			return;
		}

		LumberBlockEntity station = lumberjack.getStation();
		WorkArea area = lumberjack.getWorkArea();

		if (station == null || area == null) {
			clearJob(lumberjack);
			phase = null;
			served.clear();
			state = State.NO_STATION;
			note = "";
			return;
		}

		long now = world.getTime();
		blockedPlots.long2LongEntrySet().removeIf(entry -> entry.getLongValue() <= now);
		blockedDrops.int2LongEntrySet().removeIf(entry -> entry.getLongValue() <= now);

		swimming = lumberjack.isTouchingWater() ? swimming + 1 : 0;

		if (swimming > SWIM_PATIENCE && lumberjack.getNavigation().isIdle()) {
			BlockPos post = area.getCenter();
			lumberjack.getMoveControl().moveTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, RETURN_SPEED);
		}

		if (job != null) {
			runJob(lumberjack, world, station);
			return;
		}

		if (scanCooldown > 0) {
			scanCooldown--;
		} else {
			scanCooldown = station.getSettings().workIntervalTicks;

			if (chooseJob(lumberjack, world, area, station)) {
				timeout = JOB_TIMEOUT;
				repathCooldown = 0;
				actionCooldown = 0;
				stalled = 0;
				settling = 0;
				closest = Double.MAX_VALUE;
				state = State.WALKING;
				navigate(lumberjack);
				return;
			}
		}

		idle(lumberjack, area, station.getSettings());
	}

	public String describe(LumberjackEntity lumberjack) {
		StringBuilder text = new StringBuilder(switch (state) {
			case NO_STATION -> "no station";
			case IDLE -> "idle";
			case WAITING -> "waiting";
			case RETURNING -> "heading back";
			case WALKING -> "walking";
			case WORKING -> "working";
		});

		if (state == State.WAITING) {
			text.append(' ').append(Math.max(0, settleTicks(lumberjack.getSettings()) - settling));
		}

		if (phase != null) {
			text.append(' ').append(phase.name().toLowerCase(Locale.ROOT));
		}

		if (job != null && (phase == null || !job.name().equals(phase.name()))) {
			text.append(phase == null ? ' ' : '/').append(job.name().toLowerCase(Locale.ROOT));
		}

		if (job == Job.PLANT && planting != null) {
			text.append(' ').append(Registries.ITEM.getId(planting).getPath());
		}

		if (state == State.WALKING) {
			text.append(String.format(Locale.ROOT, " %.1fm t%d", distanceToTarget(lumberjack), timeout));
			Path path = lumberjack.getNavigation().getCurrentPath();
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

		if (scanSurvey != null) {
			text.append(" | trees ").append(scanSurvey.trees().size());
		}

		if (sweepOwed) {
			text.append(" | sweep due");
		}

		if (!blockedPlots.isEmpty() || !blockedDrops.isEmpty()) {
			text.append(" | skip ").append(blockedPlots.size() + blockedDrops.size());
		}

		int carried = WorkerPack.count(lumberjack.getCarried());

		if (carried > 0) {
			text.append(" | pack ").append(carried).append('/').append(LumberjackEntity.CARRY_SLOTS);
		}

		return text.toString();
	}

	private void runJob(LumberjackEntity lumberjack, ServerWorld world, LumberBlockEntity station) {
		if (!jobValid(world)) {
			note = "target gone";
			clearJob(lumberjack);
			return;
		}

		if (target != null) {
			lumberjack.getLookControl().lookAt(target, 30.0F, 30.0F);
		} else if (targetPos != null) {
			lumberjack.getLookControl().lookAt(Vec3d.ofCenter(targetPos));
		}

		if (actionCooldown > 0) {
			actionCooldown--;
		}

		if (withinReach(lumberjack)) {
			state = State.WORKING;
			lumberjack.getNavigation().stop();
			timeout = JOB_TIMEOUT;
			stalled = 0;
			closest = Double.MAX_VALUE;

			if (actionCooldown <= 0 && actionReady(lumberjack)) {
				perform(lumberjack, world, station);
			}

			return;
		}

		if (--timeout <= 0) {
			blockCurrentTarget(world);
			note = "timed out";
			clearJob(lumberjack);
			return;
		}

		state = State.WALKING;

		if (--repathCooldown <= 0) {
			repathCooldown = REPATH_INTERVAL;
			navigate(lumberjack);
		}

		if (lumberjack.isWorkingGate()) {
			return;
		}

		double distance = distanceToTarget(lumberjack);

		if (distance < closest - STALL_PROGRESS) {
			closest = distance;
			stalled = 0;
			return;
		}

		if (++stalled > STALL_LIMIT) {
			blockCurrentTarget(world);
			note = "out of reach";
			clearJob(lumberjack);
		}
	}

	private boolean jobValid(ServerWorld world) {
		if (job == Job.COLLECT) {
			return target != null && target.isAlive() && !target.isRemoved();
		}

		if (job == Job.CHOP) {
			return felling != null && felling.standing(world);
		}

		if (targetPos == null) {
			return false;
		}

		return switch (job) {
			case DEPOSIT -> true;
			case PLANT -> Woods.canPlantAt(world, targetPos);
			case FERTILIZE -> Woods.isSapling(world.getBlockState(targetPos));
			default -> false;
		};
	}

	private void perform(LumberjackEntity lumberjack, ServerWorld world, LumberBlockEntity station) {
		Job current = job;

		if (current == null) {
			return;
		}

		boolean done = switch (current) {
			case CHOP -> chop(lumberjack, world, station);
			case PLANT -> plant(lumberjack, world, station);
			case FERTILIZE -> fertilize(lumberjack, world, station);
			case COLLECT -> collect(lumberjack);
			case DEPOSIT -> deposit(lumberjack, station);
		};

		if (done) {
			clearJob(lumberjack);
		}
	}

	private boolean actionReady(LumberjackEntity lumberjack) {
		if (job == null) {
			return false;
		}

		return switch (job) {
			case CHOP, PLANT, FERTILIZE -> lumberjack.canWorkNow();
			default -> true;
		};
	}

	private void clearJob(LumberjackEntity lumberjack) {
		job = null;
		target = null;
		targetPos = null;
		standPos = null;
		felling = null;
		chopCursor = 0;
		planting = null;
		actionCooldown = 0;

		if (state != State.NO_STATION) {
			state = State.IDLE;
			lumberjack.getNavigation().stop();
		}
	}

	private void idle(LumberjackEntity lumberjack, WorkArea area, LumberSettings config) {
		settling++;
		BlockPos post = area.getCenter();

		if (lumberjack.squaredDistanceTo(Vec3d.ofCenter(post)) <= POST_REACH_SQUARED) {
			state = State.IDLE;

			if (!lumberjack.getNavigation().isIdle()) {
				lumberjack.getNavigation().stop();
			}

			return;
		}

		if (settling < settleTicks(config)) {
			state = State.WAITING;

			if (!lumberjack.getNavigation().isIdle()) {
				lumberjack.getNavigation().stop();
			}

			return;
		}

		state = State.RETURNING;

		if (lumberjack.getNavigation().isIdle()
				&& !WorkerMovement.approach(lumberjack, Vec3d.ofCenter(post), RETURN_SPEED)) {
			lumberjack.getNavigation().startMovingTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, RETURN_SPEED);
		}
	}

	private static int settleTicks(LumberSettings config) {
		return Math.max(SETTLE_TICKS, config.workIntervalTicks * 2);
	}

	private boolean chooseJob(LumberjackEntity lumberjack, ServerWorld world, WorkArea area,
			LumberBlockEntity station) {
		LumberSettings config = station.getSettings();
		note = "";
		scanSurvey = null;

		// A pack that has built up interrupts whatever is running. Waiting until every slot is
		// occupied is what left a pile on the ground: the last swings have nowhere to put what
		// they produce. Walking back a little earlier costs a trip; carrying on until the pack
		// is jammed costs the wood.
		//
		// The early trip stands down while the station is known full, or the lumberjack would
		// wait at a station that cannot take anything instead of working the slots it still has.
		// A pack with no room left has nowhere to put a log either way, so that one still bites.
		if (WorkerPack.isFull(lumberjack.getCarried())
				|| (!stationFull && WorkerPack.shouldDeposit(lumberjack.getCarried()))) {
			return takeDeposit(area);
		}

		// Whatever the last swing just produced, before walking on. The phase is left alone, so
		// the next scan resumes the same felling rather than starting the rotation over; a drop
		// across the wood is the sweep's problem, not this one's.
		if (takeUnderfoot(lumberjack, world, area)) {
			return true;
		}

		if (phase != null) {
			if (takeJobIn(lumberjack, world, area, station, config)) {
				return true;
			}

			if (pathPending) {
				return false;
			}

			endPhase();
		}

		for (int attempt = 0; attempt <= ROTATION.length; attempt++) {
			startPhase(nextPhase(config));

			if (takeJobIn(lumberjack, world, area, station, config)) {
				return true;
			}

			if (pathPending) {
				return false;
			}

			endPhase();
		}

		if (!lumberjack.getCarried().isEmpty()) {
			return takeDeposit(area);
		}

		why("nothing to do");
		return false;
	}

	/**
	 * Records why a phase turned a round down, keeping the first answer rather than the last.
	 *
	 * <p>A round that ends idle is a round every phase declined, so the last one to speak would
	 * always be the one read over the worker's head. The first is the one worth having: the
	 * rotation is in the order the work matters, and a wood standing still is a question about
	 * felling, which a later "no saplings" would otherwise bury.
	 */
	private void why(String reason) {
		if (note.isEmpty()) {
			note = reason;
		}
	}

	private Phase nextPhase(LumberSettings config) {
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

		return Phase.COLLECT;
	}

	private static boolean enabled(Phase phase, LumberSettings config) {
		return switch (phase) {
			case CHOP -> config.enableChopping;
			case PLANT -> config.enableReplanting;
			case FERTILIZE -> config.forceGrowing;
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
		if (phaseWorked && (phase == Phase.CHOP || phase == Phase.PLANT || phase == Phase.FERTILIZE)) {
			sweepOwed = true;
		}

		phase = null;
		phaseWorked = false;
		served.clear();
	}

	private boolean takeJobIn(LumberjackEntity lumberjack, ServerWorld world, WorkArea area,
			LumberBlockEntity station, LumberSettings config) {
		pathPending = false;

		if (phase == null) {
			return false;
		}

		return switch (phase) {
			case CHOP -> takeChop(lumberjack, world, station);
			case PLANT -> takePlant(lumberjack, world, station, config);
			case FERTILIZE -> takeFertilize(lumberjack, world, station);
			case COLLECT -> takeCollect(lumberjack, world, area);
		};
	}

	private boolean takeChop(LumberjackEntity lumberjack, ServerWorld world, LumberBlockEntity station) {
		List<BlockPos> stumps = new ArrayList<>();
		List<Woods.Tree> trees = survey(world, station).trees();

		for (Woods.Tree tree : trees) {
			if (blockedPlots.get(tree.stump().asLong()) <= world.getTime()
					&& !served.contains(tree.stump().asLong())) {
				stumps.add(tree.stump());
			}
		}

		// Aimed at a square beside the trunk rather than at the trunk itself. Neither the stump
		// nor the block above it is somewhere a path can end: see the note on standingSpotBeside
		// for what the navigator does with a solid target.
		BlockPos stump = nearestReachable(lumberjack, world, stumps,
				spot -> approachTo(world, spot), 0);

		if (stump == null) {
			return false;
		}

		Woods.Tree tree = null;

		for (Woods.Tree candidate : trees) {
			if (candidate.stump().equals(stump)) {
				tree = candidate;
				break;
			}
		}

		if (tree == null) {
			return false;
		}

		job = Job.CHOP;
		target = null;
		targetPos = stump;
		standPos = approachTo(world, stump);
		felling = tree;
		chopCursor = 0;
		planting = null;
		return true;
	}

	/**
	 * Where to walk to work the trunk at {@code stump}. Falls back on the stump itself when the
	 * tree is walled in, which will not path, but leaves the usual unreachable handling to say so
	 * rather than inventing a second way of giving up.
	 */
	private static BlockPos approachTo(ServerWorld world, BlockPos stump) {
		BlockPos beside = Woods.standingSpotBeside(world, stump);
		return beside == null ? stump : beside;
	}

	private boolean takePlant(LumberjackEntity lumberjack, ServerWorld world, LumberBlockEntity station,
			LumberSettings config) {
		List<Item> palette = Woods.palette(station.seedStores());

		if (palette.isEmpty()) {
			why("no saplings");
			return false;
		}

		Item sapling = config.saplingMix.choose(palette, station.getPlantedTally());

		if (sapling == null) {
			why("mix all zero");
			return false;
		}

		SaplingBlock block = Woods.saplingFor(sapling);

		if (block == null) {
			return false;
		}

		List<BlockPos> spots = new ArrayList<>();

		for (BlockPos soil : survey(world, station).plantable()) {
			if (Woods.canPlant(world, soil, block)
					&& blockedPlots.get(soil.asLong()) <= world.getTime()
					&& !served.contains(soil.asLong())) {
				spots.add(soil);
			}
		}

		BlockPos soil = nearestReachable(lumberjack, world, spots, BlockPos::up, 0);

		if (soil == null) {
			return false;
		}

		job = Job.PLANT;
		target = null;
		targetPos = soil;
		felling = null;
		planting = sapling;
		return true;
	}

	private boolean takeFertilize(LumberjackEntity lumberjack, ServerWorld world, LumberBlockEntity station) {
		if (!Stock.holds(station.seedStores(), Items.BONE_MEAL)) {
			why("no bone meal");
			return false;
		}

		List<BlockPos> saplings = new ArrayList<>();

		for (BlockPos sapling : survey(world, station).saplings()) {
			if (blockedPlots.get(sapling.asLong()) <= world.getTime() && !served.contains(sapling.asLong())) {
				saplings.add(sapling);
			}
		}

		BlockPos spot = nearestReachable(lumberjack, world, saplings, pos -> pos, 1);

		if (spot == null) {
			return false;
		}

		job = Job.FERTILIZE;
		target = null;
		targetPos = spot;
		felling = null;
		planting = null;
		return true;
	}

	/**
	 * Pockets a drop sitting at the lumberjack's feet without touching {@link #phase}. Taking a
	 * job outside the phase is how a full pack already interrupts work; the same pattern lets
	 * a grab happen between swings and then hands the phase back the next scan.
	 *
	 * <p>Pathfinding declining to answer is a fact about the lumberjack, usually that it is mid
	 * stride, and must not pause the phase. An unreachable drop is written off as usual so the
	 * same one cannot stall every scan from here on.
	 */
	private boolean takeUnderfoot(LumberjackEntity lumberjack, ServerWorld world, WorkArea area) {
		List<ItemEntity> nearby = WorkerPack.underfoot(lumberjack, world, area.getBox(), lumberjack.getCarried());

		if (nearby.isEmpty()) {
			return false;
		}

		ItemEntity drop = nearestReachableDrop(lumberjack, world, nearby);

		if (drop == null) {
			pathPending = false;
			return false;
		}

		job = Job.COLLECT;
		target = drop;
		targetPos = null;
		felling = null;
		planting = null;
		return true;
	}

	private boolean takeCollect(LumberjackEntity lumberjack, ServerWorld world, WorkArea area) {
		ItemEntity drop = nearestReachableDrop(lumberjack, world, area);

		if (drop != null) {
			job = Job.COLLECT;
			target = drop;
			targetPos = null;
			felling = null;
			planting = null;
			return true;
		}

		if (!stationFull && !lumberjack.getCarried().isEmpty()) {
			return takeDeposit(area);
		}

		return false;
	}

	private boolean takeDeposit(WorkArea area) {
		job = Job.DEPOSIT;
		target = null;
		targetPos = area.getCenter();
		felling = null;
		planting = null;
		return true;
	}

	private WoodsSurvey survey(ServerWorld world, LumberBlockEntity station) {
		if (scanSurvey == null) {
			scanSurvey = station.surveyWoods(world);
		}

		return scanSurvey;
	}

	@Nullable
	private BlockPos nearestReachable(LumberjackEntity lumberjack, ServerWorld world, List<BlockPos> spots,
			UnaryOperator<BlockPos> stand, int slack) {
		if (spots.isEmpty()) {
			return null;
		}

		List<BlockPos> queue = new ArrayList<>(spots);
		queue.sort(Comparator.comparingDouble(spot -> lumberjack.squaredDistanceTo(Vec3d.ofCenter(spot))));

		for (int index = 0; index < Math.min(queue.size(), MAX_PATH_CHECKS); index++) {
			BlockPos spot = queue.get(index);

			if (WorkerMovement.isFarOff(lumberjack, Vec3d.ofCenter(spot))) {
				return spot;
			}

			Path path = lumberjack.getNavigation().findPathTo(stand.apply(spot), slack);

			if (path == null) {
				why("no path yet");
				pathPending = true;
				return null;
			}

			if (path.reachesTarget()) {
				return spot;
			}

			blockedPlots.put(spot.asLong(), world.getTime() + BLOCKED_COOLDOWN);
			why("unreachable");
		}

		return null;
	}

	@Nullable
	private ItemEntity nearestReachableDrop(LumberjackEntity lumberjack, ServerWorld world, WorkArea area) {
		return nearestReachableDrop(lumberjack, world, WorkerPack.looseIn(world, area.getBox(), lumberjack.getCarried()));
	}

	@Nullable
	private ItemEntity nearestReachableDrop(LumberjackEntity lumberjack, ServerWorld world, List<ItemEntity> candidates) {
		long now = world.getTime();
		List<ItemEntity> queue = new ArrayList<>();

		for (ItemEntity drop : candidates) {
			if (blockedDrops.get(drop.getId()) <= now) {
				queue.add(drop);
			}
		}

		if (queue.isEmpty()) {
			return null;
		}

		queue.sort(Comparator.comparingDouble(lumberjack::squaredDistanceTo));

		for (int index = 0; index < Math.min(queue.size(), MAX_PATH_CHECKS); index++) {
			ItemEntity drop = queue.get(index);

			if (WorkerMovement.isFarOff(lumberjack, drop.getPos())) {
				return drop;
			}

			Path path = lumberjack.getNavigation().findPathTo(drop, 0);

			if (path == null) {
				why("no path yet");
				pathPending = true;
				return null;
			}

			if (path.reachesTarget()) {
				return drop;
			}

			Path nearby = lumberjack.getNavigation().findPathTo(drop, COLLECT_PATH_DISTANCE);

			if (nearby != null && nearby.reachesTarget()) {
				return drop;
			}

			blockedDrops.put(drop.getId(), world.getTime() + BLOCKED_COOLDOWN);
			why("unreachable");
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
	 * Breaks the next batch of the tree being felled. Logs first, then the canopy when the
	 * station is set to take it: saplings come out of the leaves, and leaving them until the
	 * trunk is gone means a half-chopped tree does not drop its seed onto a log the lumberjack
	 * is about to break. With the canopy left standing the leaf list is empty and the job ends
	 * with the last log.
	 *
	 * <p>Returning false keeps the job running so the next swing continues the same tree rather
	 * than walking off and back again.
	 */
	private boolean chop(LumberjackEntity lumberjack, ServerWorld world, LumberBlockEntity station) {
		if (felling == null) {
			return true;
		}

		served.add(felling.stump().asLong());
		lumberjack.swingHand(Hand.MAIN_HAND);

		int broken = 0;
		List<BlockPos> logs = felling.logs();

		while (chopCursor < logs.size() && broken < CHOP_BATCH) {
			BlockPos log = logs.get(chopCursor++);

			if (world.isChunkLoaded(log.getX() >> 4, log.getZ() >> 4) && Woods.isLog(world.getBlockState(log))) {
				world.breakBlock(log, true, lumberjack);
				broken++;
			}
		}

		if (chopCursor < logs.size()) {
			lumberjack.startWorkCooldown();
			actionCooldown = SWING_INTERVAL;
			phaseWorked = true;
			return false;
		}

		int leafIndex = chopCursor - logs.size();
		List<BlockPos> leaves = felling.leaves();

		while (leafIndex < leaves.size() && broken < CHOP_BATCH) {
			BlockPos leaf = leaves.get(leafIndex++);

			if (world.isChunkLoaded(leaf.getX() >> 4, leaf.getZ() >> 4)
					&& Woods.isLeaves(world.getBlockState(leaf))) {
				world.breakBlock(leaf, true, lumberjack);
				broken++;
			}
		}

		chopCursor = logs.size() + leafIndex;

		if (leafIndex < leaves.size()) {
			lumberjack.startWorkCooldown();
			actionCooldown = SWING_INTERVAL;
			phaseWorked = true;
			return false;
		}

		station.noteStump(felling.stump());
		lumberjack.startWorkCooldown();
		actionCooldown = SWING_INTERVAL;
		phaseWorked = true;
		return true;
	}

	private boolean plant(LumberjackEntity lumberjack, ServerWorld world, LumberBlockEntity station) {
		BlockPos soil = targetPos;
		Item sapling = planting;

		if (soil == null || sapling == null) {
			return true;
		}

		served.add(soil.asLong());
		SaplingBlock block = Woods.saplingFor(sapling);

		if (block == null || !Woods.canPlant(world, soil, block)) {
			note = "cannot plant";
			station.clearStump(soil);
			return true;
		}

		int needed = Woods.needsSquare(sapling) ? 4 : 1;

		// Counted first so a dark oak that wants four does not spend two and then give up,
		// leaving saplings gone and nothing in the ground.
		if (ModConfig.get().consumeSeeds) {
			if (Stock.count(station.seedStores(), sapling) < needed) {
				needed = 1;
			}

			if (needed == 1 && !Stock.spend(station.seedStores(), sapling)) {
				note = "out of saplings";
				return true;
			}

			if (needed == 4) {
				for (int spent = 0; spent < 4; spent++) {
					Stock.spend(station.seedStores(), sapling);
				}
			}
		}

		lumberjack.swingHand(Hand.MAIN_HAND);

		if (needed == 4) {
			plantSquare(world, soil, block);
		} else {
			world.setBlockState(soil.up(), block.getDefaultState());
		}

		world.playSound(null, soil.up(), SoundEvents.ITEM_CROP_PLANT, SoundCategory.BLOCKS, 1.0F, 1.0F);
		station.notePlanted(sapling);
		station.clearStump(soil);
		lumberjack.startWorkCooldown();
		actionCooldown = SWING_INTERVAL;
		phaseWorked = true;
		return true;
	}

	/**
	 * Dark oak will not grow from a single sapling, so the four go down together or not at all.
	 * The corner is {@code soil} itself plus the three neighbours towards +X/+Z that are still
	 * clear; if any of those has been built on since the job was taken, the extras stay in the
	 * air and the one that did land will sit until the player finishes the square.
	 */
	private static void plantSquare(ServerWorld world, BlockPos soil, SaplingBlock sapling) {
		for (int dx = 0; dx < 2; dx++) {
			for (int dz = 0; dz < 2; dz++) {
				BlockPos plot = soil.add(dx, 0, dz);

				if (Woods.canPlant(world, plot, sapling)) {
					world.setBlockState(plot.up(), sapling.getDefaultState());
				}
			}
		}
	}

	/**
	 * One go of vanilla bone meal, spent from the station's own stock. {@link BoneMealItem}
	 * already rolls the chance and advances a stage, so a sapling is not forced into a tree on
	 * the first tap, and a station that has run out simply stops trying.
	 */
	private boolean fertilize(LumberjackEntity lumberjack, ServerWorld world, LumberBlockEntity station) {
		BlockPos sapling = targetPos;

		if (sapling == null) {
			return true;
		}

		served.add(sapling.asLong());

		if (!Stock.holds(station.seedStores(), Items.BONE_MEAL)) {
			note = "no bone meal";
			return true;
		}

		ItemStack meal = new ItemStack(Items.BONE_MEAL);

		if (!BoneMealItem.useOnFertilizable(meal, world, sapling)) {
			note = "will not grow";
			return true;
		}

		Stock.spend(station.seedStores(), Items.BONE_MEAL);
		world.syncWorldEvent(WorldEvents.BONE_MEAL_USED, sapling, 0);
		lumberjack.swingHand(Hand.MAIN_HAND);
		lumberjack.startWorkCooldown();
		actionCooldown = SWING_INTERVAL;
		phaseWorked = true;
		return true;
	}

	private boolean collect(LumberjackEntity lumberjack) {
		if (!(target instanceof ItemEntity item) || item.cannotPickup()) {
			return true;
		}

		ItemStack remainder = lumberjack.getCarried().addStack(item.getStack().copy());

		if (remainder.isEmpty()) {
			item.discard();
		} else {
			item.setStack(remainder);
		}

		lumberjack.getWorld().playSound(null, lumberjack.getBlockPos(), SoundEvents.ENTITY_ITEM_PICKUP,
				SoundCategory.NEUTRAL, 0.15F,
				(lumberjack.getRandom().nextFloat() - lumberjack.getRandom().nextFloat()) * 1.4F + 2.0F);
		phaseWorked = true;
		return true;
	}

	private boolean deposit(LumberjackEntity lumberjack, LumberBlockEntity station) {
		SimpleInventory carried = lumberjack.getCarried();
		boolean moved = false;

		for (int slot = 0; slot < carried.size(); slot++) {
			ItemStack stack = carried.getStack(slot);

			if (stack.isEmpty()) {
				continue;
			}

			int before = stack.getCount();
			ItemStack left = store(station, stack);
			carried.setStack(slot, left.isEmpty() ? ItemStack.EMPTY : left);

			if (left.getCount() != before) {
				moved = true;
			}
		}

		if (moved) {
			lumberjack.swingHand(Hand.MAIN_HAND);
		} else if (!carried.isEmpty()) {
			note = "station full";
			stationFull = true;
		}

		return true;
	}

	/**
	 * Saplings go into the seed boxes and wood onto the station's own shelves, which is the
	 * whole point of sharing the box with the farm: a wood's returns are mostly saplings, and
	 * left in with the logs they fill the station up with the one thing that was going straight
	 * back into the ground.
	 *
	 * <p>Saplings fall back on the station when the boxes are full. Logs do not fall the other
	 * way, or a station left unemptied would end up filling the seed boxes with oak and undo
	 * the separation.
	 */
	private static ItemStack store(LumberBlockEntity station, ItemStack stack) {
		if (Woods.isSapling(stack)) {
			stack = Stock.fill(station.seedBoxes(), stack);
		}

		return Stock.fill(List.of(station), stack);
	}

	private boolean withinReach(LumberjackEntity lumberjack) {
		if (job == Job.COLLECT) {
			return target != null && lumberjack.squaredDistanceTo(target) <= COLLECT_REACH_SQUARED;
		}

		if (targetPos == null) {
			return false;
		}

		double reach = job == Job.DEPOSIT ? STATION_REACH_SQUARED : TREE_REACH_SQUARED;
		return lumberjack.squaredDistanceTo(Vec3d.ofCenter(targetPos)) <= reach;
	}

	private void navigate(LumberjackEntity lumberjack) {
		if (job == Job.COLLECT) {
			if (target == null || WorkerMovement.approach(lumberjack, target.getPos(), WALK_SPEED)) {
				return;
			}

			Path direct = lumberjack.getNavigation().findPathTo(target, 0);
			Path path = direct != null && !direct.reachesTarget()
					? lumberjack.getNavigation().findPathTo(target, COLLECT_PATH_DISTANCE)
					: direct;

			if (path != null) {
				lumberjack.getNavigation().startMovingAlong(path, WALK_SPEED);
			}

			return;
		}

		if (targetPos == null) {
			return;
		}

		if (WorkerMovement.approach(lumberjack, Vec3d.ofCenter(targetPos), WALK_SPEED)) {
			return;
		}

		// Walked to the square picked out beside the trunk when the job was taken, not to the
		// trunk, which the navigator would answer by aiming above the canopy.
		if (job == Job.CHOP && standPos != null) {
			Path path = lumberjack.getNavigation().findPathTo(standPos, 0);

			if (path != null) {
				lumberjack.getNavigation().startMovingAlong(path, WALK_SPEED);
			}

			return;
		}

		if (job == Job.DEPOSIT || job == Job.FERTILIZE) {
			lumberjack.getNavigation().startMovingTo(targetPos.getX() + 0.5, targetPos.getY(),
					targetPos.getZ() + 0.5, WALK_SPEED);
			return;
		}

		BlockPos stand = targetPos.up();
		Path path = lumberjack.getNavigation().findPathTo(stand, 0);

		if (path != null) {
			lumberjack.getNavigation().startMovingAlong(path, WALK_SPEED);
		}
	}

	private double distanceToTarget(LumberjackEntity lumberjack) {
		if (target != null) {
			return Math.sqrt(lumberjack.squaredDistanceTo(target));
		}

		return targetPos == null ? 0.0 : Math.sqrt(lumberjack.squaredDistanceTo(Vec3d.ofCenter(targetPos)));
	}
}
