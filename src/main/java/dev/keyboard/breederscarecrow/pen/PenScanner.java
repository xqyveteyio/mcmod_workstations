package dev.keyboard.breederscarecrow.pen;

import dev.keyboard.breederscarecrow.ModConfig;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

import java.util.ArrayDeque;

/**
 * Flood fills the floor around a scarecrow and stops at anything an animal cannot walk through,
 * which is what a one block high fence or wall looks like from the ground.
 */
public final class PenScanner {
	private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

	private PenScanner() {
	}

	private enum CellKind {
		/** Animals can stand here. */
		FLOOR,
		/** Blocked at foot level, so it acts as a pen wall. */
		WALL,
		/** Nothing solid underneath, so animals would fall out of the pen. */
		HOLE
	}

	public static PenRegion scan(World world, BlockPos origin) {
		ModConfig config = ModConfig.get();
		int floorY = origin.getY();
		LongOpenHashSet cells = new LongOpenHashSet();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		boolean leaking = false;

		for (Direction direction : HORIZONTAL) {
			BlockPos start = origin.offset(direction);
			CellKind kind = classify(world, start);

			if (kind == CellKind.FLOOR) {
				cells.add(start.asLong());
				queue.add(start);
			} else if (kind == CellKind.HOLE) {
				leaking = true;
			}
		}

		while (!queue.isEmpty()) {
			if (cells.size() >= config.scanMaxCells) {
				leaking = true;
				break;
			}

			BlockPos current = queue.poll();

			for (Direction direction : HORIZONTAL) {
				BlockPos next = current.offset(direction);

				if (next.equals(origin) || cells.contains(next.asLong())) {
					continue;
				}

				if (Math.abs(next.getX() - origin.getX()) > config.scanMaxRadius
						|| Math.abs(next.getZ() - origin.getZ()) > config.scanMaxRadius) {
					leaking = true;
					continue;
				}

				if (!world.isChunkLoaded(next.getX() >> 4, next.getZ() >> 4)) {
					leaking = true;
					continue;
				}

				CellKind kind = classify(world, next);

				if (kind == CellKind.WALL) {
					continue;
				}

				if (kind == CellKind.HOLE) {
					leaking = true;
					continue;
				}

				cells.add(next.asLong());
				queue.add(next);
			}
		}

		return new PenRegion(floorY, !leaking && !cells.isEmpty(), cells);
	}

	private static CellKind classify(World world, BlockPos pos) {
		if (!isWalkThrough(world, pos)) {
			return CellKind.WALL;
		}

		BlockPos below = pos.down();
		BlockState belowState = world.getBlockState(below);

		if (!belowState.getFluidState().isEmpty()) {
			return CellKind.FLOOR;
		}

		return belowState.getCollisionShape(world, below).isEmpty() ? CellKind.HOLE : CellKind.FLOOR;
	}

	private static boolean isWalkThrough(World world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);

		if (state.isAir()) {
			return true;
		}

		VoxelShape shape = state.getCollisionShape(world, pos);

		if (shape.isEmpty()) {
			return true;
		}

		// Carpets, pressure plates and similar flat blocks are stepped over rather than blocking.
		return shape.getMax(Direction.Axis.Y) <= 0.5;
	}
}
