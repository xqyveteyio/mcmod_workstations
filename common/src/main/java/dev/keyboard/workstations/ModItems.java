package dev.keyboard.workstations;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;

public final class ModItems {
	private ModItems() {
	}

	/**
	 * A block item whose model is {@code builtin/entity}, so the loader has to be told how to draw
	 * it. Fabric registers a renderer later; Forge needs a BlockItem that can carry one.
	 */
	@ExpectPlatform
	public static BlockItem renderedBlockItem(Block block, Item.Settings settings) {
		throw new AssertionError();
	}
}
