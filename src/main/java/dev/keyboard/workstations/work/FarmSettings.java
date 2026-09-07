package dev.keyboard.workstations.work;

import dev.keyboard.workstations.ModConfig;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

import java.util.ArrayList;
import java.util.List;

/**
 * One farm station's own orders, the counterpart to {@link StationSettings}. Same framework, so a
 * farm saves, syncs and draws itself through the code the ranch already uses.
 *
 * <p>The seed mix sits outside the option list because it is not a fixed set of settings: its rows
 * are whatever seeds the station has been given, so it cannot be declared up front and gets a tab
 * of its own that the screen builds from the container's contents.
 */
public final class FarmSettings implements WorkerSettings<FarmSettings> {
	public boolean enableTilling;
	public boolean enableSowing;
	public boolean enableHarvesting;
	public boolean consumeSeeds;
	public boolean openFenceGates;
	public boolean shoveBlockers;
	public boolean invulnerable;
	public boolean showWorkerState;
	public int workRadius;
	public int workHeight;
	public int workIntervalTicks;
	public int farmIntervalTicks;
	public int workerRespawnTicks;
	/** Position in {@link WorkerSkin#FARMER} of the look this station's farmer wears. */
	public int workerSkin;

	/** Weights per seed. Edited on its own tab rather than through an {@link SettingOption}. */
	public final SeedMix seedMix = new SeedMix();

	private static final String MIX_KEY = "SeedMix";

	/** Groups for the settings screen, in tab order. Keys are {@code config.workstations.*}. */
	public static final String FIELD = "category.field";
	/** The tab the seed ratio rows are built onto, which has no fixed options of its own. */
	public static final String SEEDS = "category.seeds";
	public static final String AREA = WorkerSettings.AREA;
	public static final String DISPLAY = WorkerSettings.DISPLAY;

	public static final List<SettingOption<FarmSettings>> OPTIONS = List.of(
			SettingOption.flag(FIELD, "enable_tilling", s -> s.enableTilling, (s, v) -> s.enableTilling = v),
			SettingOption.flag(FIELD, "enable_sowing", s -> s.enableSowing, (s, v) -> s.enableSowing = v),
			SettingOption.flag(FIELD, "enable_harvesting", s -> s.enableHarvesting, (s, v) -> s.enableHarvesting = v),
			SettingOption.flag(FIELD, "consume_seeds", s -> s.consumeSeeds, (s, v) -> s.consumeSeeds = v),
			SettingOption.range(FIELD, "farm_interval_ticks", 1, 200, s -> s.farmIntervalTicks, (s, v) -> s.farmIntervalTicks = v),

			SettingOption.range(AREA, "work_radius", 1, 64, s -> s.workRadius, (s, v) -> s.workRadius = v),
			SettingOption.range(AREA, "work_height", 1, 32, s -> s.workHeight, (s, v) -> s.workHeight = v),
			SettingOption.range(AREA, "work_interval_ticks", 1, 200, s -> s.workIntervalTicks, (s, v) -> s.workIntervalTicks = v),
			SettingOption.range(AREA, "worker_respawn_ticks", 20, 2400, s -> s.workerRespawnTicks, (s, v) -> s.workerRespawnTicks = v),
			SettingOption.flag(AREA, "open_fence_gates", s -> s.openFenceGates, (s, v) -> s.openFenceGates = v),
			SettingOption.flag(AREA, "shove_blockers", s -> s.shoveBlockers, (s, v) -> s.shoveBlockers = v),
			SettingOption.flag(AREA, "invulnerable", s -> s.invulnerable, (s, v) -> s.invulnerable = v),

			SettingOption.flag(DISPLAY, "show_worker_state", s -> s.showWorkerState, (s, v) -> s.showWorkerState = v),
			SettingOption.choice(DISPLAY, "worker_skin", WorkerSkin.ids(WorkerSkin.FARMER), s -> s.workerSkin, (s, v) -> s.workerSkin = v));

	public FarmSettings() {
		this(ModConfig.get());
	}

	public FarmSettings(ModConfig config) {
		enableTilling = config.enableTilling;
		enableSowing = config.enableSowing;
		enableHarvesting = config.enableHarvesting;
		consumeSeeds = config.consumeSeeds;
		openFenceGates = config.openFenceGates;
		shoveBlockers = config.shoveBlockers;
		invulnerable = config.invulnerable;
		showWorkerState = config.showWorkerState;
		workRadius = config.workRadius;
		workHeight = config.workHeight;
		workIntervalTicks = config.workIntervalTicks;
		farmIntervalTicks = config.farmIntervalTicks;
		workerRespawnTicks = config.workerRespawnTicks;
		clamp();
	}

	@Override
	public List<SettingOption<FarmSettings>> options() {
		return OPTIONS;
	}

	/** The seed tab is appended by hand, being the one tab no option in the list mentions. */
	@Override
	public List<String> categories() {
		List<String> tabs = new ArrayList<>(WorkerSettings.super.categories());
		tabs.add(1, SEEDS);
		return tabs;
	}

	@Override
	public FarmSettings shippedDefaults() {
		return new FarmSettings(new ModConfig());
	}

	@Override
	public FarmSettings copy() {
		FarmSettings clone = new FarmSettings();
		clone.copyFrom(this);
		return clone;
	}

	@Override
	public void copyFrom(FarmSettings other) {
		NbtCompound carrier = new NbtCompound();
		other.writeNbt(carrier);
		readNbt(carrier);
	}

	@Override
	public void writeNbt(NbtCompound nbt) {
		for (SettingOption<FarmSettings> option : OPTIONS) {
			option.write(this, nbt);
		}

		NbtCompound mix = new NbtCompound();
		seedMix.writeNbt(mix);
		nbt.put(MIX_KEY, mix);
	}

	@Override
	public void readNbt(NbtCompound nbt) {
		for (SettingOption<FarmSettings> option : OPTIONS) {
			option.read(this, nbt);
		}

		if (nbt.contains(MIX_KEY, NbtElement.COMPOUND_TYPE)) {
			seedMix.readNbt(nbt.getCompound(MIX_KEY));
		}

		clamp();
	}

	public void clamp() {
		for (SettingOption<FarmSettings> option : OPTIONS) {
			option.clamp(this);
		}
	}
}
