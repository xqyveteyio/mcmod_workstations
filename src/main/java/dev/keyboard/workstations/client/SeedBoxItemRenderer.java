package dev.keyboard.workstations.client;

import dev.keyboard.workstations.WorkstationsMod;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The seed box in hand, in the inventory and on the ground, drawn as the same shut chest.
 *
 * <p>The item model is {@code builtin/entity}, carrying no geometry of its own, the way vanilla's
 * chest item does.
 */
public class SeedBoxItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
	/**
	 * Built on first use rather than in the constructor: the chest model is only there to borrow
	 * once resources have been loaded, which is later than client startup.
	 */
	@Nullable
	private SeedBoxModel model;

	@Override
	public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, int overlay) {
		if (model == null) {
			model = new SeedBoxModel(MinecraftClient.getInstance()
					.getEntityModelLoader().getModelPart(EntityModelLayers.CHEST));
		}

		model.render(WorkstationsMod.SEED_BOX_BLOCK.getDefaultState(), 0.0F,
				matrices, vertexConsumers, light, overlay);
	}
}
