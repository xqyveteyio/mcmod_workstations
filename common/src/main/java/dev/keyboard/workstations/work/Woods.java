package dev.keyboard.workstations.work;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.SaplingBlock;
import net.minecraft.block.SaplingGenerator;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * What the lumberjack knows about trees and saplings, kept apart from the AI so "is this a tree,
 * and will a sapling actually grow here" can be reasoned about on its own.
 *
 * <p>Logs and leaves are recognised by the vanilla tags, not by a hardcoded list of woods. A tag
 * is what a modded tree joins to be treated as wood by the rest of the game, so it is also what
 * makes one of those trees this station's business. A house of planks is already out of those
 * tags. A house whose posts happen to be logs is not: those read as the same block a trunk is.
 * What tells them apart is the canopy, and the joinery a post sits in. A pile of logs with no
 * leaves is a building or a leftover; a log with planks, stairs or a door against it is a post,
 * even when a real tree has grown in against the wall.
 *
 * <p>A tree is gathered by flood-filling from a log. The fill walks every neighbouring log of
 * the same wood, including diagonally and down, because a branch is still part of the same tree
 * when it steps sideways or dips; stopping at the six cardinal faces would leave an acacia half
 * standing, and walking into a birch because it touched an oak would fell two trees as one. The
 * walk is capped so a giant jungle or a mod's world tree cannot hold the tick hostage.
 *
 * <p>The canopy always comes down with the trunk: nether hats never decay, and overworld leaves
 * left to vanilla would drip saplings for a minute after the wood was already empty. The box
 * around those logs is the same extents Tree Harvester uses: three blocks out in the overworld,
 * five in the nether, one below the stump and five above the highest log. A leaf still sitting
 * within two of another tree's log is left for that tree. Matching leaf blocks keeps a birch
 * from taking the oak beside it.
 */
public final class Woods {
	/**
	 * Most logs one flood fill will take. Large enough for a grown oak or a modest jungle, small
	 * enough that a 2x2 mega spruce or a mod's giant is cut short and finished on a later pass
	 * rather than walked in one tick.
	 */
	public static final int MAX_LOGS = 256;
	/**
	 * Bound on how many canopy blocks one tree may take. The box around a large fungus or a
	 * 2x2 jungle is several times the trunk; this keeps a mod's world tree from holding the tick.
	 */
	public static final int MAX_LEAVES = 1024;
	/**
	 * How far the canopy box extends from the trunk in the overworld, matching Tree Harvester.
	 * Branches already widen the box; this is the extra around the outermost log.
	 */
	public static final int LEAF_PAD = 3;
	/**
	 * The same extra in the nether. A huge fungus hat is a hollow shell up to four across, so
	 * the overworld pad would leave most of it standing after the stem came down.
	 */
	public static final int FUNGUS_PAD = 5;
	/** Blocks above the highest log that still count as this tree's canopy. */
	public static final int CANOPY_ABOVE = 5;
	/** Blocks below the lowest log, so hanging mangrove leaves and weeping vines are not missed. */
	public static final int CANOPY_BELOW = 1;
	/**
	 * A leaf this close to a log that is not this tree's is left standing. Tree Harvester uses
	 * the same gap so two trunks side by side do not steal each other's hats.
	 */
	public static final int FOREIGN_LOG_KEEP = 2;
	/** Air a sapling wants above it before it is worth planting, in blocks. */
	public static final int GROW_HEIGHT = 7;
	/** Light a sapling needs to grow, matching vanilla's own random-tick gate. */
	public static final int MIN_LIGHT = 9;
	/**
	 * Chebyshev gap kept between auto-planted saplings. Two is enough that neighbouring oaks do
	 * not grow into each other's trunk, and sparse enough that a wood is not carpeted with
	 * saplings that can never mature.
	 */
	public static final int PLANT_SPACING = 3;
	/** Side of the block of saplings a {@link #needsSquare} wood has to be planted in. */
	public static final int SQUARE_SIDE = 2;

