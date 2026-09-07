package dev.keyboard.workstations.client;

import dev.keyboard.workstations.WorkstationsMod;
import dev.keyboard.workstations.entity.FarmerEntity;
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
 */
public class FarmerEntityRenderer extends MobEntityRenderer<FarmerEntity, VillagerResemblingModel<FarmerEntity>> {
	/** 64x64, laid out for the villager model rather than for a player skin. */
	private static final Identifier TEXTURE = WorkstationsMod.id("textures/entity/farmer.png");
	/** Same layout again, but transparent everywhere except the hat and hat rim boxes. */
	private static final Identifier HAT_TEXTURE = WorkstationsMod.id("textures/entity/farmer_hat.png");
	/** The same shrink vanilla applies, without which the villager model looks oversized. */
	private static final float MODEL_SCALE = 0.9375F;

	public FarmerEntityRenderer(EntityRendererFactory.Context context) {
		super(context, new VillagerResemblingModel<>(context.getPart(EntityModelLayers.VILLAGER)), 0.5F);
		this.addFeature(new WorkerOverlayFeatureRenderer<>(this, HAT_TEXTURE));
	}

	@Override
	public Identifier getTexture(FarmerEntity entity) {
		return TEXTURE;
	}

	@Override
	protected void scale(FarmerEntity entity, MatrixStack matrices, float amount) {
		matrices.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
	}
}
