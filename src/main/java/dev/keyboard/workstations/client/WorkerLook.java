package dev.keyboard.workstations.client;

import dev.keyboard.workstations.McaVillagers;
import dev.keyboard.workstations.WorkstationsMod;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;

/**
 * Whether a worker is drawn as itself or borrowed from another mod.
 *
 * <p>This class names no class from any of those mods, which is the whole point of it. A class is
 * not loaded until a line naming it actually runs, so {@link McaSupport} is what keeps the game
 * from going looking for classes that are not installed, or installed in a version that no longer
 * has them.
 *
 * <p>The safety net has to live out here too, rather than inside the bridge it guards. A version
 * that passes the check is still no promise that the classes compiled against are the ones present:
 * MCA can be repackaged, in which case the failure is not something the bridge throws but the
 * bridge itself failing to load. A net cast inside it would go down with it.
 */
public final class WorkerLook {
	/** Set once the MCA side has failed even once, which puts every worker back in its own skin. */
	private static boolean mcaGivenUp;

	private WorkerLook() {
	}

	/**
	 * Draws the worker as the Minecraft Comes Alive villager whose looks it has been lent.
	 *
	 * <p>An empty disguise means there is nothing to put on: either the server has not rolled one
	 * yet, or it is a server without MCA on it and never will.
	 *
	 * @return whether the worker has been drawn, so the caller knows to skip its own drawing
	 */
	public static boolean renderAsMcaVillager(MobEntity worker, NbtCompound disguise, float yaw,
			float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
		if (!McaSupport.available() || mcaGivenUp || disguise.isEmpty()) {
			return false;
		}

		try {
			return McaVillagerLook.render(worker, disguise, yaw, tickDelta, matrices, vertexConsumers, light);
		} catch (Throwable failure) {
			mcaGivenUp = true;
			WorkstationsMod.LOGGER.warn("""
					Could not dress workers as Minecraft Comes Alive villagers, so they will keep their own look. \
					If MCA was installed as its combined "universal" download, swap it for the Fabric one: the \
					combined build renames every class inside it, so nothing outside it can find them.""", failure);
			return false;
		}
	}
}
