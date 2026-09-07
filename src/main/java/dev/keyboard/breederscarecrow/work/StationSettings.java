package dev.keyboard.breederscarecrow.work;

import dev.keyboard.breederscarecrow.ModConfig;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.math.MathHelper;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.ObjIntConsumer;
import java.util.function.ToIntFunction;

/**
 * One station's own orders. Every ranch is set up separately, so two stations can sit in the same
 * world breeding at different rates or working areas of different sizes.
 *
 * <p>{@link ModConfig} is no longer what the ranchers read. It supplies the starting values for a
 * newly placed station and nothing else, which is why a fresh block still comes up with whatever
 * you put in the config file.
 *
 * <p>Each setting is declared once in {@link #OPTIONS}, and saving, loading, sending and drawing
 * all walk that list. Adding a setting is a field plus one entry, and it cannot then go missing
 * from the save file or the screen the way it could when all three were written out by hand.
 */
public final class StationSettings {
	public boolean enableBreeding;
	public boolean enableCulling;
	public boolean requireFeedItems;
	public boolean feedBabies;
	public boolean playFeedSound;
	public boolean openFenceGates;
	public boolean shoveBlockers;
	public boolean showWorkerState;
	public int workRadius;
	public int workHeight;
	public int workIntervalTicks;
	public int breedIntervalTicks;
	public int cullIntervalTicks;
	public int keepAdultsPerType;
	public int maxAnimalsPerType;
	public int workerRespawnTicks;

	/** Groups for the settings screen, in tab order. Keys are {@code config.breeder_scarecrow.*}. */
	public static final String BREEDING = "category.breeding";
	public static final String CULLING = "category.culling";
	public static final String AREA = "category.area";
	public static final String DISPLAY = "category.display";

	/** Bounds double as the slider ends, so what the screen offers is exactly what is accepted. */
	public static final List<Option> OPTIONS = List.of(
			new Flag(BREEDING, "enable_breeding", s -> s.enableBreeding, (s, v) -> s.enableBreeding = v),
			new Flag(BREEDING, "require_feed_items", s -> s.requireFeedItems, (s, v) -> s.requireFeedItems = v),
			new Range(BREEDING, "breed_interval_ticks", 1, 600, s -> s.breedIntervalTicks, (s, v) -> s.breedIntervalTicks = v),
			new Range(BREEDING, "max_animals_per_type", 2, 64, s -> s.maxAnimalsPerType, (s, v) -> s.maxAnimalsPerType = v),
			new Flag(BREEDING, "feed_babies", s -> s.feedBabies, (s, v) -> s.feedBabies = v),
			new Flag(BREEDING, "play_feed_sound", s -> s.playFeedSound, (s, v) -> s.playFeedSound = v),

			new Flag(CULLING, "enable_culling", s -> s.enableCulling, (s, v) -> s.enableCulling = v),
			new Range(CULLING, "keep_adults_per_type", 2, 32, s -> s.keepAdultsPerType, (s, v) -> s.keepAdultsPerType = v),
			new Range(CULLING, "cull_interval_ticks", 1, 1200, s -> s.cullIntervalTicks, (s, v) -> s.cullIntervalTicks = v),

			new Range(AREA, "work_radius", 1, 64, s -> s.workRadius, (s, v) -> s.workRadius = v),
			new Range(AREA, "work_height", 1, 32, s -> s.workHeight, (s, v) -> s.workHeight = v),
			new Range(AREA, "work_interval_ticks", 1, 200, s -> s.workIntervalTicks, (s, v) -> s.workIntervalTicks = v),
			new Range(AREA, "worker_respawn_ticks", 20, 2400, s -> s.workerRespawnTicks, (s, v) -> s.workerRespawnTicks = v),
			new Flag(AREA, "open_fence_gates", s -> s.openFenceGates, (s, v) -> s.openFenceGates = v),
			new Flag(AREA, "shove_blockers", s -> s.shoveBlockers, (s, v) -> s.shoveBlockers = v),

			new Flag(DISPLAY, "show_worker_state", s -> s.showWorkerState, (s, v) -> s.showWorkerState = v));

	/** The values a station starts life with, taken from the config file. */
	public StationSettings() {
		this(ModConfig.get());
	}

	/** The values the mod ships with, ignoring whatever the config file has been changed to. */
	public static StationSettings builtInDefaults() {
		return new StationSettings(new ModConfig());
	}

