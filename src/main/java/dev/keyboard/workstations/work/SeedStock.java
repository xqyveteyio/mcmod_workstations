package dev.keyboard.workstations.work;

import dev.keyboard.workstations.block.SeedBoxBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The seed a planting station works from: boxes standing in its area, its own shelves as overflow,
 * and the tally of what has gone in the ground so the mix can keep the ratios.
 *
 * <p>Shared by the farm and the lumber station, which both sow from the same kind of store and
 * both divide what they plant by the same weights. Without this they would each keep their own
 * copy of the box scan and the tally, and a change to how a box is found would have to be made
 * twice or the two stations would quietly disagree about where seed lives.
 */
public final class SeedStock {
	private static final String PLANTED_KEY = "Planted";
	private static final String SEED_KEY = "Seed";
	private static final String COUNT_KEY = "Count";

	private final AreaContainers<SeedBoxBlockEntity> boxes = new AreaContainers<>(SeedBoxBlockEntity.class);
	private final Map<Item, Integer> planted = new HashMap<>();

	/**
	 * The seed boxes anywhere in the area, nearest first.
	 *
	 * <p>Separate from {@link #stores} because the boxes are where seed is meant to end up and the
	 * station is only what catches the overflow, a distinction that matters when deciding how much
	 * of something belongs in a box in the first place.
	 */
	public List<Inventory> boxes(@Nullable World world, WorkArea area) {
		return List.copyOf(boxes.in(world, area));
	}

	/**
	 * Everywhere this station's seed might be, the place to reach for first listed first.
	 *
	 * <p>Seed boxes come before the station's own shelves: seed is taken out of a box while one
	 * holds any, which is what keeps the station's own space clear for the produce coming the other
	 * way. The station is last rather than absent so seed left on its shelves by hand is still
	 * sown, and with no box in the area the station is the only store there is and everything works
	 * as it did before boxes existed.
	 */
	public List<Inventory> stores(Inventory station, @Nullable World world, WorkArea area) {
		List<Inventory> stores = new ArrayList<>(boxes(world, area));
		stores.add(station);
		return stores;
	}

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
			row.putString(SEED_KEY, Registries.ITEM.getId(entry.getKey()).toString());
			row.putInt(COUNT_KEY, entry.getValue());
			tally.add(row);
		}

		nbt.put(PLANTED_KEY, tally);
	}

	public void readNbt(NbtCompound nbt) {
		planted.clear();
		NbtList tally = nbt.getList(PLANTED_KEY, NbtElement.COMPOUND_TYPE);

		for (int index = 0; index < tally.size(); index++) {
			NbtCompound row = tally.getCompound(index);
			Identifier id = Identifier.tryParse(row.getString(SEED_KEY));

			if (id != null && Registries.ITEM.containsId(id)) {
				planted.put(Registries.ITEM.get(id), row.getInt(COUNT_KEY));
			}
		}
	}
}
