package dev.keyboard.breederscarecrow.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The miniature pen on a station's tabletop, where one of each kind of livestock stands in a fenced
 * corner. Shared so the placed block and the item in your hand lay their pen out the same way.
 *
 * <p>The pen is a sign saying what the station is for, not a readout of the herd, so it is always
 * full. The animals are throwaway client side instances that are never added to the world, the
 * trick vanilla's mob spawner uses to show the mob it is about to produce.
 */
final class TabletopDisplay {
	/** Top of the table in the block model, which the animals stand on. */
	private static final float TABLETOP = 12.0F / 16.0F;
	/** Longest side of an animal, in blocks, so a cow and a chicken both read as ornaments. */
	private static final float SIZE = 0.26F;

	/**
	 * Where each kind stands, in block coordinates on the tabletop, and which way it is turned. The
	 * four corners of a pen whose fence runs one pixel in from the block's edge.
	 */
	static final List<Slot> SLOTS = List.of(
			new Slot(EntityType.COW, 0.31F, 0.31F, 225.0F),
			new Slot(EntityType.SHEEP, 0.69F, 0.31F, 135.0F),
			new Slot(EntityType.PIG, 0.31F, 0.69F, 315.0F),
			new Slot(EntityType.CHICKEN, 0.69F, 0.69F, 45.0F));

	/**
	 * Stand-ins by world, so leaving a world lets its animals go. One instance per kind is enough:
	 * they hold no per station state, and every pen draws them in the same pose.
	 */
	private static final Map<World, Map<EntityType<?>, LivingEntity>> STAND_INS = new WeakHashMap<>();

	private TabletopDisplay() {
	}

	/** One kind's reserved corner of the pen. */
	record Slot(EntityType<? extends LivingEntity> type, float x, float z, float yaw) {
	}

	/** Draws one of every kind, each in its own corner, in the block's own coordinates. */
	static void render(World world, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light) {
		EntityRenderDispatcher dispatcher = MinecraftClient.getInstance().getEntityRenderDispatcher();

		// The stand-ins never left the world origin, so their shadows would land nowhere useful.
		dispatcher.setRenderShadows(false);

		for (Slot slot : SLOTS) {
			LivingEntity animal = standIn(world, slot.type());

			if (animal == null) {
				continue;
			}

			matrices.push();
			matrices.translate(slot.x(), TABLETOP, slot.z());
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(slot.yaw()));
			float scale = SIZE / Math.max(animal.getWidth(), animal.getHeight());
			matrices.scale(scale, scale, scale);
			dispatcher.render(animal, 0.0, 0.0, 0.0, 0.0F, tickDelta, matrices, vertexConsumers, light);
			matrices.pop();
		}

		dispatcher.setRenderShadows(true);
	}

	@Nullable
	private static LivingEntity standIn(World world, EntityType<? extends LivingEntity> type) {
		return STAND_INS.computeIfAbsent(world, key -> new HashMap<>())
				.computeIfAbsent(type, key -> settle(type.create(world)));
	}

	/**
	 * Stops a stand-in twitching. Every living entity is born facing a small random angle, which its
	 * constructor copies to {@code headYaw} while leaving {@code prevHeadYaw} at zero. A mob in the
	 * world evens the two out on its first tick, but nothing ever ticks a stand-in, so the renderer
	 * goes on interpolating between them and the head snaps back and forth twenty times a second.
	 */
	@Nullable
	private static LivingEntity settle(@Nullable LivingEntity animal) {
		if (animal != null) {
			animal.setYaw(0.0F);
			animal.prevYaw = 0.0F;
			animal.headYaw = 0.0F;
			animal.prevHeadYaw = 0.0F;
			animal.bodyYaw = 0.0F;
			animal.prevBodyYaw = 0.0F;
		}

		return animal;
	}
}
