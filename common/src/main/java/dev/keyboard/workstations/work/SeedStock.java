package dev.keyboard.workstations.work;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

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

	public void writeNbt(CompoundTag nbt) {
		ListTag tally = new ListTag();

		for (Map.Entry<Item, Integer> entry : planted.entrySet()) {
			CompoundTag row = new CompoundTag();
			row.putString(SEED_KEY, BuiltInRegistries.ITEM.getKey(entry.getKey()).toString());
			row.putInt(COUNT_KEY, entry.getValue());
			tally.add(row);
		}

		nbt.put(PLANTED_KEY, tally);
	}

	public void save(ValueOutput output) {
		var tally = output.list(PLANTED_KEY, CompoundTag.CODEC);

		for (Map.Entry<Item, Integer> entry : planted.entrySet()) {
			CompoundTag row = new CompoundTag();
			row.putString(SEED_KEY, BuiltInRegistries.ITEM.getKey(entry.getKey()).toString());
			row.putInt(COUNT_KEY, entry.getValue());
			tally.add(row);
		}
	}

	public void load(ValueInput input) {
		planted.clear();

		for (CompoundTag row : input.listOrEmpty(PLANTED_KEY, CompoundTag.CODEC)) {
			Identifier id = Identifier.tryParse(row.getStringOr(SEED_KEY, ""));

			if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
				planted.put(BuiltInRegistries.ITEM.getValue(id), row.getIntOr(COUNT_KEY, 0));
			}
		}
	}

	public void readNbt(CompoundTag nbt) {
		planted.clear();
		ListTag tally = nbt.getListOrEmpty(PLANTED_KEY);

		for (int index = 0; index < tally.size(); index++) {
			CompoundTag row = tally.getCompoundOrEmpty(index);
			Identifier id = Identifier.tryParse(row.getStringOr(SEED_KEY, ""));

			if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
				planted.put(BuiltInRegistries.ITEM.getValue(id), row.getIntOr(COUNT_KEY, 0));
			}
		}
	}
}
