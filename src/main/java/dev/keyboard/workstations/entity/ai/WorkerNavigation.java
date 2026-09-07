package dev.keyboard.workstations.entity.ai;

import net.minecraft.block.BlockState;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.entity.ai.pathing.LandPathNodeMaker;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.ai.pathing.PathNodeNavigator;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * Ground navigation that reads a shut fence gate as something to be opened rather than as a wall.
 *
 * <p>Vanilla has no notion of a mob using a gate. {@code LandPathNodeMaker} files every gate that
 * is not already open under {@link PathNodeType#FENCE}, and one FENCE anywhere in a mob's footprint
 * short circuits node typing straight to impassable. Even villagers, which do open doors, are
 * stopped by a gate for exactly this reason. Relabelling the gate {@link PathNodeType#WALKABLE_DOOR}
 * is all it takes for paths to run through it; {@link GateOperator} works the latch in passing.
 *
 * <p>Only gates are relabelled, so real doors keep vanilla's behaviour and nothing else about where
 * the worker may walk changes.
 */
public class WorkerNavigation extends MobNavigation {
	/**
	 * Kept for the node maker below. Assigned after {@code super}, which is where the node maker is
	 * built, but the node maker only reads it once a path is actually being worked out.
	 */
	private final MobEntity navigator;

	public WorkerNavigation(MobEntity mob, World world) {
		super(mob, world);
		navigator = mob;
	}

	/** A shut gate, as opposed to the walls and fence posts that also come back as FENCE. */
	public static boolean isClosedGate(BlockState state) {
		return state.getBlock() instanceof FenceGateBlock && !state.get(FenceGateBlock.OPEN);
	}

	@Override
	protected PathNodeNavigator createPathNodeNavigator(int range) {
		nodeMaker = new GateAwareNodeMaker();
		nodeMaker.setCanEnterOpenDoors(true);
		return new PathNodeNavigator(nodeMaker, range);
	}

	/**
	 * Inner rather than static so it can read the navigating mob, whose station says whether gates
	 * are in play at this station.
	 */
	private class GateAwareNodeMaker extends LandPathNodeMaker {
		@Override
		public PathNodeType getDefaultNodeType(BlockView world, int x, int y, int z) {
			PathNodeType type = super.getDefaultNodeType(world, x, y, z);

			if (type != PathNodeType.FENCE || !gatesAllowed()) {
				return type;
			}

			return isClosedGate(world.getBlockState(new BlockPos(x, y, z)))
					? PathNodeType.WALKABLE_DOOR
					: type;
		}

		private boolean gatesAllowed() {
			return navigator instanceof WorkerMob worker && worker.mayOpenGates();
		}
	}
}
