package dev.keyboard.workstations.client;

import dev.keyboard.workstations.Mc;

import dev.keyboard.workstations.McaVillagers;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.nbt.NbtCompound;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Dresses a worker in a Minecraft Comes Alive villager.
 *
 * <p>MCA's villagers are player shaped bipeds wearing layered skin, clothing and hair, nothing like
 * the vanilla villager model a worker otherwise wears, so there is no swapping textures across: the
 * only way to get the look is to let MCA draw it. Both its model and its renderer are bound to
 * {@code VillagerLike}, an interface that drags in MCA's genetics, brain and dialogue systems and
 * so cannot be implemented here without making MCA a hard dependency of the whole mod.
 *
 * <p>Hence a stand in. Each worker gets a real MCA villager, built but never put in the world,
 * posed to match the worker every frame and then handed to MCA's own renderer. The worker itself is
 * untouched: its AI, its name tag, its shadow and its hitbox are all still its own, and only the
 * body anyone sees is borrowed. MCA does the same thing itself for the villager it falls back on
 * when it has no player data to draw from, so building one outside the world is a road it has
 * already been down.
 *
 * <p>What the stand in looks like is not decided here. That was settled on the server and travels
 * on the worker, so nothing on this side rolls anything and every player is dressing the worker in
 * the same villager.
 *
 * <p>None of this goes through an API MCA offers, because it offers none for this, so anything here
 * is free to throw and this class makes no attempt to catch it. {@link WorkerLook} does the
 * catching, out where one failure can put every worker back in its own skin for good.
 */
final class McaVillagerLook {
	/**
	 * Stand ins, held only as long as the workers they belong to are. Nothing here refers back to
	 * the worker, so a worker that goes away takes its stand in with it.
	 */
	private static final Map<MobEntity, StandIn> STAND_INS = new WeakHashMap<>();

	/** Ticks of walking a stand in may catch up on at once, so a slow frame cannot stall on it. */
	private static final int MAX_CATCH_UP = 4;

	private McaVillagerLook() {
	}

	/** A stand in together with the villager data it was dressed in, so a change is noticed. */
	private record StandIn(VillagerEntity villager, NbtCompound wearing) {
	}

	static boolean render(MobEntity worker, NbtCompound disguise, float yaw, float tickDelta,
			MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
		VillagerEntity standIn = standInFor(worker, disguise);
		pose(worker, standIn);

		// Dispatched on the stand in's entity type, which is MCA's, so this hands back MCA's own
		// renderer however the villager in front of it happens to be typed here.
		EntityRenderer<? super VillagerEntity> renderer =
				MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(standIn);
		renderer.render(standIn, yaw, tickDelta, matrices, vertexConsumers, light);
		return true;
	}

	private static VillagerEntity standInFor(MobEntity worker, NbtCompound disguise) {
		StandIn held = STAND_INS.get(worker);

		if (held == null || !held.wearing().equals(disguise)) {
			held = new StandIn(dress(worker, disguise), disguise);
			STAND_INS.put(worker, held);
		}

		return held.villager();
	}

	private static VillagerEntity dress(MobEntity worker, NbtCompound disguise) {
		VillagerEntity standIn = McaVillagers.createStandIn(Mc.world(worker));
		McaVillagers.wear(standIn, disguise);

		// The name plate over a worker is the worker's own business, drawn by the worker's own
		// renderer, so the stand in must not put up a second one of its own.
		standIn.setCustomNameVisible(false);
		return standIn;
	}

	/**
	 * Copies enough of the worker onto its stand in for the two to be drawn as one body.
	 *
	 * <p>Everything read straight off the worker is set both as it is now and as it was last tick,
	 * because the renderer interpolates between the pair of them and a stale previous value would
	 * have the villager swinging back to wherever it last stood on every frame.
	 */
	private static void pose(MobEntity worker, VillagerEntity standIn) {
		walk(worker, standIn);

		standIn.setPos(worker.getX(), worker.getY(), worker.getZ());
		standIn.prevX = worker.prevX;
		standIn.prevY = worker.prevY;
		standIn.prevZ = worker.prevZ;

		//? if >=1.17 {
		standIn.setYaw(worker.getYaw());
		standIn.setPitch(worker.getPitch());
		//?} else {
		/* standIn.yaw = worker.yaw;
		standIn.pitch = worker.pitch; */
		//?}
		standIn.prevYaw = worker.prevYaw;
		standIn.prevPitch = worker.prevPitch;
		standIn.bodyYaw = worker.bodyYaw;
		standIn.prevBodyYaw = worker.prevBodyYaw;
		standIn.headYaw = worker.headYaw;
		standIn.prevHeadYaw = worker.prevHeadYaw;

		standIn.handSwinging = worker.handSwinging;
		standIn.handSwingProgress = worker.handSwingProgress;
		standIn.lastHandSwingProgress = worker.lastHandSwingProgress;
		standIn.hurtTime = worker.hurtTime;
		standIn.maxHurtTime = worker.maxHurtTime;

		standIn.setPose(worker.getPose());
		standIn.setInvisible(worker.isInvisible());
	}

	/**
	 * Swings the stand in's legs at the rate the worker is walking.
	 *
	 * <p>This is the one part of the pose that cannot simply be read across. The limb animator adds
	 * up how far a body has walked and offers no way to say where it has got to, so the stand in
	 * has to be walked forward a tick at a time. Frames are not ticks, so the worker's own age says
	 * how many are owed, and a cap keeps a client that has fallen behind from paying them all at
	 * once.
	 */
	private static void walk(MobEntity worker, VillagerEntity standIn) {
		double travelledX = worker.getX() - worker.prevX;
		double travelledZ = worker.getZ() - worker.prevZ;
		float speed = (float) Math.min(Math.sqrt(travelledX * travelledX + travelledZ * travelledZ) * 4.0, 1.0);
		int owed = Math.min(worker.age - standIn.age, MAX_CATCH_UP);

		for (int tick = 0; tick < owed; tick++) {
			//? if >=1.20 {
			standIn.limbAnimator.updateLimbs(speed, 0.4F);
			//?} else {
			/* standIn.limbDistance += (speed - standIn.limbDistance) * 0.4F;
			standIn.limbAngle += standIn.limbDistance; */
			//?}
		}

		standIn.age = worker.age;
	}
}
