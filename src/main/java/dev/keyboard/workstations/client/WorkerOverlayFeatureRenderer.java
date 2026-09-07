package dev.keyboard.workstations.client;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.VillagerResemblingModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;

/**
 * Draws a worker's model a second time with another texture, the way vanilla layers a villager's
 * biome and profession clothing over the base villager skin. The overlay shares the model and so
 * also its UV layout, and its limbs are already posed by the pass that ran before this one.
 *
 * <p>Whatever the overlay leaves transparent is discarded by the cutout render layer, so an overlay
 * that paints only the hat boxes shows only a hat. Overlay and base must not paint the same box, or
 * the two copies of that surface will z-fight.
 */
public class WorkerOverlayFeatureRenderer<T extends LivingEntity>
		extends FeatureRenderer<T, VillagerResemblingModel<T>> {
	private final Identifier texture;

	public WorkerOverlayFeatureRenderer(FeatureRendererContext<T, VillagerResemblingModel<T>> context,
			Identifier texture) {
		super(context);
		this.texture = texture;
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, T entity,
			float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw,
			float headPitch) {
		if (entity.isInvisible()) {
			return;
		}

		VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture));
		getContextModel().render(matrices, consumer, light, OverlayTexture.DEFAULT_UV, 1.0F, 1.0F, 1.0F, 1.0F);
	}
}
