package dev.keyboard.workstations.work;

import dev.keyboard.workstations.Mc;

import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * Moving items into and out of the inventories a station works with.
 *
 * <p>Every worker that carries a pack ends up doing the same two things with it: taking one item
 * out of the first store that holds it, and pouring a stack into the first destination that has
 * room. Written once here so a farm and a lumber station cannot disagree about whether a box is
 * emptied before the station's own shelves, or about how two half stacks combine.
 *
 * <p>{@link #stow} is the other half of that: which of a station's containers a given item belongs
 * in. It is one rule for all three kinds of worker, so a seed picked up by a rancher ends up in
 * the same place as one picked up by a farmer.
 */
public final class Stock {
	/**
	 * How much of a seed that is also food the seed boxes are kept stocked with before the rest of
	 * it counts as produce. A stack, which no field gets through between two harvests, since a plot
	 * takes one to sow and gives several back.
	 */
	public static final int SEED_RESERVE = 64;

	private Stock() {
	}

	/**
	 * Takes one of {@code item} out of the first store holding it, or reports that there were none
	 * left after all.
	 *
	 * <p>The stores come in the order they are to be drawn down, so a seed box beside the station
	 * is emptied of a kind before the station's own copies of it are touched.
	 */
	public static boolean spend(List<Inventory> stores, Item item) {
		for (Inventory store : stores) {
			for (int slot = 0; slot < store.size(); slot++) {
				ItemStack stack = store.getStack(slot);

				if (!stack.isEmpty() && stack.getItem() == item) {
					store.removeStack(slot, 1);
					store.markDirty();
					return true;
				}
			}
		}

		return false;
	}

	/** Whether any of the stores is holding at least one of {@code item}. */
	public static boolean holds(List<Inventory> stores, Item item) {
		return count(stores, item) > 0;
	}

	/**
	 * The first slot across these stores holding something {@code wanted} accepts, or null when
	 * none of them does. Searched in the order the stores were given, so the store meant to be
	 * drawn down first is.
	 */
	@Nullable
	public static Held find(List<Inventory> stores, Predicate<ItemStack> wanted) {
		for (Inventory store : stores) {
			for (int slot = 0; slot < store.size(); slot++) {
				if (wanted.test(store.getStack(slot))) {
					return new Held(store, slot);
				}
			}
		}

		return null;
	}

	/** How many items across these stores {@code wanted} accepts. */
	public static int count(List<Inventory> stores, Predicate<ItemStack> wanted) {
		int total = 0;

		for (Inventory store : stores) {
			for (int slot = 0; slot < store.size(); slot++) {
				ItemStack stack = store.getStack(slot);

				if (wanted.test(stack)) {
					total += stack.getCount();
				}
			}
		}

		return total;
	}

	/**
	 * One slot of one store, which is what a caller has to be handed when the thing it was looking
	 * for could have been in any of several stores. A slot number on its own would be meaningless
	 * without the store it counts from.
	 */
	public record Held(Inventory store, int slot) {
		public ItemStack stack() {
			return store.getStack(slot);
		}

		public ItemStack take(int amount) {
			ItemStack taken = store.removeStack(slot, amount);

			if (!taken.isEmpty()) {
				store.markDirty();
			}

			return taken;
		}
	}

	/** How many of {@code item} the stores hold between them. */
	public static int count(List<Inventory> stores, Item item) {
		int total = 0;

		for (Inventory store : stores) {
			for (int slot = 0; slot < store.size(); slot++) {
				ItemStack stack = store.getStack(slot);

				if (!stack.isEmpty() && stack.getItem() == item) {
					total += stack.getCount();
				}
			}
		}

		return total;
	}

	/**
	 * Puts a stack away where it belongs and hands back whatever would not fit: seed and saplings
	 * into the seed boxes, everything else onto the station's own shelves.
	 *
	 * <p>One rule for every worker, which is the point of it living here rather than in each brain.
	 * A field's returns are mostly seed and a wood's are mostly saplings, and left in with the
	 * produce they fill a station up with the one thing that was going straight back into the
	 * ground. A rancher that sweeps up seed from the grass it trampled has no more use for it on
	 * its own shelves than a farmer does.
	 *
	 * <p>Seed falls back on the station when the boxes are full, because a box that has run out of
	 * room should not stop the harvest coming in. Produce does not fall the other way, or a station
	 * left unemptied would end up filling the seed boxes with wheat and undo the separation.
	 *
	 * <p>A seed that is also food, meaning a carrot or a potato, is only seed up to a point. The
	 * boxes are kept topped up to {@link #SEED_RESERVE} of it, which is far more than a field
	 * consumes between harvests, and everything past that is treated as the produce it also is and
	 * shelved with the rest. Sending all of it to the boxes instead would leave the harvest
	 * somewhere other than where the harvest is collected, and would in time pack the boxes with
	 * food that has nothing to do with sowing.
	 */
	public static ItemStack stow(Inventory station, List<Inventory> boxes, ItemStack stack) {
		if (belongsInBox(stack)) {
			int offered = Crops.isEdibleSeed(stack)
					? Math.min(stack.getCount(), boxRoomFor(boxes, stack))
					: stack.getCount();

			if (offered > 0) {
				// Split rather than handed over whole, so the part held back is never at the mercy
				// of how much room the boxes happen to have.
				ItemStack refused = fill(boxes, stack.split(offered));

				// Whatever the boxes would not take rejoins the part held back, and goes with it
				// to the station below.
				if (!refused.isEmpty()) {
					if (stack.isEmpty()) {
						stack = refused;
					} else {
						stack.increment(refused.getCount());
					}
				}
			}
		}

		return fill(List.of(station), stack);
	}

	/** Whether a seed box is the place for this: seed to sow, or a sapling to put in the ground. */
	public static boolean belongsInBox(ItemStack stack) {
		return Crops.isSeed(stack) || Woods.isSapling(stack);
	}

	/** How much more of a seed that is also food the boxes should be holding. */
	private static int boxRoomFor(List<Inventory> boxes, ItemStack stack) {
		int held = 0;

		for (Inventory box : boxes) {
			for (int slot = 0; slot < box.size(); slot++) {
				ItemStack existing = box.getStack(slot);

				if (Mc.stacksMatch(existing, stack)) {
					held += existing.getCount();
				}
			}
		}

		return Math.max(0, SEED_RESERVE - held);
	}

	/** Works down the destinations in order, returning whatever none of them had room for. */
	public static ItemStack fill(List<Inventory> destinations, ItemStack stack) {
		for (Inventory destination : destinations) {
			if (stack.isEmpty()) {
				break;
			}

			int before = stack.getCount();
			stack = insert(destination, stack);

			if (stack.getCount() != before) {
				destination.markDirty();
			}
		}

		return stack;
	}

	/** Moves what fits into {@code target}, mutating and returning the leftover. */
	public static ItemStack insert(Inventory target, ItemStack stack) {
		for (int slot = 0; slot < target.size() && !stack.isEmpty(); slot++) {
			ItemStack existing = target.getStack(slot);

			if (existing.isEmpty()) {
				target.setStack(slot, stack.copy());
				stack.setCount(0);
				break;
			}

			if (!Mc.stacksMatch(existing, stack)) {
				continue;
			}

			int room = Math.min(existing.getMaxCount(), target.getMaxCountPerStack()) - existing.getCount();
			int moved = Math.min(room, stack.getCount());

			if (moved > 0) {
				existing.increment(moved);
				stack.decrement(moved);
			}
		}

		return stack;
	}
}
