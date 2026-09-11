package dev.keyboard.workstations.work;

import dev.keyboard.workstations.Mc;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * The box a worker works inside: a rectangle on the ground around the station, reaching a
 * chosen distance up and a chosen distance down.
 *
 * <p>Front-to-back and left-to-right are measured from the station's facing, so turning the
 * block turns the plot. The four reaches are carried around rather than read from the config on
 * the spot, because the client draws this box from values the station sends it, which on a
 * dedicated server are the server's.
 */
public final class WorkArea {
	private final BlockPos center;
	private final int xRadius;
	private final int zRadius;
	private final int above;
	private final int below;

	public WorkArea(BlockPos center, int xRadius, int zRadius, int above, int below) {
		this.center = center.toImmutable();
		this.xRadius = xRadius;
		this.zRadius = zRadius;
		this.above = above;
		this.below = below;
	}

	/**
	 * {@code along} is in front of the station and behind it; {@code across} is to either side.
	 * Facing north, along is Z and across is X; facing east they swap.
	 */
	public static WorkArea of(BlockPos center, Direction facing, int along, int across, int above, int below) {
		boolean northSouth = facing.getAxis() == Direction.Axis.Z;
		return new WorkArea(center, northSouth ? across : along, northSouth ? along : across, above, below);
	}

	public BlockPos getCenter() {
		return center;
	}

	public int getXRadius() {
		return xRadius;
	}

	public int getZRadius() {
		return zRadius;
	}

	public int getAbove() {
		return above;
	}

	public int getBelow() {
		return below;
	}

	/** Block aligned, so the box covers the whole column of every block within the reaches. */
	public Box getBox() {
		return new Box(
				center.getX() - xRadius, center.getY() - below, center.getZ() - zRadius,
				center.getX() + xRadius + 1.0, center.getY() + above + 1.0, center.getZ() + zRadius + 1.0);
	}

	public boolean contains(Entity entity) {
		return contains(Mc.vec(entity));
	}

	/**
	 * Whether a block sits inside the plot. Answered on the block coordinates rather than by
	 * asking the box about a corner, so a block on the far edge counts as in rather than falling
	 * a fraction outside it.
	 */
	public boolean contains(BlockPos pos) {
		int dy = pos.getY() - center.getY();
		return Math.abs(pos.getX() - center.getX()) <= xRadius
				&& Math.abs(pos.getZ() - center.getZ()) <= zRadius
				&& dy <= above && dy >= -below;
	}

	public boolean contains(Vec3d pos) {
		return getBox().contains(pos);
	}
}
