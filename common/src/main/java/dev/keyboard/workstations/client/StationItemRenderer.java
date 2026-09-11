package dev.keyboard.workstations.client;

import dev.keyboard.workstations.WorkstationsMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * The station as an item, drawn exactly as the placed block is: the table with its pen full.
 *
 * <p>The item model is {@code builtin/entity}, meaning it carries no geometry of its own and this
 * class draws the table too, the way vanilla renders chests and shulker boxes.
 */
public class StationItemRenderer implements BuiltinItemRenderers.DynamicItemRenderer {
	@Override
	public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, int overlay) {
		MinecraftClient client = MinecraftClient.getInstance();
		client.getBlockRenderManager().renderBlockAsEntity(WorkstationsMod.RANCH_BLOCK.get().getDefaultState(),
				matrices, vertexConsumers, light, overlay);

		World world = client.world;

		// No world means no animals to borrow, so the item is a bare table until you are in one.
		if (world != null) {
			TabletopDisplay.render(world, client.getTickDelta(), matrices, vertexConsumers, light);
		}
	}
}
