package dev.keyboard.workstations.forge;

import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.fml.DistExecutor;

import java.util.function.Consumer;

public final class ModItemsImpl {
	public static BlockItem renderedBlockItem(Block block, Item.Settings settings) {
		return new BlockItem(block, settings) {
			@Override
			public void initializeClient(Consumer<IClientItemExtensions> consumer) {
				DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> StationItemExtensions.register(consumer));
			}
		};
	}
}
