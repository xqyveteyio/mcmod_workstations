package dev.keyboard.workstations.client;

import dev.keyboard.workstations.entity.FarmerEntity;
import dev.keyboard.workstations.work.WorkerSkin;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.VillagerResemblingModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

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

	public FarmerEntityRenderer(EntityRendererFactory.Context context) {
		super(context, new VillagerResemblingModel<>(context.getPart(EntityModelLayers.VILLAGER)), 0.5F);
		this.addFeature(new WorkerOverlayFeatureRenderer<>(this, entity -> skin(entity).hatTexture()));
	}

	@Override
	public Identifier getTexture(FarmerEntity entity) {
		return skin(entity).texture();
	}

	private static WorkerSkin skin(FarmerEntity entity) {
		return WorkerSkin.get(WorkerSkin.FARMER, entity.getSkin());
	}

	@Override
	protected void scale(FarmerEntity entity, MatrixStack matrices, float amount) {
		matrices.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
	}
}
