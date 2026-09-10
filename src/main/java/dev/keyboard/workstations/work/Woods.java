package dev.keyboard.workstations.work;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SaplingBlock;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * What the lumberjack knows about trees and saplings, kept apart from the AI so "is this a tree,
 * and will a sapling actually grow here" can be reasoned about on its own.
 *
 * <p>Logs and leaves are recognised by the vanilla tags, not by a hardcoded list of woods. A tag
 * is what a modded tree joins to be treated as wood by the rest of the game, so it is also what
 * makes one of those trees this station's business. Everything else is left alone: a log cabin
 * built of logs will be taken, because a log is a log, but a house of planks or a fence is not
 * in the tags and is never touched.
 *
 * <p>A tree is gathered by flood-filling from a log. The fill walks every neighbouring log,
 * including diagonally and down, because a branch is still part of the same tree when it steps
 * sideways or dips; stopping at the six cardinal faces would leave an acacia half standing. The
 * walk is capped so a giant jungle or a mod's world tree cannot hold the tick hostage.
 *
 * <p>Whether the canopy comes down with the trunk is a station setting. Decay is random and only
 * runs while a leaf is still near a log, so a tree whose last log has just gone can sit for a
 * long time dropping nothing, and the saplings the lumberjack needs to replant come out of the
 * leaf loot table. Breaking the leaves makes that harvest immediate and reliable; leaving them
 * is cheaper and quieter, and replanting then runs on whatever saplings the station already
 * holds. The flood fill that would walk the canopy is skipped entirely when the setting is off.
 * The same cap applies when it is on, and only tagged leaves are broken.
 */
public final class Woods {
	/**
	 * Most logs one flood fill will take. Large enough for a grown oak or a modest jungle, small
	 * enough that a 2x2 mega spruce or a mod's giant is cut short and finished on a later pass
	 * rather than walked in one tick.
	 */
	public static final int MAX_LOGS = 256;
	/** Same bound for the canopy, which is usually several times the trunk. */
	public static final int MAX_LEAVES = 256;
	/**
	 * How far a leaf may sit from a log of this tree and still be taken. Vanilla's own leaf
	 * distance tops out at 7; staying inside that keeps a neighbouring tree's canopy, which is
	 * equally close to its own trunk, from being stolen.
	 */
	public static final int LEAF_REACH = 6;
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

	private Woods() {
	}

	public static boolean isLog(BlockState state) {
		return state.isIn(BlockTags.LOGS);
	}

	public static boolean isLeaves(BlockState state) {
		return state.isIn(BlockTags.LEAVES);
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
	 * Dark oak is the one vanilla sapling that will not grow from a single block. Planting one
	 * on its own in a wood that will never see the other three is a sapling spent on nothing.
	 */
	public static boolean needsSquare(Item sapling) {
		return sapling instanceof BlockItem blockItem && blockItem.getBlock() == Blocks.DARK_OAK_SAPLING;
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
	 */
	@Nullable
	public static BlockPos squareFrom(WorldView world, BlockPos soil, SaplingBlock sapling) {
		for (int dx = 1 - SQUARE_SIDE; dx <= 0; dx++) {
			for (int dz = 1 - SQUARE_SIDE; dz <= 0; dz++) {
				BlockPos corner = soil.add(dx, 0, dz);

				if (squareWorks(world, corner, sapling)) {
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

	private static boolean squareWorks(WorldView world, BlockPos corner, SaplingBlock sapling) {
		boolean gap = false;

		for (BlockPos plot : square(corner)) {
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
	 *
	 * @param includeLeaves whether to walk the canopy. Off leaves the list empty, which is a
	 * tree the lumberjack will fell for its logs and otherwise leave standing to decay.
	 */
	public static Tree gather(ServerWorld world, BlockPos start, boolean includeLeaves) {
		List<BlockPos> logs = new ArrayList<>();
		Set<BlockPos> seen = new LinkedHashSet<>();
		Queue<BlockPos> queue = new ArrayDeque<>();
		queue.add(start.toImmutable());
		seen.add(start.toImmutable());

		BlockPos.Mutable cursor = new BlockPos.Mutable();

		while (!queue.isEmpty() && logs.size() < MAX_LOGS) {
			BlockPos current = queue.poll();

			if (!world.isChunkLoaded(current.getX() >> 4, current.getZ() >> 4)) {
				continue;
			}

			if (!isLog(world.getBlockState(current))) {
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
			return new Tree(start.toImmutable(), List.of(), List.of());
		}

		return new Tree(stumpOf(world, logs), logs, includeLeaves ? canopy(world, logs) : List.of());
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
	 * Leaves hanging off these logs, walked the way vanilla walks leaf distance: six faces, and
	 * only as far as a leaf can sit from a log before it would decay on its own.
	 */
	private static List<BlockPos> canopy(ServerWorld world, List<BlockPos> logs) {
		List<BlockPos> leaves = new ArrayList<>();
		Set<BlockPos> seen = new LinkedHashSet<>(logs);
		Queue<LeafStep> queue = new ArrayDeque<>();

		for (BlockPos log : logs) {
			queue.add(new LeafStep(log, 0));
		}

		BlockPos.Mutable cursor = new BlockPos.Mutable();

		while (!queue.isEmpty() && leaves.size() < MAX_LEAVES) {
			LeafStep step = queue.poll();

			if (step.distance >= LEAF_REACH) {
				continue;
			}

			for (Direction face : Direction.values()) {
				cursor.set(step.pos).move(face);
				BlockPos next = cursor.toImmutable();

				if (!seen.add(next) || !world.isChunkLoaded(next.getX() >> 4, next.getZ() >> 4)) {
					continue;
				}

				if (!isLeaves(world.getBlockState(next))) {
					continue;
				}

				leaves.add(next);
				queue.add(new LeafStep(next, step.distance + 1));
			}
		}

		return leaves;
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
	 *
	 * <p>Fluids are replaceable too and are not accepted: a sapling stood in water is a sapling
	 * washed away the moment it is placed.
	 */
	private static boolean isPlantingSpace(WorldView world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		return state.isAir() || (state.isReplaceable() && state.getFluidState().isEmpty());
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
	 * Air or leaves. Leaves are treated as clear because they are what a previous tree left
	 * behind and what this station is about to take down; a solid block is somebody's roof.
	 */
	private static boolean isGrowClear(BlockState state) {
		return state.isAir() || isLeaves(state);
	}

	private record LeafStep(BlockPos pos, int distance) {
	}

	/**
	 * One tree, already walked: the stump to stand at, the logs to break, and the leaves that
	 * will drop the saplings. The leaf list is empty when the station is leaving the canopy
	 * to decay, which is still a usable tree — there is simply nothing to break after the trunk.
	 */
	public record Tree(BlockPos stump, List<BlockPos> logs, List<BlockPos> leaves) {
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
	}
}
