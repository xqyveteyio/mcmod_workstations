package dev.keyboard.workstations.client;

import dev.keyboard.workstations.WorkstationsMod;
//? if <26.1 {
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
//?}
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
//? if >=1.19.4 {
import net.minecraft.client.render.model.json.ModelTransformationMode;
//?} else {
/* import net.minecraft.client.render.model.json.ModelTransformation; */
//?}
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * The station as an item, drawn exactly as the placed block is: the table with its pen full.
 *
 * <p>The item model is {@code builtin/entity}, meaning it carries no geometry of its own and this
 * class draws the table too, the way vanilla renders chests and shulker boxes.
 */
public class StationItemRenderer
		//? if <26.1 {
		implements BuiltinItemRendererRegistry.DynamicItemRenderer {
		//?} else {
		/* { */
		//?}
	//? if <26.1 {
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
		client.getBlockRenderManager().renderBlockAsEntity(WorkstationsMod.RANCH_BLOCK.getDefaultState(),
				matrices, vertexConsumers, light, overlay);

		World world = client.world;

		// No world means no animals to borrow, so the item is a bare table until you are in one.
		if (world != null) {
			//? if >=1.21 {
			/* TabletopDisplay.render(world, client.getRenderTickCounter().getTickDelta(false), matrices, vertexConsumers, light); */
			//?} else {
			TabletopDisplay.render(world, client.getTickDelta(), matrices, vertexConsumers, light);
			//?}
		}
	}
	//?}
}
