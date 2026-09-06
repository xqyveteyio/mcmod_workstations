package dev.keyboard.breederscarecrow.entity.ai;

import dev.keyboard.breederscarecrow.ModConfig;
import dev.keyboard.breederscarecrow.entity.RancherEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Works the latch on fence gates the rancher's path runs through, and shuts them behind it.
 *
 * <p>The gate is open for as short a time as possible, because every tick it stands open is a tick
 * an animal could wander out. Two things make the window small: it is only opened once the rancher
 * is about to touch the gate rather than a couple of blocks out, and it is shut the moment the
 * rancher's own hitbox stops overlapping the gateway instead of after a fixed delay.
 *
 * <p>Driven from {@code mobTick()} so all of that is judged every tick. As a {@code Goal} it could
 * not be: {@code GoalSelector.tick()}, the only caller of {@code canStart()}, runs on every other
 * tick, which is a long time to leave a gate hanging open.
 */
public class GateOperator {
	/**
	 * How close the rancher gets before reaching for the latch, squared. The gate's collision box
	 * starts about 0.125 blocks from the centre of its block and the rancher is 0.6 wide, so contact
	 * happens around 0.43 blocks out. Opening at 1.25 leaves a few ticks of margin and no more.
	 */
	private static final double OPEN_RANGE_SQUARED = 1.6;
	/** Path nodes ahead that get checked for a gate. */
	private static final int LOOKAHEAD = 2;
	/** Ticks the gate stays open after the rancher has physically cleared it. */
	private static final int CLOSE_GRACE = 2;
	/**
	 * A cow that parks in the doorway can pin the rancher on the far side of the gate for as long as
	 * it likes, and a gate held open that long defeats the point. Past this the gate shuts even
	 * though the rancher still wants through, and it makes another attempt after the cooldown.
	 */
	private static final int MAX_OPEN_TICKS = 60;
	private static final int REOPEN_COOLDOWN = 20;
	/**
	 * Hard ceiling that applies even when shutting the gate would close it on the rancher, which
	 * otherwise holds it open with no limit at all. A gate standing open is the one outcome worth
	 * avoiding, and the rancher only gets nudged clear of the gateway rather than harmed.
	 */
	private static final int NEVER_OPEN_LONGER_THAN = 200;

	@Nullable
	private BlockPos gate;
	private int grace;
	private int openTicks;
	private int cooldown;

	public void tick(RancherEntity rancher) {
		if (!ModConfig.get().openFenceGates) {
			shut(rancher);
			return;
		}

		if (cooldown > 0) {
			cooldown--;
		}

		if (gate != null) {
			hold(rancher);
			return;
		}

		if (cooldown > 0) {
			return;
		}

		BlockPos ahead = findGateAhead(rancher);

		if (ahead != null) {
			setOpen(rancher, ahead, true);
			gate = ahead;
			grace = CLOSE_GRACE;
			openTicks = 0;
		}
	}

	/**
	 * Whether a gate is currently being worked, including the pause after giving up on a blocked
	 * one. Waiting your turn at a gate is not the same as being unable to get anywhere, so the
	 * brain must not read it as a target it should write off.
	 */
	public boolean isBusy() {
		return gate != null || cooldown > 0;
	}

	/** Shuts whatever is still open, for a rancher that died or was dismissed mid gateway. */
	public void shut(RancherEntity rancher) {
		if (gate != null) {
			setOpen(rancher, gate, false);
			gate = null;
		}
	}

	private void hold(RancherEntity rancher) {
		BlockPos current = gate;

		if (current == null) {
			return;
		}

		BlockState state = rancher.getWorld().getBlockState(current);

		// Somebody else shut it, or the gate was broken while the rancher was walking through.
		if (!(state.getBlock() instanceof FenceGateBlock) || !state.get(FenceGateBlock.OPEN)) {
			gate = null;
			return;
		}

		openTicks++;
		boolean wouldTrap = wouldTrap(rancher, current, state);

		if ((!wouldTrap && openTicks > MAX_OPEN_TICKS) || openTicks > NEVER_OPEN_LONGER_THAN) {
			setOpen(rancher, current, false);
			gate = null;
			cooldown = REOPEN_COOLDOWN;
			return;
		}

		if (wouldTrap || isOnPathAhead(rancher, current)) {
			grace = CLOSE_GRACE;
			return;
		}

		if (--grace <= 0) {
			setOpen(rancher, current, false);
			gate = null;
		}
	}

	/**
	 * True while shutting the gate would close it on the rancher. Asking the block for the collision
	 * shape it would have while shut is exact, where a guessed margin around the gate's block is
	 * not: the shape is a thin slab across the passage, so it stops overlapping the moment the
	 * rancher is through, rather than while it merely stands in the neighbouring block.
	 */
	private static boolean wouldTrap(RancherEntity rancher, BlockPos pos, BlockState state) {
		Box body = rancher.getBoundingBox();

		for (Box slab : state.with(FenceGateBlock.OPEN, false)
				.getCollisionShape(rancher.getWorld(), pos)
				.getBoundingBoxes()) {
			if (slab.offset(pos.getX(), pos.getY(), pos.getZ()).intersects(body)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * True while the gate is one of the next few steps, which covers both walking up to it and
	 * turning round to come back through. Once it drops off the path the rancher is done with it.
	 */
	private static boolean isOnPathAhead(RancherEntity rancher, BlockPos pos) {
		Path path = rancher.getNavigation().getCurrentPath();

		if (path == null || path.isFinished()) {
			return false;
		}

		int last = Math.min(path.getCurrentNodeIndex() + LOOKAHEAD, path.getLength());

		for (int index = path.getCurrentNodeIndex(); index < last; index++) {
			if (path.getNode(index).getBlockPos().equals(pos)) {
				return true;
			}
		}

		return false;
	}

	@Nullable
	private static BlockPos findGateAhead(RancherEntity rancher) {
		Path path = rancher.getNavigation().getCurrentPath();

		if (path == null || path.isFinished()) {
			return null;
		}

		int last = Math.min(path.getCurrentNodeIndex() + LOOKAHEAD, path.getLength());

		for (int index = path.getCurrentNodeIndex(); index < last; index++) {
			BlockPos pos = path.getNode(index).getBlockPos();

			if (horizontalDistanceSquared(rancher, pos) <= OPEN_RANGE_SQUARED
					&& RancherNavigation.isClosedGate(rancher.getWorld().getBlockState(pos))) {
				return pos;
			}
		}

		return null;
	}

	/** Measured flat, since a gate the rancher is walking through is at its own feet level. */
	private static double horizontalDistanceSquared(RancherEntity rancher, BlockPos pos) {
		double dx = rancher.getX() - (pos.getX() + 0.5);
		double dz = rancher.getZ() - (pos.getZ() + 0.5);
		return dx * dx + dz * dz;
	}

	private static void setOpen(RancherEntity rancher, BlockPos pos, boolean open) {
		World world = rancher.getWorld();
		BlockState state = world.getBlockState(pos);

		if (!(state.getBlock() instanceof FenceGateBlock) || state.get(FenceGateBlock.OPEN) == open) {
			return;
		}

		world.setBlockState(pos, state.with(FenceGateBlock.OPEN, open), Block.NOTIFY_LISTENERS);
		world.playSound(null, pos,
				open ? SoundEvents.BLOCK_FENCE_GATE_OPEN : SoundEvents.BLOCK_FENCE_GATE_CLOSE,
				SoundCategory.BLOCKS, 1.0F, world.getRandom().nextFloat() * 0.1F + 0.9F);
		world.emitGameEvent(rancher, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
	}
}
