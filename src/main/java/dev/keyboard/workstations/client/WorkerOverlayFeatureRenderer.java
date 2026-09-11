package dev.keyboard.workstations.client;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.VillagerResemblingModel;
import net.minecraft.client.util.math.MatrixStack;
//? if <26.1 {
import net.minecraft.entity.LivingEntity;
//?}
import net.minecraft.util.Identifier;

import java.util.function.Function;

/**
 * Draws a worker's model a second time with another texture, the way vanilla layers a villager's
 * biome and profession clothing over the base villager skin. The overlay shares the model and so
 * also its UV layout, and its limbs are already posed by the pass that ran before this one.
 *
 * <p>Whatever the overlay leaves transparent is discarded by the cutout render layer, so an overlay
 * that paints only the hat boxes shows only a hat. Overlay and base must not paint the same box, or
 * the two copies of that surface will z-fight.
 *
 * <p>Asked for the texture per entity rather than given one, since two workers of the same kind can
 * be dressed differently by their own stations.
 */
//? if >=26.1 {
/* public class WorkerOverlayFeatureRenderer extends FeatureRenderer<WorkerRenderState, VillagerResemblingModel> {
	private final Function<WorkerRenderState, Identifier> texture;

	public WorkerOverlayFeatureRenderer(FeatureRendererContext<WorkerRenderState, VillagerResemblingModel> context,
			Function<WorkerRenderState, Identifier> texture) {
		super(context);
		this.texture = texture;
	}

	@Override
	public void submit(MatrixStack matrices, net.minecraft.client.renderer.SubmitNodeCollector collector, int light,
			WorkerRenderState state, float limbAngle, float limbDistance) {
		Identifier hat = texture.apply(state);

		if (hat == null || state.isInvisible) {
			return;
		}

		collector.submitModel(getParentModel(), state, matrices, hat, light, OverlayTexture.DEFAULT_UV, 0, null);
	}
}

class WorkerRenderState extends net.minecraft.client.renderer.entity.state.VillagerRenderState {
	public Identifier texture;
	public Identifier hatTexture;
	public double sink;
}
*/
//?} else {
public class WorkerOverlayFeatureRenderer<T extends LivingEntity>
		extends FeatureRenderer<T, VillagerResemblingModel<T>> {
	private final Function<T, Identifier> texture;

	public WorkerOverlayFeatureRenderer(FeatureRendererContext<T, VillagerResemblingModel<T>> context,
			Function<T, Identifier> texture) {
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

		VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture.apply(entity)));
		//? if >=1.21 {
		/* getContextModel().render(matrices, consumer, light, OverlayTexture.DEFAULT_UV); */
		//?} else {
		getContextModel().render(matrices, consumer, light, OverlayTexture.DEFAULT_UV, 1.0F, 1.0F, 1.0F, 1.0F);
		//?}
	}
}
//?}
