package dev.keyboard.breederscarecrow.client;

import dev.keyboard.breederscarecrow.entity.RancherEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.VillagerResemblingModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * Borrows the vanilla villager model and texture as a placeholder skin for the rancher. The model
 * layer is the one vanilla already bakes for its own villagers, so nothing extra is registered;
 * swapping in a custom model later means changing only this class.
 */
public class RancherEntityRenderer extends MobEntityRenderer<RancherEntity, VillagerResemblingModel<RancherEntity>> {
	private static final Identifier TEXTURE = new Identifier("textures/entity/villager/villager.png");
	/** The same shrink vanilla applies, without which the villager model looks oversized. */
	private static final float MODEL_SCALE = 0.9375F;

	public RancherEntityRenderer(EntityRendererFactory.Context context) {
		super(context, new VillagerResemblingModel<>(context.getPart(EntityModelLayers.VILLAGER)), 0.5F);
	}

	@Override
	public Identifier getTexture(RancherEntity entity) {
		return TEXTURE;
	}

	@Override
	protected void scale(RancherEntity entity, MatrixStack matrices, float amount) {
		matrices.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
	}
}
