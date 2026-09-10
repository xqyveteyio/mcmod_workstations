package dev.keyboard.workstations.client;

import dev.keyboard.workstations.WorkstationsMod;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;

/**
 * The lumber station as an item, drawn exactly as the placed block is: the bench with the axe
 * turning over it.
 *
 * <p>The item model is {@code builtin/entity}, meaning it carries no geometry of its own and this
 * class draws the bench too, the way vanilla renders chests and shulker boxes.
 */
public class LumberItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
	@Override
	public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, int overlay) {
		MinecraftClient client = MinecraftClient.getInstance();
		client.getBlockRenderManager().renderBlockAsEntity(WorkstationsMod.LUMBER_BLOCK.getDefaultState(),
				matrices, vertexConsumers, light, overlay);
		AxeDisplay.render(client.world, client.getTickDelta(), matrices, vertexConsumers, light, overlay);
	}
}
