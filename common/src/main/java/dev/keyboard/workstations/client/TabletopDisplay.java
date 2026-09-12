package dev.keyboard.workstations.client;

import org.jetbrains.annotations.Nullable;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * The miniature pen on a station's tabletop, where one of each kind of livestock stands in a fenced
 * corner.
 */
final class TabletopDisplay {
	private static final float TABLETOP = 12.0F / 16.0F;
	private static final float SIZE = 0.26F;

	static final List<Slot> SLOTS = List.of(
			new Slot(EntityType.COW, 0.31F, 0.31F, 225.0F),
			new Slot(EntityType.SHEEP, 0.69F, 0.31F, 135.0F),
			new Slot(EntityType.PIG, 0.31F, 0.69F, 315.0F),
			new Slot(EntityType.CHICKEN, 0.69F, 0.69F, 45.0F));

	private static final Map<Level, Map<EntityType<?>, LivingEntity>> STAND_INS = new WeakHashMap<>();

	private TabletopDisplay() {
	}

	record Slot(EntityType<? extends LivingEntity> type, float x, float z, float yaw) {
	}

	record Drawn(EntityRenderState state, float x, float z, float yaw, float scale) {
	}

	static void extract(Level world, float tickDelta, List<Drawn> out) {
		EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();

		for (Slot slot : SLOTS) {
			LivingEntity animal = standIn(world, slot.type());

			if (animal == null) {
				continue;
			}

			float scale = SIZE / Math.max(animal.getBbWidth(), animal.getBbHeight());
			out.add(new Drawn(dispatcher.extractEntity(animal, tickDelta), slot.x(), slot.z(), slot.yaw(), scale));
		}
	}

	static void submit(List<Drawn> animals, PoseStack matrices, SubmitNodeCollector collector,
			CameraRenderState camera, int light) {
		EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();

		for (Drawn animal : animals) {
			EntityRenderState state = animal.state();
			state.lightCoords = light;
			state.isInvisible = false;
			state.shadowPieces.clear();
			matrices.pushPose();
			matrices.translate(animal.x(), TABLETOP, animal.z());
			matrices.mulPose(Axis.YP.rotationDegrees(animal.yaw()));
			matrices.scale(animal.scale(), animal.scale(), animal.scale());
			dispatcher.submit(state, camera, 0.0, 0.0, 0.0, matrices, collector);
			matrices.popPose();
		}
	}

	/** A camera that only exists so item rendering can reuse the same submit path as the world. */
	static CameraRenderState itemCamera() {
		CameraRenderState camera = new CameraRenderState();
		camera.pos = net.minecraft.world.phys.Vec3.ZERO;
		camera.orientation = new org.joml.Quaternionf();
		camera.initialized = true;
		return camera;
	}

	@Nullable
	private static LivingEntity standIn(Level world, EntityType<? extends LivingEntity> type) {
		return STAND_INS.computeIfAbsent(world, key -> new HashMap<>())
				.computeIfAbsent(type, key -> settle(type.create(world, EntitySpawnReason.LOAD)));
	}

	@Nullable
	private static LivingEntity settle(@Nullable LivingEntity animal) {
		if (animal != null) {
			animal.setYRot(0.0F);
			animal.yRotO = 0.0F;
			animal.yHeadRot = 0.0F;
			animal.yHeadRotO = 0.0F;
			animal.yBodyRot = 0.0F;
			animal.yBodyRotO = 0.0F;
		}

		return animal;
	}
}
