package dev.keyboard.workstations.work;

import dev.keyboard.workstations.Mc;

import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How a farm's plots are divided between the seeds it has been given: one weight per kind of seed,
 * and the rule that turns those weights into what goes in the next hole.
 *
 * <p>Weights rather than percentages, because the seeds on offer are whatever is in the station's
 * container and that changes as you add and remove them. A weight is a share of however many kinds
 * are actually available, so pulling all the carrots out silently re-divides the field between
 * what is left instead of leaving a tenth of it unplanted.
 *
 * <p>Weights are kept for seeds that are not currently in the container as well, so emptying the
 * station and refilling it does not lose a mix you spent time dialling in.
 */
public final class SeedMix {
	public static final int MIN_WEIGHT = 0;
	public static final int MAX_WEIGHT = 100;
	/**
	 * What a seed the farm has not seen before is given. Full weight, so dropping a new kind into
	 * the container splits the field evenly with everything else rather than doing nothing until
	 * the ratios are visited.
	 */
	public static final int DEFAULT_WEIGHT = MAX_WEIGHT;

	private static final String WEIGHTS_KEY = "Weights";
	private static final String SEED_KEY = "Seed";
	private static final String WEIGHT_KEY = "Weight";

	/** Insertion ordered, so the screen lists seeds in the order the farm met them. */
	private final Map<Item, Integer> weights = new LinkedHashMap<>();

	public int weight(Item seed) {
		return weights.getOrDefault(seed, DEFAULT_WEIGHT);
	}

	public void setWeight(Item seed, int weight) {
		weights.put(seed, MathHelper.clamp(weight, MIN_WEIGHT, MAX_WEIGHT));
	}

	/** Seeds this mix has an opinion about, whether or not any are in the container right now. */
	public List<Item> known() {
		return List.copyOf(weights.keySet());
	}

	public void copyFrom(SeedMix other) {
		weights.clear();
		weights.putAll(other.weights);
	}

	/** Whether two mixes would plant the same field the same way. */
	public boolean matches(SeedMix other) {
		return weights.equals(other.weights);
	}

	/**
	 * The share of the field each seed in {@code palette} should get, as a percentage rounded for
	 * display. Only for the screen: the planting rule works off the raw weights.
	 */
	public int share(Item seed, List<Item> palette) {
		int total = 0;

		for (Item candidate : palette) {
			total += weight(candidate);
		}

		return total == 0 ? 0 : Math.round(weight(seed) * 100.0F / total);
	}

	/**
	 * Which seed to plant next, given what is available and what has already gone in the ground.
	 *
	 * <p>Picks whichever seed is furthest behind the share its weight asks for, which spreads the
	 * kinds through the field as it is planted rather than laying down all the wheat first and then
	 * all the carrots. Over a whole field the counts land on the ratios; over the first few holes
	 * it still alternates sensibly.
	 *
	 * @param palette seeds actually available to plant, which is what the container holds
	 * @param planted how many plots each seed has been given so far
	 * @return the seed to plant, or null when every available seed has been turned down to zero
	 */
	@Nullable
	public Item choose(List<Item> palette, Map<Item, Integer> planted) {
		int totalWeight = 0;

		for (Item candidate : palette) {
			totalWeight += weight(candidate);
		}

		if (totalWeight == 0) {
			return null;
		}

		int totalPlanted = 0;

		for (Item candidate : palette) {
			totalPlanted += planted.getOrDefault(candidate, 0);
		}

		Item best = null;
		double worstDeficit = Double.NEGATIVE_INFINITY;

		for (Item candidate : palette) {
			int weight = weight(candidate);

			if (weight == 0) {
				continue;
			}

			// How many plots this seed should hold once this one more is planted, less how many it
			// actually holds. The largest shortfall is the seed most overdue.
			double owed = (double) weight / totalWeight * (totalPlanted + 1);
			double deficit = owed - planted.getOrDefault(candidate, 0);

			if (deficit > worstDeficit) {
				worstDeficit = deficit;
				best = candidate;
			}
		}

		return best;
	}

	public void writeNbt(NbtCompound nbt) {
		NbtList list = new NbtList();

		for (Map.Entry<Item, Integer> entry : weights.entrySet()) {
			NbtCompound row = new NbtCompound();
			row.putString(SEED_KEY, Mc.itemId(entry.getKey()).toString());
			row.putInt(WEIGHT_KEY, entry.getValue());
			list.add(row);
		}

		nbt.put(WEIGHTS_KEY, list);
	}

	/** An absent list leaves the mix alone, so a save from before ratios existed loses nothing. */
	public void readNbt(NbtCompound nbt) {
		if (!Mc.has(nbt, WEIGHTS_KEY, Mc.NBT_LIST)) {
			return;
		}

		weights.clear();
		NbtList list = Mc.list(nbt, WEIGHTS_KEY);

		for (int index = 0; index < list.size(); index++) {
			NbtCompound row = Mc.compoundAt(list, index);
			Identifier id = Identifier.tryParse(Mc.string(row, SEED_KEY));

			// A seed from a mod that is no longer installed is dropped rather than crashing the
			// load; its weight is simply forgotten.
			if (id != null && Mc.hasItem(id)) {
				setWeight(Mc.item(id), Mc.integer(row, WEIGHT_KEY));
			}
		}
	}

	/** Rows for the screen: every seed on offer, with its weight and the share that works out to. */
	public List<Row> rows(List<Item> palette) {
		List<Row> rows = new ArrayList<>(palette.size());

		for (Item seed : palette) {
			rows.add(new Row(seed, weight(seed), share(seed, palette)));
		}

		return rows;
	}

	public record Row(Item seed, int weight, int share) {
	}
}
