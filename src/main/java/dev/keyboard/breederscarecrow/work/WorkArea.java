package dev.keyboard.breederscarecrow.work;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * The box the rancher works inside: a horizontal square around the station block, given some
 * headroom above and below so animals on a slope or on top of a fence still count.
 *
 * <p>The radius is carried around rather than read from the config on the spot because the client
 * draws this box from values the station sends it, which on a dedicated server are the server's.
 */
public final class WorkArea {
	private final BlockPos center;
	private final int radius;
	private final int height;

	public WorkArea(BlockPos center, int radius, int height) {
		this.center = center.toImmutable();
		this.radius = radius;
		this.height = height;
	}

	public BlockPos getCenter() {
		return center;
	}

	public int getRadius() {
		return radius;
	}

	public int getHeight() {
		return height;
	}

	/** Block aligned, so the box covers the whole column of every block within the radius. */
	public Box getBox() {
		return new Box(
				center.getX() - radius, center.getY() - height, center.getZ() - radius,
				center.getX() + radius + 1.0, center.getY() + height + 1.0, center.getZ() + radius + 1.0);
	}

	public boolean contains(Entity entity) {
		return contains(entity.getPos());
	}

	public boolean contains(Vec3d pos) {
		return getBox().contains(pos);
	}
}
