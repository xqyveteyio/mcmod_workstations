package dev.keyboard.breederscarecrow.client;

import dev.keyboard.breederscarecrow.BreederScarecrowMod;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * The station as an item: the same table, with the pen on top full. A station in your hand has no
 * herd to report on, so it shows one of every kind, as a picture of what it is for.
 *
 * <p>The item model is {@code builtin/entity}, meaning it carries no geometry of its own and this
 * class draws the table too, the way vanilla renders chests and shulker boxes.
 */
public class StationItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
	@Override
	public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, int overlay) {
		MinecraftClient client = MinecraftClient.getInstance();
		client.getBlockRenderManager().renderBlockAsEntity(BreederScarecrowMod.SCARECROW_BLOCK.getDefaultState(),
				matrices, vertexConsumers, light, overlay);

		World world = client.world;

		// No world means no animals to borrow, so the item is a bare table until you are in one.
		if (world != null) {
			TabletopDisplay.render(world, TabletopDisplay.EVERY_KIND, client.getTickDelta(), matrices,
					vertexConsumers, light);
		}
	}
}
