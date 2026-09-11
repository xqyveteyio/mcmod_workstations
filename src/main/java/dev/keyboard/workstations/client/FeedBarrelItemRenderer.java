package dev.keyboard.workstations.client;

import dev.keyboard.workstations.WorkstationsMod;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;

/** A feed box in hand, in the inventory and on the ground, drawn as the same shut chest. */
public class FeedBarrelItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
	private final SeedBoxModel model = new SeedBoxModel(WorkstationsMod.id("textures/entity/feed_barrel.png"));

	@Override
	public void render(ItemStack stack, ModelTransformation.Mode mode, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, int overlay) {
		model.render(WorkstationsMod.FEED_BARREL_BLOCK.getDefaultState(), 0.0F,
				matrices, vertexConsumers, light, overlay);
	}
}
