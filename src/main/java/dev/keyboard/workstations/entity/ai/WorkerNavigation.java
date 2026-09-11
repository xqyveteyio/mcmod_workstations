package dev.keyboard.workstations.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * Ground navigation that reads a shut fence gate as something to be opened rather than as a wall.
 *
 * <p>Vanilla has no notion of a mob using a gate. {@code LandPathNodeMaker} files every gate that
 * is not already open under {@link PathType#FENCE}, and one FENCE anywhere in a mob's footprint
 * short circuits node typing straight to impassable. Even villagers, which do open doors, are
 * stopped by a gate for exactly this reason. Relabelling the gate {@link PathType#WALKABLE_DOOR}
 * is all it takes for paths to run through it; {@link GateOperator} works the latch in passing.
 *
 * <p>Only gates are relabelled, so real doors keep vanilla's behaviour and nothing else about where
 * the worker may walk changes.
 */
public class WorkerNavigation extends GroundPathNavigation {
	/**
	 * Kept for the node maker below. Assigned after {@code super}, which is where the node maker is
	 * built, but the node maker only reads it once a path is actually being worked out.
	 */
	private final Mob navigator;

	public WorkerNavigation(Mob mob, Level world) {
		super(mob, world);
		navigator = mob;
	}

	/** A shut gate, as opposed to the walls and fence posts that also come back as FENCE. */
	public static boolean isClosedGate(BlockState state) {
		return state.getBlock() instanceof FenceGateBlock && !state.getValue(FenceGateBlock.OPEN);
	}

	@Override
	protected PathFinder createPathFinder(int range) {
		nodeEvaluator = new GateAwareNodeMaker();
		nodeEvaluator.setCanPassDoors(true);
		return new PathFinder(nodeEvaluator, range);
	}

	/**
	 * Inner rather than static so it can read the navigating mob, whose station says whether gates
	 * are in play at this station.
	 */
	private class GateAwareNodeMaker extends WalkNodeEvaluator {
		@Override
		public PathType getPathType(PathfindingContext context, int x, int y, int z) {
			PathType type = super.getPathType(context, x, y, z);

			if (type != PathType.FENCE || !gatesAllowed()) {
				return type;
			}

			return isClosedGate(context.getBlockState(new BlockPos(x, y, z)))
					? PathType.WALKABLE_DOOR
					: type;
		}

		private boolean gatesAllowed() {
			return navigator instanceof WorkerMob worker && worker.mayOpenGates();
		}
	}
}
