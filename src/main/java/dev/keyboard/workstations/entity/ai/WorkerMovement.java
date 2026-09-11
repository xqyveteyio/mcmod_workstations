package dev.keyboard.workstations.entity.ai;

import dev.keyboard.workstations.Mc;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Getting a worker's body where the work is: walking to somewhere too far to path to in one go, and
 * shouldering aside whatever stands in the way. Both are wanted by every kind of worker, and both
 * are about locomotion rather than about deciding what to do, so they live away from the brains.
 */
public final class WorkerMovement {
	/**
	 * How far off something may be before it is walked at in stages rather than pathed to directly,
	 * in blocks.
	 *
	 * <p>Pathfinding cannot see beyond the mob's follow range: the search stops expanding at nodes
	 * further from the worker than that, so a request for anything past it comes back as a path
	 * that does not reach, exactly as if a wall were in the way. Follow range is 32 while the work
	 * radius goes up to 64, which quietly wrote off everything in the outer ring of a wide area
	 * however open the ground was. Standing at its post the worker measures from the middle, so
	 * the ring started at 32 blocks out, or nearer than that towards the corners.
	 *
	 * <p>Raising follow range instead is a trap, because it is also the search bound: one genuinely
	 * unreachable target in a wide area would then have the search exhaust every node within tens
	 * of blocks before admitting defeat, on a scan that repeats. Walked in hops, each search stays
	 * the size it has always been no matter how large the area is.
	 */
	public static final double PATH_RADIUS = 24.0;
	/** How far ahead each hop of a staged approach aims, in blocks. */
	public static final double APPROACH_STEP = 16.0;

	/** How far ahead blockers get shouldered aside, in blocks. */
	private static final double SHOVE_RANGE = 1.8;
	/**
	 * Velocity added per tick at point blank range, tapering to nothing at {@link #SHOVE_RANGE}.
	 *
	 * <p>Safe to be firm about, because the shove is only ever sideways. A blocker cannot be driven
	 * through the gap the worker is heading for however hard it is pushed, and velocity is still
	 * stopped by walls, so a penned animal stays penned.
	 */
	private static final double SHOVE_STRENGTH = 0.14;
	/** Cosine of the cone in front of the worker that counts as being in the way. */
	private static final double SHOVE_CONE = 0.3;

	private WorkerMovement() {
	}

	/** Whether a destination is beyond what pathfinding can answer for and needs walking in hops. */
	public static boolean isFarOff(MobEntity worker, Vec3d destination) {
		return worker.squaredDistanceTo(destination) > PATH_RADIUS * PATH_RADIUS;
	}

	/**
	 * Walks one hop towards a destination too far away to path to, aiming at a point on the
	 * straight line to it. Called again on every repath, the hops carry the worker along until the
	 * real destination comes into range and normal pathing takes over.
	 *
	 * @return whether the destination was far enough to need this, and a hop was therefore started
	 */
	public static boolean approach(MobEntity worker, Vec3d destination, double speed) {
		if (!isFarOff(worker, destination)) {
			return false;
		}

		Vec3d hop = worker.getPos()
				.add(destination.subtract(worker.getPos()).normalize().multiply(APPROACH_STEP));
		worker.getNavigation().startMovingTo(hop.x, hop.y, hop.z, speed);
		return true;
	}

	/**
	 * Shoulders whatever is in the way aside while walking. Mobs only shove each other once their
	 * hitboxes already overlap, which is too late to stop a cow stood in a doorway from sending the
	 * worker the long way round, or nowhere at all.
	 *
	 * <p>Only while actually navigating, and only for what is ahead. Jostling the herd while stood
	 * still would keep animals in love from ever reaching each other, so nothing would breed.
	 *
	 * <p>The shove goes sideways rather than straight ahead on purpose: pushing a blocker along the
	 * direction of travel would drive whatever stands in a gateway right through it, which for an
	 * animal means out of the pen. Sideways clears the corridor without ever pushing anything
	 * through the gap the worker is heading for.
	 */
	public static void shoveBlockers(MobEntity worker) {
		if (worker.getNavigation().isIdle()) {
			return;
		}

		Vec3d forward = Vec3d.fromPolar(0.0F, worker.bodyYaw);

		for (Entity other : Mc.world(worker).getOtherEntities(worker,
				worker.getBoundingBox().expand(SHOVE_RANGE, 0.5, SHOVE_RANGE),
				candidate -> candidate.isPushable() && !(candidate instanceof PlayerEntity))) {
			double dx = other.getX() - worker.getX();
			double dz = other.getZ() - worker.getZ();
			double distance = Math.sqrt(dx * dx + dz * dz);

			if (distance < 1.0E-4 || distance > SHOVE_RANGE) {
				continue;
			}

			if ((forward.x * dx + forward.z * dz) / distance < SHOVE_CONE) {
				continue;
			}

			double sideX = -forward.z;
			double sideZ = forward.x;
			double lean = sideX * dx + sideZ * dz;
			// Pushed towards the side it already leans, so the two never disagree about which way
			// it should go. Dead ahead there is no such side, so its id picks one and sticks to it.
			boolean flip = Math.abs(lean) < 1.0E-3 ? (Mc.entityId(other) & 1) == 0 : lean < 0.0;

			if (flip) {
				sideX = -sideX;
				sideZ = -sideZ;
			}

			double push = SHOVE_STRENGTH * (1.0 - distance / SHOVE_RANGE);
			other.addVelocity(sideX * push, 0.0, sideZ * push);
			other.velocityModified = true;
		}
	}
}
