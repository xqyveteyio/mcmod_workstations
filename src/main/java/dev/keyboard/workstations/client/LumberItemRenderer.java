package dev.keyboard.workstations.client;

import dev.keyboard.workstations.WorkstationsMod;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
//? if >=1.19.4 {
import net.minecraft.client.render.model.json.ModelTransformationMode;
//?} else {
/* import net.minecraft.client.render.model.json.ModelTransformation; */
//?}
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
	public void render(ItemStack stack,
			//? if >=1.19.4 {
			ModelTransformationMode mode,
			//?} else {
			/* ModelTransformation.Mode mode, */
			//?}
			MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, int overlay) {
		MinecraftClient client = MinecraftClient.getInstance();
		client.getBlockRenderManager().renderBlockAsEntity(WorkstationsMod.LUMBER_BLOCK.getDefaultState(),
				matrices, vertexConsumers, light, overlay);
		//? if >=1.21 {
		/* AxeDisplay.render(client.world, client.getRenderTickCounter().getTickDelta(false), matrices, vertexConsumers, light, overlay); */
		//?} else {
		AxeDisplay.render(client.world, client.getTickDelta(), matrices, vertexConsumers, light, overlay);
		//?}
	}
}
