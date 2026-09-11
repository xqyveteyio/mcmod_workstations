package dev.keyboard.workstations.fabric;

import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;

public final class ModItemsImpl {
	public static BlockItem renderedBlockItem(Block block, Item.Settings settings) {
		return new BlockItem(block, settings);
	}
}