	public StationSettings(ModConfig config) {
		enableBreeding = config.enableBreeding;
		enableCulling = config.enableCulling;
		requireFeedItems = config.requireFeedItems;
		feedBabies = config.feedBabies;
		playFeedSound = config.playFeedSound;
		openFenceGates = config.openFenceGates;
		shoveBlockers = config.shoveBlockers;
		showWorkerState = config.showWorkerState;
		workRadius = config.workRadius;
		workHeight = config.workHeight;
		workIntervalTicks = config.workIntervalTicks;
		breedIntervalTicks = config.breedIntervalTicks;
		cullIntervalTicks = config.cullIntervalTicks;
		keepAdultsPerType = config.keepAdultsPerType;
		maxAnimalsPerType = config.maxAnimalsPerType;
		workerRespawnTicks = config.workerRespawnTicks;
		clamp();
	}

	public StationSettings copy() {
		return new StationSettings(this);
	}

	private StationSettings(StationSettings other) {
		copyFrom(other);
	}

	public void copyFrom(StationSettings other) {
		NbtCompound carrier = new NbtCompound();
		other.writeNbt(carrier);
		readNbt(carrier);
	}

	public void writeNbt(NbtCompound nbt) {
		for (Option option : OPTIONS) {
			option.write(this, nbt);
		}
	}

	/** Absent keys keep their current value, so a save from an older build loses nothing. */
	public void readNbt(NbtCompound nbt) {
		for (Option option : OPTIONS) {
			option.read(this, nbt);
		}

		clamp();
	}

	public void clamp() {
		for (Option option : OPTIONS) {
			option.clamp(this);
		}

		// Culling down to a herd larger than breeding is allowed to reach would have the rancher
		// undo its own work forever, so the ceiling is never below the floor.
		maxAnimalsPerType = Math.max(maxAnimalsPerType, keepAdultsPerType);
	}

	/** A setting, described in one place so the file, the packet and the screen cannot disagree. */
	public abstract static class Option {
		private final String category;
		private final String key;

		private Option(String category, String key) {
			this.category = category;
			this.key = key;
		}

		public String category() {
			return category;
		}

		/** Doubles as the NBT key, so there is no second naming scheme to keep in step. */
		public String key() {
			return key;
		}

		public String labelKey() {
			return "config.breeder_scarecrow." + key;
		}

		public String tooltipKey() {
			return labelKey() + ".tooltip";
		}

		abstract void write(StationSettings settings, NbtCompound nbt);

		abstract void read(StationSettings settings, NbtCompound nbt);

		abstract void clamp(StationSettings settings);
	}

	/** An on/off setting. */
	public static final class Flag extends Option {
		private final Function<StationSettings, Boolean> getter;
		private final BiConsumer<StationSettings, Boolean> setter;

		private Flag(String category, String key, Function<StationSettings, Boolean> getter,
				BiConsumer<StationSettings, Boolean> setter) {
			super(category, key);
			this.getter = getter;
			this.setter = setter;
		}

		public boolean get(StationSettings settings) {
			return getter.apply(settings);
		}

		public void set(StationSettings settings, boolean value) {
			setter.accept(settings, value);
		}

		@Override
		void write(StationSettings settings, NbtCompound nbt) {
			nbt.putBoolean(key(), get(settings));
		}

		@Override
		void read(StationSettings settings, NbtCompound nbt) {
			if (nbt.contains(key(), NbtElement.BYTE_TYPE)) {
				set(settings, nbt.getBoolean(key()));
			}
		}

		@Override
		void clamp(StationSettings settings) {
		}
	}

	/** A whole number setting with the ends the screen's slider runs between. */
	public static final class Range extends Option {
		private final int min;
		private final int max;
		private final ToIntFunction<StationSettings> getter;
		private final ObjIntConsumer<StationSettings> setter;

		private Range(String category, String key, int min, int max, ToIntFunction<StationSettings> getter,
				ObjIntConsumer<StationSettings> setter) {
			super(category, key);
			this.min = min;
			this.max = max;
			this.getter = getter;
			this.setter = setter;
		}

		public int min() {
			return min;
		}

		public int max() {
			return max;
		}

		public int get(StationSettings settings) {
			return getter.applyAsInt(settings);
		}

		public void set(StationSettings settings, int value) {
			setter.accept(settings, MathHelper.clamp(value, min, max));
		}

		@Override
		void write(StationSettings settings, NbtCompound nbt) {
			nbt.putInt(key(), get(settings));
		}

		@Override
		void read(StationSettings settings, NbtCompound nbt) {
			if (nbt.contains(key(), NbtElement.INT_TYPE)) {
				set(settings, nbt.getInt(key()));
			}
		}

		@Override
		void clamp(StationSettings settings) {
			set(settings, get(settings));
		}
	}
}