	/**
	 * Whether each sapling wants a square, remembered so the grower is not asked again every
	 * hole. Identity, because an item is one object for the life of the game.
	 */
	private static final Map<Item, Boolean> SQUARE = new IdentityHashMap<>();

	private Woods() {
	}

	public static boolean isLog(BlockState state) {
		return state.isIn(BlockTags.LOGS) || isMangroveRoot(state);
	}

	public static boolean isLeaves(BlockState state) {
		return state.isIn(BlockTags.LEAVES) || isFungusCanopy(state);
	}

	/**
	 * The nether equivalent of a leaf, matching Tree Harvester: wart blocks of the hat and the
	 * shroomlights grown into it. Weeping vines hang off a crimson hat in the same box, so they
	 * come down with it; twisting vines grow from the ground and would walk a warped forest into
	 * one canopy.
	 */
	private static boolean isFungusCanopy(BlockState state) {
		return state.isIn(BlockTags.WART_BLOCKS)
				|| state.isOf(Blocks.SHROOMLIGHT)
				|| state.isOf(Blocks.WEEPING_VINES)
				|| state.isOf(Blocks.WEEPING_VINES_PLANT);
	}

	private static boolean isMangroveRoot(BlockState state) {
		return state.isOf(Blocks.MANGROVE_ROOTS) || state.isOf(Blocks.MUDDY_MANGROVE_ROOTS);
	}

	private static boolean isNetherStem(BlockState state) {
		return state.isIn(BlockTags.CRIMSON_STEMS) || state.isIn(BlockTags.WARPED_STEMS);
	}

	/**
	 * Whether two trunk blocks are the same wood. Oak must not walk into birch because the
	 * trunks touched. Mangrove roots are the same wood as mangrove logs; crimson stem and
	 * hyphae are one fungus.
	 */
	private static boolean sameWood(BlockState a, BlockState b) {
		return woodKey(a).equals(woodKey(b));
	}

	private static String woodKey(BlockState state) {
		String path = Registries.BLOCK.getId(state.getBlock()).getPath();

		if (path.contains("mangrove")) {
			return "mangrove";
		}

		for (String suffix : new String[] {"_log", "_wood", "_stem", "_hyphae", "_roots"}) {
			if (path.endsWith(suffix)) {
				return path.substring(0, path.length() - suffix.length());
			}
		}

		return path;
	}

	private static boolean isPersistent(BlockState state) {
		return state.contains(LeavesBlock.PERSISTENT) && state.get(LeavesBlock.PERSISTENT);
	}

	/**
	 * Whether this log still has its bark. Stripped wood is something a player or a village
	 * already worked, not a trunk that grew there.
	 */
	public static boolean isWorked(BlockState state) {
		return Registries.BLOCK.getId(state.getBlock()).getPath().contains("stripped");
	}

	/**
	 * Whether {@code pos} is a log built into something: planks, stairs, a door, glass. The four
	 * sides only, so a trunk on a cobble path or a stone floor is still a trunk.
	 */
	public static boolean isFramed(WorldView world, BlockPos pos) {
		for (Direction face : Direction.Type.HORIZONTAL) {
			if (isJoinery(world.getBlockState(pos.offset(face)))) {
				return true;
			}
		}

		return false;
	}

	/**
	 * The blocks a house puts against its posts, which a tree never grows next to on its own.
	 * Fences are left out: a trunk in a pen touches those all the time and is still a tree.
	 */
	private static boolean isJoinery(BlockState state) {
		return state.isIn(BlockTags.PLANKS)
				|| state.isIn(BlockTags.WOODEN_STAIRS)
				|| state.isIn(BlockTags.WOODEN_SLABS)
				|| state.isIn(BlockTags.WOODEN_DOORS)
				|| state.isIn(BlockTags.WOODEN_TRAPDOORS)
				|| state.isIn(BlockTags.FENCE_GATES)
				|| state.isIn(BlockTags.WOOL)
				|| state.isIn(BlockTags.BEDS)
				|| state.isIn(BlockTags.IMPERMEABLE);
	}

