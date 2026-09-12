package dev.keyboard.workstations.client.fabric;

import dev.keyboard.workstations.client.BuiltinItemRenderers;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.item.Item;

public final class BuiltinItemRenderersImpl {
	public static void registerPlatform(Item item, BuiltinItemRenderers.DynamicItemRenderer renderer) {
		BuiltinItemRendererRegistry.INSTANCE.register(item, renderer::render);
	}
}
