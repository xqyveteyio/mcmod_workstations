package dev.keyboard.workstations.forge;

import dev.keyboard.workstations.client.BuiltinItemRenderers;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.BuiltinModelItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

final class StationItemExtensions implements IClientItemExtensions {
	private static BuiltinModelItemRenderer renderer;

	static void register(Consumer<IClientItemExtensions> consumer) {
		consumer.accept(new StationItemExtensions());
	}

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