	/** A log that is still part of a tree, not a post or a beam. */
	public static boolean isTrunkLog(WorldView world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		return isLog(state) && !isWorked(state) && !isFramed(world, pos);
	}

	/** Whether this is part of a tree at all, which is what a felling swing is allowed to break. */
	public static boolean isTreeBlock(BlockState state) {
		return isLog(state) || isLeaves(state);
	}

	public static boolean isSapling(BlockState state) {
		return state.isIn(BlockTags.SAPLINGS);
	}

	/** Whether a stack is a sapling the lumberjack could put in the ground. */
	public static boolean isSapling(ItemStack stack) {
		return !stack.isEmpty() && saplingFor(stack.getItem()) != null;
	}

	/** The sapling an item plants, or null when the item is not a sapling at all. */
	@Nullable
	public static SaplingBlock saplingFor(Item item) {
		return item instanceof BlockItem blockItem && blockItem.getBlock() instanceof SaplingBlock sapling
				? sapling
				: null;
	}

	/**
	 * Whether this sapling will only grow from a block of four.
	 *
	 * <p>Read off the sapling's own grower rather than named as dark oak. Vanilla's mark for a
	 * wood that cannot stand alone is a grower whose small-tree feature is empty, which is how
	 * dark oak is written and how a mod copies it. Jungle and spruce also grow from a square, but
	 * they still grow from one, so they plant as one.
	 *
	 * <p>A sapling that is not a {@link SaplingBlock}, or that grows by some other means, has
	 * nothing here to read: there is no tag and no public method that says "I need four".
	 * Those plant as one.
	 */
	public static boolean needsSquare(Item sapling) {
		return SQUARE.computeIfAbsent(sapling, Woods::detectSquare);
	}

	private static boolean detectSquare(Item item) {
		SaplingBlock sapling = saplingFor(item);

		if (sapling == null) {
			return false;
		}

		SaplingGenerator generator = sapling.generator;
		Random roll = Random.create(0L);
		return generator.getSmallTreeFeature(roll, false) == null
				&& generator.getSmallTreeFeature(roll, true) == null;
	}

	/**
	 * Where to root a square of {@code sapling} that covers {@code soil}, or null when no such
	 * square is there to be had.
	 *
	 * <p>All four positions {@code soil} could take in the square are tried, not just the one
	 * running towards +X/+Z. A stump on the edge of a clearing is as likely to be the far corner
	 * of the only square that fits as the near one, and only ever asking about one of them turns
	 * a plantable spot into a refusal.
	 *
	 * <p>A square counts when every one of its four squares either takes a sapling now or already
	 * holds this same one, and at least one of them still needs filling. Accepting the ones
	 * already planted is what lets a half finished square be completed rather than written off
	 * for as long as it stands there.
	 *
	 * <p>The whole square has to be inside {@code area}. Letting it hang over the edge would put
	 * a tree where this station never looks for one, so the square that does not fit the plot is
	 * no square at all.
	 */
	@Nullable
	public static BlockPos squareFrom(WorldView world, BlockPos soil, SaplingBlock sapling, WorkArea area) {
		for (int dx = 1 - SQUARE_SIDE; dx <= 0; dx++) {
			for (int dz = 1 - SQUARE_SIDE; dz <= 0; dz++) {
				BlockPos corner = soil.add(dx, 0, dz);

				if (squareWorks(world, corner, sapling, area)) {
					return corner;
				}
			}
		}

		return null;
	}

	/** The squares of the block rooted at {@code corner} that are still waiting for a sapling. */
	public static List<BlockPos> squareGaps(WorldView world, BlockPos corner, SaplingBlock sapling) {
		List<BlockPos> gaps = new ArrayList<>();

		for (BlockPos plot : square(corner)) {
			if (canPlant(world, plot, sapling)) {
				gaps.add(plot);
			}
		}

		return gaps;
	}

