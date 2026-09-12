package dev.keyboard.workstations.neoforge;

import dev.keyboard.workstations.client.BuiltinItemRenderers;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.BuiltinModelItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

final class StationItemExtensions implements IClientItemExtensions {
	static final StationItemExtensions INSTANCE = new StationItemExtensions();

	private BuiltinModelItemRenderer renderer;

	@Override
	public BuiltinModelItemRenderer getCustomRenderer() {
		if (renderer == null) {
			MinecraftClient client = MinecraftClient.getInstance();
			renderer = new BuiltinModelItemRenderer(client.getBlockEntityRenderDispatcher(),
					client.getEntityModelLoader()) {
				@Override
				public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices,
						VertexConsumerProvider vertexConsumers, int light, int overlay) {
					BuiltinItemRenderers.DynamicItemRenderer custom = BuiltinItemRenderers.get(stack.getItem());

					if (custom != null) {
						custom.render(stack, mode, matrices, vertexConsumers, light, overlay);
					}
				}
			};
		}

		return renderer;
	}
}
