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
 * <p>{@link McaVillagers} is what decides whether there is anything to borrow, and none of what it
 * reaches for is an API MCA offers. So the safety net lives out here, where one failure can put
 * every worker back in its own skin for the rest of the session rather than throwing once a frame
 * for as long as the world is open.
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
		if (!McaVillagers.usable() || mcaGivenUp || disguise.isEmpty()) {
			return false;
		}

		try {
			return McaVillagerLook.render(worker, disguise, yaw, tickDelta, matrices, vertexConsumers, light);
		} catch (Throwable failure) {
			mcaGivenUp = true;
			WorkstationsMod.LOGGER.warn(
					"Could not dress workers as Minecraft Comes Alive villagers, so they will keep their own look",
					failure);
			return false;
		}
	}
}
