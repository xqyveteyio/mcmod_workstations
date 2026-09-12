package dev.keyboard.workstations.forge;

import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

public final class ModItemsImpl {
	public static BlockItem renderedBlockItem(Block block, Item.Settings settings) {
		DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> StationIster.attach(settings));
		return new BlockItem(block, settings);
	}
}
