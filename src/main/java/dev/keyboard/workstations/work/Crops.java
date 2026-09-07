package dev.keyboard.workstations.work;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What the farmer knows about seeds and soil, kept apart from the AI so "is this a plot, and is
 * what is growing on it ready" can be reasoned about on its own.
 *
 * <p>Only {@link CropBlock} counts as a crop. That covers wheat, carrots, potatoes, beetroot and
 * torchflowers: everything with an age that ripens in place, which is the whole of what harvesting
 * a square of farmland means. Melon and pumpkin stems are deliberately left out, because their
 * fruit grows on a neighbouring block and managing them is a different job from managing a plot.
 */
public final class Crops {
	/** Ground a hoe turns into farmland, so a trampled plot can be put back to work. */
	private static final Set<Block> TILLABLE = Set.of(
			Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.DIRT_PATH, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT);

	private Crops() {
	}

	/** Whether a stack is a seed the farmer could put in the ground. */
	public static boolean isSeed(ItemStack stack) {
		return !stack.isEmpty() && cropFor(stack.getItem()) != null;
	}

	/** The crop a seed grows into, or null when the item is not a seed at all. */
	@Nullable
	public static CropBlock cropFor(Item seed) {
		return seed instanceof BlockItem item && item.getBlock() instanceof CropBlock crop ? crop : null;
	}

	/**
	 * The seeds a station is offering, in container order and without repeats. This is the whole of
	 * what the farmer is allowed to plant, whether or not it is set to spend them.
	 */
	public static List<Item> palette(Inventory station) {
		Set<Item> seeds = new LinkedHashSet<>();

		for (int slot = 0; slot < station.size(); slot++) {
			ItemStack stack = station.getStack(slot);

			if (isSeed(stack)) {
				seeds.add(stack.getItem());
			}
		}

		return new ArrayList<>(seeds);
	}

	public static boolean isFarmland(BlockState state) {
		return state.isOf(Blocks.FARMLAND);
	}

	public static boolean isTillable(BlockState state) {
		return TILLABLE.contains(state.getBlock());
	}

	/**
	 * Whether a position is still the farmer's to look after: farmland, or ground that could be
	 * hoed back into farmland. Anything else means the plot was dug up or built over, and a plot
	 * like that is dropped from the register rather than fought over.
	 */
	public static boolean isPlot(BlockState state) {
		return isFarmland(state) || isTillable(state);
	}

	/** Whether there is room above a plot to plant in, or to turn the plot into farmland at all. */
	public static boolean isClearAbove(BlockView world, BlockPos plot) {
		return world.getBlockState(plot.up()).isAir();
	}

	/** Whatever is growing on a plot, or null when nothing is. */
	@Nullable
	public static CropBlock growingOn(BlockView world, BlockPos plot) {
		return world.getBlockState(plot.up()).getBlock() instanceof CropBlock crop ? crop : null;
	}

	/** Whether what is growing on a plot has finished and is worth breaking. */
	public static boolean isRipe(BlockView world, BlockPos plot) {
		BlockPos above = plot.up();
		BlockState state = world.getBlockState(above);
		return state.getBlock() instanceof CropBlock crop && crop.isMature(state);
	}
}
