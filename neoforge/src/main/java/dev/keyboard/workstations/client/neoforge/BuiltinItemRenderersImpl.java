package dev.keyboard.workstations.client.neoforge;

import dev.keyboard.workstations.client.BuiltinItemRenderers;
import net.minecraft.item.Item;

public final class BuiltinItemRenderersImpl {
	public static void registerPlatform(Item item, BuiltinItemRenderers.DynamicItemRenderer renderer) {
		// NeoForge hangs the renderer on the item through RegisterClientExtensionsEvent.
	}
}