	private static boolean squareWorks(WorldView world, BlockPos corner, SaplingBlock sapling, WorkArea area) {
		boolean gap = false;

		for (BlockPos plot : square(corner)) {
			if (!area.contains(plot)) {
				return false;
			}

			if (canPlant(world, plot, sapling)) {
				gap = true;
			} else if (!world.getBlockState(plot.up()).isOf(sapling)) {
				return false;
			}
		}

		return gap;
	}

	private static List<BlockPos> square(BlockPos corner) {
		List<BlockPos> plots = new ArrayList<>(SQUARE_SIDE * SQUARE_SIDE);

		for (int dx = 0; dx < SQUARE_SIDE; dx++) {
			for (int dz = 0; dz < SQUARE_SIDE; dz++) {
				plots.add(corner.add(dx, 0, dz));
			}
		}

		return plots;
	}

	public static boolean isBoneMeal(ItemStack stack) {
		return stack.isOf(Items.BONE_MEAL);
	}

	/**
	 * The saplings a station is offering, without repeats and in the order its stores were given.
	 * This is the whole of what the lumberjack is allowed to plant, whether or not it is set to
	 * spend them.
	 */
	public static List<Item> palette(List<Inventory> stores) {
		Set<Item> saplings = new LinkedHashSet<>();

		for (Inventory store : stores) {
			for (int slot = 0; slot < store.size(); slot++) {
				ItemStack stack = store.getStack(slot);

				if (isSapling(stack)) {
					saplings.add(stack.getItem());
				}
			}
		}

		return new ArrayList<>(saplings);
	}

