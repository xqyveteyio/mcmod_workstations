package dev.keyboard.workstations.client;

import dev.keyboard.workstations.entity.LumberjackEntity;
import dev.keyboard.workstations.work.WorkerSkin;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.VillagerResemblingModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * Draws the lumberjack on the vanilla villager model, wearing the mod's own skin. Built exactly
 * like {@link FarmerEntityRenderer}, with its own pair of textures so the two workers can be told
 * apart at a glance.
 *
 * <p>Which pair of files it wears comes from the lumberjack itself, since its station picks its
 * look out of {@link WorkerSkin#LUMBERJACK}.
 */
public class LumberjackEntityRenderer
		extends MobEntityRenderer<LumberjackEntity, VillagerResemblingModel<LumberjackEntity>> {
	/** The same shrink vanilla applies, without which the villager model looks oversized. */
	private static final float MODEL_SCALE = 0.9375F;

	public LumberjackEntityRenderer(EntityRenderDispatcher dispatcher) {
		super(dispatcher, new VillagerResemblingModel<>(0.0F), 0.5F);
		this.addFeature(new WorkerOverlayFeatureRenderer<>(this, entity -> skin(entity).hatTexture()));
	}

	@Override
	public Identifier getTexture(LumberjackEntity entity) {
		return skin(entity).texture();
	}

	private static WorkerSkin skin(LumberjackEntity entity) {
		return WorkerSkin.get(WorkerSkin.LUMBERJACK, entity.getSkin());
	}

	@Override
	public void render(LumberjackEntity entity, float yaw, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light) {
		double sink = entity.getEntrance().sink(tickDelta);

		if (sink > 0.0) {
			matrices.push();
			matrices.translate(0.0, -sink, 0.0);
			super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
			matrices.pop();
		} else {
			super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
		}
	}

	@Override
	protected void scale(LumberjackEntity entity, MatrixStack matrices, float amount) {
		matrices.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
	}

	/** A name tag on a lumberjack still underground is a label lying face up on the floor. */
	@Override
	protected boolean hasLabel(LumberjackEntity entity) {
		return !entity.getEntrance().isBuried() && super.hasLabel(entity);
	}
}
