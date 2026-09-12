package dev.keyboard.workstations.work;

import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * What a planting station has put in the ground, so its mix can keep to the ratios it was given.
 *
 * <p>Shared by the farm and the lumber station, which divide what they plant by the same weights.
 * Where the seed itself lives is not kept here: every station has seed boxes, ranch included, so
 * that scan belongs to
 * {@link dev.keyboard.workstations.block.WorkStationBlockEntity#seedBoxes()} and not to the two
 * stations that sow.
 */
public final class SeedStock {
	private static final String PLANTED_KEY = "Planted";
	private static final String SEED_KEY = "Seed";
	private static final String COUNT_KEY = "Count";

	private final Map<Item, Integer> planted = new HashMap<>();

	/** How many plantings each kind holds, for the mix to divide the next one out by. */
	public Map<Item, Integer> tally() {
		return Collections.unmodifiableMap(planted);
	}

	public void note(Item seed) {
		planted.merge(seed, 1, Integer::sum);
	}

	/**
	 * Forgets what has been planted where, so changing the ratios takes effect from now rather
	 * than being fought by every plot sown under the old ones.
	 *
	 * @return whether there was anything to forget, so the caller can skip marking dirty
	 */
	public boolean clear() {
		if (planted.isEmpty()) {
			return false;
		}

		planted.clear();
		return true;
	}

	public void writeNbt(NbtCompound nbt) {
		NbtList tally = new NbtList();

		for (Map.Entry<Item, Integer> entry : planted.entrySet()) {
			NbtCompound row = new NbtCompound();
			row.putString(SEED_KEY, Registry.ITEM.getId(entry.getKey()).toString());
			row.putInt(COUNT_KEY, entry.getValue());
			tally.add(row);
		}

		nbt.put(PLANTED_KEY, tally);
	}

	public void readNbt(NbtCompound nbt) {
		planted.clear();
		NbtList tally = nbt.getList(PLANTED_KEY, 10);

		for (int index = 0; index < tally.size(); index++) {
			NbtCompound row = tally.getCompound(index);
			Identifier id = Identifier.tryParse(row.getString(SEED_KEY));

			if (id != null && Registry.ITEM.containsId(id)) {
				planted.put(Registry.ITEM.get(id), row.getInt(COUNT_KEY));
			}
		}
	}
}