	/**
	 * The whole of the tree hanging off {@code start}, which must itself be a log.
	 *
	 * <p>Logs outside the work area are still taken once the fill has started: leaving a trunk
	 * standing because a branch crossed the line would be a tree half felled, and the cap is what
	 * stops that from walking the rest of the world.
	 */
	public static Tree gather(ServerWorld world, BlockPos start) {
		List<BlockPos> logs = new ArrayList<>();
		Set<BlockPos> seen = new LinkedHashSet<>();
		Queue<BlockPos> queue = new ArrayDeque<>();
		queue.add(start.toImmutable());
		seen.add(start.toImmutable());

		BlockState wood = world.getBlockState(start);
		BlockPos.Mutable cursor = new BlockPos.Mutable();

		while (!queue.isEmpty() && logs.size() < MAX_LOGS) {
			BlockPos current = queue.poll();

			if (!world.isChunkLoaded(current.getX() >> 4, current.getZ() >> 4)) {
				continue;
			}

			if (!isTrunkLog(world, current) || !sameWood(wood, world.getBlockState(current))) {
				continue;
			}

			logs.add(current);

			if (logs.size() >= MAX_LOGS) {
				break;
			}

			for (int dy = -1; dy <= 1; dy++) {
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (dx == 0 && dy == 0 && dz == 0) {
							continue;
						}

						cursor.set(current.getX() + dx, current.getY() + dy, current.getZ() + dz);
						BlockPos next = cursor.toImmutable();

						if (seen.add(next)) {
							queue.add(next);
						}
					}
				}
			}
		}

		if (logs.isEmpty()) {
			// The starting block is still spoken for, or a framed post would be asked about on
			// every look and never marked seen.
			return new Tree(start.toImmutable(), List.of(start.toImmutable()), List.of(), false);
		}

		List<BlockPos> foundLeaves = canopy(world, logs);

		// No canopy is a building, a leftover pile, or a trunk whose leaves have already gone.
		// None of those are a tree: chopping them is how a village loses its posts.
		if (foundLeaves.isEmpty()) {
			return new Tree(stumpOf(world, logs), logs, List.of(), false);
		}

		return new Tree(stumpOf(world, logs), logs, foundLeaves, true);
	}

	/**
	 * The lowest log, preferring one that is sitting on soil a sapling could use. That is the
	 * square to walk to and the square to put a sapling back on; a log higher up is a branch.
	 */
	private static BlockPos stumpOf(ServerWorld world, List<BlockPos> logs) {
		BlockPos stump = logs.get(0);

		for (BlockPos log : logs) {
			boolean lower = log.getY() < stump.getY()
					|| (log.getY() == stump.getY() && log.asLong() < stump.asLong());
			boolean betterSoil = canPlantAt(world, log.down()) && !canPlantAt(world, stump.down());

			if (betterSoil || (lower && canPlantAt(world, log.down()) == canPlantAt(world, stump.down()))) {
				stump = log;
			}
		}

		return stump;
	}

	/**
	 * Canopy hanging off these logs. Tree Harvester does not walk leaf distance: it takes the
	 * box around the trunk (three out, or five for a nether hat, one below and five above) and
	 * leaves standing anything still within two of another tree's log. Overworld leaves have to
	 * match the hat above this trunk so a birch does not take the oak beside it.
	 */
	private static List<BlockPos> canopy(ServerWorld world, List<BlockPos> logs) {
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		boolean nether = false;

		for (BlockPos log : logs) {
			minX = Math.min(minX, log.getX());
			minY = Math.min(minY, log.getY());
			minZ = Math.min(minZ, log.getZ());
			maxX = Math.max(maxX, log.getX());
			maxY = Math.max(maxY, log.getY());
			maxZ = Math.max(maxZ, log.getZ());
			nether = nether || isNetherStem(world.getBlockState(log));
		}

		int pad = nether ? FUNGUS_PAD : LEAF_PAD;
		Block kind = nether ? null : canopyKind(world, logs);
		Set<BlockPos> ours = new HashSet<>(logs);
		List<BlockPos> leaves = new ArrayList<>();

		for (BlockPos pos : BlockPos.iterate(
				minX - pad, minY - CANOPY_BELOW, minZ - pad,
				maxX + pad, maxY + CANOPY_ABOVE, maxZ + pad)) {
			if (leaves.size() >= MAX_LEAVES) {
				break;
			}

			if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
				continue;
			}

			BlockState state = world.getBlockState(pos);

			if (!isLeaves(state) || isPersistent(state)) {
				continue;
			}

			if (nether) {
				if (!isFungusCanopy(state)) {
					continue;
				}
			} else if (kind != null && state.getBlock() != kind) {
				continue;
			}

			if (foreignLogNearby(world, pos, ours)) {
				continue;
			}

			leaves.add(pos.toImmutable());
		}

		return leaves;
	}

	/**
	 * The leaf block sitting on this trunk, which is what Tree Harvester uses to tell two
	 * neighbouring woods apart. Nothing above the highest log is a nether hat, or a tree whose
	 * leaves have already gone.
	 */
	@Nullable
	private static Block canopyKind(ServerWorld world, List<BlockPos> logs) {
		BlockPos highest = logs.get(0);

		for (BlockPos log : logs) {
			if (log.getY() > highest.getY()
					|| (log.getY() == highest.getY() && log.asLong() < highest.asLong())) {
				highest = log;
			}
		}

		BlockState above = world.getBlockState(highest.up());

		if (isLeaves(above) && !isPersistent(above)) {
			return above.getBlock();
		}

		for (Direction face : Direction.values()) {
			if (face == Direction.DOWN) {
				continue;
			}

			BlockState around = world.getBlockState(highest.offset(face));

			if (isLeaves(around) && !isPersistent(around)) {
				return around.getBlock();
			}
		}

		return null;
	}

	/** Whether another tree's log still sits within {@link #FOREIGN_LOG_KEEP} of this leaf. */
	private static boolean foreignLogNearby(WorldView world, BlockPos pos, Set<BlockPos> ours) {
		BlockPos.Mutable cursor = new BlockPos.Mutable();

		for (int dy = -FOREIGN_LOG_KEEP; dy <= FOREIGN_LOG_KEEP; dy++) {
			for (int dx = -FOREIGN_LOG_KEEP; dx <= FOREIGN_LOG_KEEP; dx++) {
				for (int dz = -FOREIGN_LOG_KEEP; dz <= FOREIGN_LOG_KEEP; dz++) {
					cursor.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);

					if (ours.contains(cursor) || !world.isChunkLoaded(cursor.getX() >> 4, cursor.getZ() >> 4)) {
						continue;
					}

					if (isLog(world.getBlockState(cursor))) {
						return true;
					}
				}
			}
		}

		return false;
	}

	/**
	 * A square beside {@code trunk} with room to stand in, which is where a worker has to be put
	 * to fell it.
	 *
	 * <p>Worth working out by hand rather than leaving to the navigator, because asking to path
	 * onto a log does not mean what it looks like it means. {@code MobNavigation} answers a solid
	 * target by climbing the column above it until it finds something that is not solid and
	 * pathing there instead, which is how a mob ends up stood on top of a melon. A trunk is a
	 * column of solid blocks several high, so the same rule quietly turns "walk to this stump"
	 * into "walk to the air above the canopy", and every tree comes back unreachable however open
	 * the ground around it is.
	 *
	 * <p>Answered as the block the feet would occupy, so it can be handed to the navigator as-is:
	 * an air square is resolved down onto whatever is under it, which is the standing square again.
	 *
	 * @return the square to stand in, or null when the trunk is walled in on all four sides
	 */
	@Nullable
	public static BlockPos standingSpotBeside(WorldView world, BlockPos trunk) {
		// Level with the stump first, then a step up and a step down, so a tree on a slope or one
		// whose lowest log is buried is still approached rather than written off.
		for (int dy : new int[] {0, 1, -1}) {
			for (Direction face : Direction.Type.HORIZONTAL) {
				BlockPos candidate = trunk.offset(face).up(dy);

				if (canStandIn(world, candidate)) {
					return candidate;
				}
			}
		}

		return null;
	}

	/** Room for a worker's feet and head, on ground that will hold it up. */
	private static boolean canStandIn(WorldView world, BlockPos pos) {
		return world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()
				&& world.getBlockState(pos.up()).getCollisionShape(world, pos.up()).isEmpty()
				&& !world.getBlockState(pos.down()).getCollisionShape(world, pos.down()).isEmpty();
	}

	/**
	 * Whether a sapling could stand on {@code soil}: room above, enough light, and soil vanilla
	 * itself would accept. The actual sapling is checked again at planting time, so a modded
	 * sapling that wants unusual ground is not forced into dirt here.
	 */
	public static boolean canPlantAt(WorldView world, BlockPos soil) {
		BlockPos above = soil.up();
		return isPlantingSpace(world, above)
				&& world.getBaseLightLevel(above, 0) >= MIN_LIGHT
				&& Blocks.OAK_SAPLING.getDefaultState().canPlaceAt(world, above);
	}

	/** Whether this particular sapling will survive on {@code soil}. */
	public static boolean canPlant(WorldView world, BlockPos soil, SaplingBlock sapling) {
		BlockPos above = soil.up();
		return isPlantingSpace(world, above)
				&& world.getBaseLightLevel(above, 0) >= MIN_LIGHT
				&& sapling.getDefaultState().canPlaceAt(world, above);
	}

	/**
	 * Whether {@code pos} is somewhere a sapling can go: empty, or holding something that placing
	 * a block there would sweep aside anyway.
	 *
	 * <p>Grass and ferns give way to a player planting into them and give way here too. Insisting
	 * on bare air made a single tuft the reason a dark oak's square came up one corner short,
	 * which is a tree refused over something the planting itself removes.
	 */
	private static boolean isPlantingSpace(WorldView world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		return state.isAir() || isSweptAside(state);
	}

	/**
	 * Whether a tree grown from {@code saplingPos} would have the column and the little of
	 * shoulder room vanilla's smallest trees ask for.
	 *
	 * <p>Not a substitute for the feature's own space check, which differs per wood and is not
	 * something this mod can call without growing the tree. A seven-block chimney of air and a
	 * 3x3 of clear blocks at two heights is enough that an oak or a birch will actually try, and
	 * it is what stops auto-planting from covering a floor that has a roof three blocks up.
	 */
	public static boolean hasRoomToGrow(WorldView world, BlockPos saplingPos) {
		BlockPos.Mutable cursor = new BlockPos.Mutable();

		for (int dy = 1; dy <= GROW_HEIGHT; dy++) {
			cursor.set(saplingPos.getX(), saplingPos.getY() + dy, saplingPos.getZ());

			if (!isGrowClear(world.getBlockState(cursor))) {
				return false;
			}
		}

		for (int dy : new int[] {3, 5}) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					cursor.set(saplingPos.getX() + dx, saplingPos.getY() + dy, saplingPos.getZ() + dz);

					if (!isGrowClear(world.getBlockState(cursor))) {
						return false;
					}
				}
			}
		}

		return true;
	}

	/**
	 * Whether two plantings are close enough that the later one should be skipped. Chebyshev so
	 * a diagonal neighbour counts the same as one on the axis: trees grow a canopy, not a plus.
	 */
	public static boolean tooClose(BlockPos a, BlockPos b) {
		return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getZ() - b.getZ())) < PLANT_SPACING
				&& Math.abs(a.getY() - b.getY()) <= 2;
	}

	/**
	 * Air, leaves, or something a tree would push out of its way. Leaves are treated as clear
	 * because they are what a previous tree left behind and what this station is about to take
	 * down; a solid block is somebody's roof.
	 *
	 * <p>Grass counts as clear for the same reason it counts as plantable. Two-high grass reaches
	 * a block above the sapling, so insisting on air here would have gone on refusing a grassy
	 * clearing even once the square itself was allowed to be grassy.
	 */
	private static boolean isGrowClear(BlockState state) {
		return state.isAir() || isLeaves(state) || isSweptAside(state);
	}

	/**
	 * Whether placing a block here would simply clear this one out of the way, the way planting
	 * into a tuft of grass does. Fluids are replaceable too and are left out: a sapling stood in
	 * water is a sapling washed away the moment it is placed.
	 */
	private static boolean isSweptAside(BlockState state) {
		return state.isReplaceable() && state.getFluidState().isEmpty();
	}

	/**
	 * One tree, already walked: the stump to stand at, the logs to break, and the leaves that
	 * will drop the saplings.
	 *
	 * <p>{@code grown} is whether this was a tree at all. A walk that found only posts or a pile
	 * of logs still names those blocks so the survey can mark them seen, but it is not something
	 * to fell.
	 */
	public record Tree(BlockPos stump, List<BlockPos> logs, List<BlockPos> leaves, boolean grown) {
		/** Whether any of the trunk is still standing, which is what makes the job still worth doing. */
		public boolean standing(ServerWorld world) {
			for (BlockPos log : logs) {
				if (world.isChunkLoaded(log.getX() >> 4, log.getZ() >> 4) && isLog(world.getBlockState(log))) {
					return true;
				}
			}

			return false;
		}

		/** Soil the replacement sapling goes on, which is the block under the stump. */
		public BlockPos soil() {
			return stump.down();
		}

		/**
		 * Every block of this tree in the order it comes down: trunk first, canopy after.
		 *
		 * <p>Logs before leaves so the tree falls the way a lumberjack fells it. The hat is
		 * cleared on the same job once the trunk is gone, which is also what keeps a nether
		 * fungus from standing as a floating wart block after its stem has been taken.
		 *
		 * <p>The cursor walks this whole list before the felling is called finished, so a trunk
		 * that has already come down does not walk the worker off and leave the canopy hanging.
		 */
		public List<BlockPos> falling() {
			List<BlockPos> order = new ArrayList<>(leaves.size() + logs.size());
			order.addAll(logs);
			order.addAll(leaves);
			return order;
		}
	}
}
