package dev.keyboard.workstations.work;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What the farmer knows about seeds and soil, kept apart from the AI so "is this a plot, and is
 * what is growing on it ready" can be reasoned about on its own.
 *
 * <p>Only {@link CropBlock} counts as a crop. That covers wheat, carrots, potatoes, beetroot and
 * torchflowers: everything with an age that ripens in place, which is the whole of what harvesting
 * a square of farmland means. Melon and pumpkin stems are deliberately left out of that, because
 * their fruit grows on a neighbouring block rather than on the plot.
 *
 * <p>That fruit, and mushrooms, are picked up separately: they are found by looking around the
 * work area rather than by consulting the plot register, which is what {@link Pickings} is for.
 */
public final class Crops {
	/** Ground a hoe turns into farmland, so a trampled plot can be put back to work. */
	private static final Set<Block> TILLABLE = Set.of(
			Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.DIRT_PATH, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT);

	/** The small mushrooms, which spread where they like rather than growing on a plot. */
	private static final Set<Block> MUSHROOMS = Set.of(Blocks.BROWN_MUSHROOM, Blocks.RED_MUSHROOM);

	/** Each fruit alongside the stem that would still be holding onto it if it had grown there. */
	private static final Map<Block, Block> GOURD_STEMS = Map.of(
			Blocks.MELON, Blocks.ATTACHED_MELON_STEM,
			Blocks.PUMPKIN, Blocks.ATTACHED_PUMPKIN_STEM);

	private Crops() {
	}

	/** Whether a stack is a seed the farmer could put in the ground. */
	public static boolean isSeed(ItemStack stack) {
		return !stack.isEmpty() && cropFor(stack.getItem()) != null;
	}

	/**
	 * Whether a stack is at once the seed and the crop, as a carrot and a potato are.
	 *
	 * <p>Most crops keep the two apart: wheat and beetroot are sown from seeds that cannot be
	 * eaten, so what to sow and what to keep is never in question. Carrots and potatoes are sown
	 * from themselves, and a field of them yields one item that is both the next sowing and the
	 * whole of the harvest, which is why where to put them takes deciding.
	 *
	 * <p>Asked of the item rather than named outright so a modded crop sown from something edible
	 * is treated the same way.
	 */
	public static boolean isEdibleSeed(ItemStack stack) {
		return isSeed(stack) && stack.has(DataComponents.FOOD);
	}

	/** The crop a seed grows into, or null when the item is not a seed at all. */
	@Nullable
	public static CropBlock cropFor(Item seed) {
		return seed instanceof BlockItem item && item.getBlock() instanceof CropBlock crop ? crop : null;
	}

	/**
	 * The seeds a station is offering, without repeats and in the order its stores were given.
	 * This is the whole of what the farmer is allowed to plant, whether or not it is set to spend
	 * them.
	 *
	 * <p>Read across every store rather than only the first with anything in it, so a seed box
	 * holding nothing but wheat does not quietly retire the carrots left on the station's own
	 * shelves. Which store a seed is then taken out of is a separate question, settled where it is
	 * spent.
	 */
	public static List<Item> palette(List<Container> stores) {
		Set<Item> seeds = new LinkedHashSet<>();

		for (Container store : stores) {
			for (int slot = 0; slot < store.getContainerSize(); slot++) {
				ItemStack stack = store.getItem(slot);

				if (isSeed(stack)) {
					seeds.add(stack.getItem());
				}
			}
		}

		return new ArrayList<>(seeds);
	}

	public static boolean isFarmland(BlockState state) {
		return state.is(Blocks.FARMLAND);
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
	public static boolean isClearAbove(BlockGetter world, BlockPos plot) {
		return world.getBlockState(plot.above()).isAir();
	}

	/** Whatever is growing on a plot, or null when nothing is. */
	@Nullable
	public static CropBlock growingOn(BlockGetter world, BlockPos plot) {
		return world.getBlockState(plot.above()).getBlock() instanceof CropBlock crop ? crop : null;
	}

	/** Whether what is growing on a plot has finished and is worth breaking. */
	public static boolean isRipe(BlockGetter world, BlockPos plot) {
		BlockPos above = plot.above();
		BlockState state = world.getBlockState(above);
		return state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
	}

	public static boolean isMushroom(BlockState state) {
		return MUSHROOMS.contains(state.getBlock());
	}

	/** Whether a block is a melon or a pumpkin, however it came to be there. */
	public static boolean isGourd(BlockState state) {
		return GOURD_STEMS.containsKey(state.getBlock());
	}

	/**
	 * Whether a melon or pumpkin grew where it stands, told by a stem beside it still holding on.
	 *
	 * <p>Worth asking, because a pumpkin is a building block every bit as much as it is produce.
	 * Taking every one inside the work area would have the farmer quietly dismantle a wall or a
	 * lantern somebody put up, and an attached stem is the one thing that says this one was grown.
	 */
	public static boolean isGrownGourd(BlockGetter world, BlockPos pos) {
		Block stem = GOURD_STEMS.get(world.getBlockState(pos).getBlock());

		if (stem == null) {
			return false;
		}

		for (Direction side : Direction.Plane.HORIZONTAL) {
			BlockState neighbour = world.getBlockState(pos.relative(side));

			// Pointing back at this fruit. A stem facing elsewhere grew the one next door, and
			// taking its neighbour on the strength of it would be reaching.
			if (neighbour.is(stem) && neighbour.getValue(AttachedStemBlock.FACING) == side.getOpposite()) {
				return true;
			}
		}

		return false;
	}

	/** Whether a block is one of the loose pickings a station has been set to take. */
	public static boolean isPickable(BlockGetter world, BlockPos pos, boolean gourds, boolean mushrooms) {
		return (mushrooms && isMushroom(world.getBlockState(pos))) || (gourds && isGrownGourd(world, pos));
	}
}
