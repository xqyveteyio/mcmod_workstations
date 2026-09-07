package dev.keyboard.workstations.work;

import dev.keyboard.workstations.ModConfig;
import net.minecraft.nbt.NbtCompound;

import java.util.List;

/**
 * One ranch station's own orders. Every ranch is set up separately, so two stations can sit in the
 * same world breeding at different rates or working areas of different sizes.
 *
 * <p>{@link ModConfig} is no longer what the ranchers read. It supplies the starting values for a
 * newly placed station and nothing else, which is why a fresh block still comes up with whatever
 * you put in the config file.
 *
 * <p>Each setting is declared once in {@link #OPTIONS}, and saving, loading, sending and drawing
 * all walk that list. See {@link SettingOption}.
 */
public final class StationSettings implements WorkerSettings<StationSettings> {
	public boolean enableBreeding;
	public boolean enableCulling;
	public boolean requireFeedItems;
	public boolean feedBabies;
	public boolean playFeedSound;
	public boolean instantKill;
	public boolean enableShearing;
	public boolean enableMilking;
	public boolean openFenceGates;
	public boolean shoveBlockers;
	public boolean invulnerable;
	public boolean showWorkerState;
	public int workRadius;
	public int workHeight;
	public int workIntervalTicks;
	public int breedIntervalTicks;
	public int cullIntervalTicks;
	public int keepAdultsPerType;
	public int maxAnimalsPerType;
	public int workerRespawnTicks;

	/** Groups for the settings screen, in tab order. Keys are {@code config.workstations.*}. */
	public static final String BREEDING = "category.breeding";
	public static final String CULLING = "category.culling";
	public static final String HARVEST = "category.harvest";
	public static final String AREA = WorkerSettings.AREA;
	public static final String DISPLAY = WorkerSettings.DISPLAY;

	/** Bounds double as the slider ends, so what the screen offers is exactly what is accepted. */
	public static final List<SettingOption<StationSettings>> OPTIONS = List.of(
			SettingOption.flag(BREEDING, "enable_breeding", s -> s.enableBreeding, (s, v) -> s.enableBreeding = v),
			SettingOption.flag(BREEDING, "require_feed_items", s -> s.requireFeedItems, (s, v) -> s.requireFeedItems = v),
			SettingOption.range(BREEDING, "breed_interval_ticks", 1, 600, s -> s.breedIntervalTicks, (s, v) -> s.breedIntervalTicks = v),
			SettingOption.range(BREEDING, "max_animals_per_type", 2, 64, s -> s.maxAnimalsPerType, (s, v) -> s.maxAnimalsPerType = v),
			SettingOption.flag(BREEDING, "feed_babies", s -> s.feedBabies, (s, v) -> s.feedBabies = v),
			SettingOption.flag(BREEDING, "play_feed_sound", s -> s.playFeedSound, (s, v) -> s.playFeedSound = v),

			SettingOption.flag(CULLING, "enable_culling", s -> s.enableCulling, (s, v) -> s.enableCulling = v),
			SettingOption.range(CULLING, "keep_adults_per_type", 2, 32, s -> s.keepAdultsPerType, (s, v) -> s.keepAdultsPerType = v),
			SettingOption.range(CULLING, "cull_interval_ticks", 1, 1200, s -> s.cullIntervalTicks, (s, v) -> s.cullIntervalTicks = v),
			SettingOption.flag(CULLING, "instant_kill", s -> s.instantKill, (s, v) -> s.instantKill = v),

			SettingOption.flag(HARVEST, "enable_shearing", s -> s.enableShearing, (s, v) -> s.enableShearing = v),
			SettingOption.flag(HARVEST, "enable_milking", s -> s.enableMilking, (s, v) -> s.enableMilking = v),

			SettingOption.range(AREA, "work_radius", 1, 64, s -> s.workRadius, (s, v) -> s.workRadius = v),
			SettingOption.range(AREA, "work_height", 1, 32, s -> s.workHeight, (s, v) -> s.workHeight = v),
			SettingOption.range(AREA, "work_interval_ticks", 1, 200, s -> s.workIntervalTicks, (s, v) -> s.workIntervalTicks = v),
			SettingOption.range(AREA, "worker_respawn_ticks", 20, 2400, s -> s.workerRespawnTicks, (s, v) -> s.workerRespawnTicks = v),
			SettingOption.flag(AREA, "open_fence_gates", s -> s.openFenceGates, (s, v) -> s.openFenceGates = v),
			SettingOption.flag(AREA, "shove_blockers", s -> s.shoveBlockers, (s, v) -> s.shoveBlockers = v),
			SettingOption.flag(AREA, "invulnerable", s -> s.invulnerable, (s, v) -> s.invulnerable = v),

			SettingOption.flag(DISPLAY, "show_worker_state", s -> s.showWorkerState, (s, v) -> s.showWorkerState = v));

	/** The values a station starts life with, taken from the config file. */
	public StationSettings() {
		this(ModConfig.get());
	}

	public StationSettings(ModConfig config) {
		enableBreeding = config.enableBreeding;
		enableCulling = config.enableCulling;
		requireFeedItems = config.requireFeedItems;
		feedBabies = config.feedBabies;
		playFeedSound = config.playFeedSound;
		instantKill = config.instantKill;
		enableShearing = config.enableShearing;
		enableMilking = config.enableMilking;
		openFenceGates = config.openFenceGates;
		shoveBlockers = config.shoveBlockers;
		invulnerable = config.invulnerable;
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

	@Override
	public List<SettingOption<StationSettings>> options() {
		return OPTIONS;
	}

	@Override
	public StationSettings shippedDefaults() {
		return new StationSettings(new ModConfig());
	}

	@Override
	public StationSettings copy() {
		StationSettings clone = new StationSettings();
		clone.copyFrom(this);
		return clone;
	}

	@Override
	public void copyFrom(StationSettings other) {
		NbtCompound carrier = new NbtCompound();
		other.writeNbt(carrier);
		readNbt(carrier);
	}

	@Override
	public void writeNbt(NbtCompound nbt) {
		for (SettingOption<StationSettings> option : OPTIONS) {
			option.write(this, nbt);
		}
	}

	/** Absent keys keep their current value, so a save from an older build loses nothing. */
	@Override
	public void readNbt(NbtCompound nbt) {
		for (SettingOption<StationSettings> option : OPTIONS) {
			option.read(this, nbt);
		}

		clamp();
	}

	public void clamp() {
		for (SettingOption<StationSettings> option : OPTIONS) {
			option.clamp(this);
		}

		// Culling down to a herd larger than breeding is allowed to reach would have the rancher
		// undo its own work forever, so the ceiling is never below the floor.
		maxAnimalsPerType = Math.max(maxAnimalsPerType, keepAdultsPerType);
	}
}
