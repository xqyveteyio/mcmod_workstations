package dev.keyboard.workstations.client;

import dev.keyboard.workstations.entity.FarmerEntity;
import dev.keyboard.workstations.work.WorkerSkin;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.MobEntityRenderer;
//? if >=1.17 {
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
//?} else {
/* import net.minecraft.client.render.entity.EntityRenderDispatcher; */
//?}
import net.minecraft.client.render.entity.model.VillagerResemblingModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * Draws the farmer on the vanilla villager model, wearing the mod's own skin. Built exactly like
 * {@link RancherEntityRenderer}, with its own pair of textures so the two workers can be told apart
 * at a glance.
 *
 * <p>Which pair of files it wears comes from the farmer itself, since its station picks its look
 * out of {@link WorkerSkin#FARMER}.
 */
public class FarmerEntityRenderer extends MobEntityRenderer<FarmerEntity, VillagerResemblingModel<FarmerEntity>> {
	/** The same shrink vanilla applies, without which the villager model looks oversized. */
	private static final float MODEL_SCALE = 0.9375F;

	//? if >=1.17 {
	public FarmerEntityRenderer(EntityRendererFactory.Context context) {
		super(context, new VillagerResemblingModel<>(context.getPart(EntityModelLayers.VILLAGER)), 0.5F);
		this.addFeature(new WorkerOverlayFeatureRenderer<>(this, entity -> skin(entity).hatTexture()));
	}
	//?} else {
	/* public FarmerEntityRenderer(EntityRenderDispatcher dispatcher) {
		super(dispatcher, new VillagerResemblingModel<>(0.0F), 0.5F);
		this.addFeature(new WorkerOverlayFeatureRenderer<>(this, entity -> skin(entity).hatTexture()));
	} */
	//?}

	@Override
	public Identifier getTexture(FarmerEntity entity) {
		return skin(entity).texture();
	}

	private static WorkerSkin skin(FarmerEntity entity) {
		return WorkerSkin.get(WorkerSkin.FARMER, entity.getSkin());
	}

	/** Hands the body to Minecraft Comes Alive, exactly as {@link RancherEntityRenderer} does. */
	@Override
	public void render(FarmerEntity entity, float yaw, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light) {
		if (WorkerLook.renderAsMcaVillager(entity, entity.getDisguise(), yaw, tickDelta, matrices,
				vertexConsumers, light)) {
			if (hasLabel(entity)) {
				renderLabelIfPresent(entity, entity.getDisplayName(), matrices, vertexConsumers, light
						//? if >=1.21 {
						/* , tickDelta */
						//?}
				);
			}

			return;
		}

		super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
	}

	@Override
	protected void scale(FarmerEntity entity, MatrixStack matrices, float amount) {
		matrices.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
	}

	/** Buries a farmer that is digging its way in, exactly as {@link RancherEntityRenderer} does. */
	@Override
	public Vec3d getPositionOffset(FarmerEntity entity, float tickDelta) {
		double sink = entity.getEntrance().sink(tickDelta);
		return sink <= 0.0 ? super.getPositionOffset(entity, tickDelta) : new Vec3d(0.0, -sink, 0.0);
	}

	/** A name tag on a farmer still underground is a label lying face up on the floor. */
	@Override
	protected boolean hasLabel(FarmerEntity entity) {
		return !entity.getEntrance().isBuried() && super.hasLabel(entity);
	}
}
