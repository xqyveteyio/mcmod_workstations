package dev.keyboard.breederscarecrow.client;

import dev.keyboard.breederscarecrow.BreederScarecrowMod;
import dev.keyboard.breederscarecrow.entity.RancherEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.VillagerResemblingModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * Draws the rancher on the vanilla villager model, wearing the mod's own skin. The model layer is
 * the one vanilla already bakes for its own villagers, so nothing extra is registered; swapping in
 * a custom model later means changing only this class.
 *
 * <p>No clothing feature renderer is attached, unlike vanilla's villagers, so the single texture
 * below is the whole appearance. That leaves the model's hat, hat rim and jacket boxes blank and
 * therefore invisible, since entities are drawn with alpha cutout: three spare layers the skin can
 * claim just by painting them.
 */
public class RancherEntityRenderer extends MobEntityRenderer<RancherEntity, VillagerResemblingModel<RancherEntity>> {
	/** 64x64, laid out for the villager model rather than for a player skin. */
	private static final Identifier TEXTURE = BreederScarecrowMod.id("textures/entity/rancher.png");
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
