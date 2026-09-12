package dev.keyboard.workstations.forge;

import dev.keyboard.workstations.client.BuiltinItemRenderers;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.BuiltinModelItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

final class StationIster extends BuiltinModelItemRenderer {
	static final StationIster INSTANCE = new StationIster();

	static void attach(Item.Settings settings) {
		settings.setISTER(() -> () -> INSTANCE);
	}

	@Override
	public void render(ItemStack stack, ModelTransformation.Mode mode, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, int overlay) {
		BuiltinItemRenderers.DynamicItemRenderer custom = BuiltinItemRenderers.get(stack.getItem());

		if (custom != null) {
			custom.render(stack, mode, matrices, vertexConsumers, light, overlay);
		}
	}
}
