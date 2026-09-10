package dev.keyboard.workstations.work;

import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.List;

/**
 * Moving items into and out of the inventories a station works with.
 *
 * <p>Every worker that carries a pack ends up doing the same two things with it: taking one item
 * out of the first store that holds it, and pouring a stack into the first destination that has
 * room. Written once here so a farm and a lumber station cannot disagree about whether a box is
 * emptied before the station's own shelves, or about how two half stacks combine.
 */
public final class Stock {
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

			if (!ItemStack.canCombine(existing, stack)) {
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
