package dev.keyboard.workstations.client.forge;

import dev.keyboard.workstations.client.BuiltinItemRenderers;
import net.minecraft.item.Item;

public final class BuiltinItemRenderersImpl {
	public static void registerPlatform(Item item, BuiltinItemRenderers.DynamicItemRenderer renderer) {
		// Forge hangs the renderer on the BlockItem created by ModItemsImpl.
	}
}
