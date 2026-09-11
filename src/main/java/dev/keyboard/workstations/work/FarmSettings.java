package dev.keyboard.workstations.work;

import dev.keyboard.workstations.Mc;

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
	public boolean harvestGourds;
	public boolean harvestMushrooms;
	public boolean openFenceGates;
	public boolean shoveBlockers;
	public boolean showWorkerState;
	public int workAlong;
	public int workAcross;
	public int workAbove;
	public int workBelow;
	public int workIntervalTicks;
	public int farmIntervalTicks;
	public int workerRespawnTicks;
	/** Position in {@link WorkerSkin#FARMER} of the look this station's farmer wears. */
	public int workerSkin;

	/** Weights per seed. Edited on its own tab rather than through an {@link SettingOption}. */
	public final SeedMix seedMix = new SeedMix();

	private static final String MIX_KEY = "SeedMix";

	/** Groups for the settings screen, in tab order. Keys are {@code config.keyboard_workstations.*}. */
	public static final String FIELD = "category.field";
	/** The tab the seed ratio rows are built onto, which has no fixed options of its own. */
	public static final String SEEDS = "category.seeds";
	public static final String AREA = WorkerSettings.AREA;
	public static final String DISPLAY = WorkerSettings.DISPLAY;

	public static final List<SettingOption<FarmSettings>> OPTIONS = List.of(
			SettingOption.flag(FIELD, "enable_tilling", s -> s.enableTilling, (s, v) -> s.enableTilling = v),
			SettingOption.flag(FIELD, "enable_sowing", s -> s.enableSowing, (s, v) -> s.enableSowing = v),
			SettingOption.flag(FIELD, "enable_harvesting", s -> s.enableHarvesting, (s, v) -> s.enableHarvesting = v),
			SettingOption.flag(FIELD, "harvest_gourds", s -> s.harvestGourds, (s, v) -> s.harvestGourds = v),
			SettingOption.flag(FIELD, "harvest_mushrooms", s -> s.harvestMushrooms, (s, v) -> s.harvestMushrooms = v),
			SettingOption.range(FIELD, "farm_interval_ticks", 1, 200, s -> s.farmIntervalTicks, (s, v) -> s.farmIntervalTicks = v),

			SettingOption.range(AREA, "work_along", 1, 64, s -> s.workAlong, (s, v) -> s.workAlong = v),
			SettingOption.range(AREA, "work_across", 1, 64, s -> s.workAcross, (s, v) -> s.workAcross = v),
			SettingOption.range(AREA, "work_above", 0, 32, s -> s.workAbove, (s, v) -> s.workAbove = v),
			SettingOption.range(AREA, "work_below", 0, 32, s -> s.workBelow, (s, v) -> s.workBelow = v),
			SettingOption.range(AREA, "work_interval_ticks", 1, 200, s -> s.workIntervalTicks, (s, v) -> s.workIntervalTicks = v),
			SettingOption.range(AREA, "worker_respawn_ticks", 20, 2400, s -> s.workerRespawnTicks, (s, v) -> s.workerRespawnTicks = v),
			SettingOption.flag(AREA, "open_fence_gates", s -> s.openFenceGates, (s, v) -> s.openFenceGates = v),
			SettingOption.flag(AREA, "shove_blockers", s -> s.shoveBlockers, (s, v) -> s.shoveBlockers = v),
			SettingOption.flag(DISPLAY, "show_worker_state", s -> s.showWorkerState, (s, v) -> s.showWorkerState = v),
			SettingOption.choice(DISPLAY, "worker_skin", WorkerSkin.ids(WorkerSkin.FARMER), s -> s.workerSkin, (s, v) -> s.workerSkin = v));

	public FarmSettings() {
		this(ModConfig.get());
	}

	public FarmSettings(ModConfig config) {
		enableTilling = config.enableTilling;
		enableSowing = config.enableSowing;
		enableHarvesting = config.enableHarvesting;
		harvestGourds = config.harvestGourds;
		harvestMushrooms = config.harvestMushrooms;
		openFenceGates = config.openFenceGates;
		shoveBlockers = config.shoveBlockers;
		showWorkerState = config.showWorkerState;
		workAlong = config.workRadius;
		workAcross = config.workRadius;
		workAbove = config.workHeight;
		workBelow = config.workHeight;
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
		return new FarmSettings();
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

		if (Mc.has(nbt, MIX_KEY, Mc.NBT_COMPOUND)) {
			seedMix.readNbt(Mc.compound(nbt, MIX_KEY));
		}

		WorkerSettings.inheritWorkRadius(nbt, v -> workAlong = v, v -> workAcross = v);
		WorkerSettings.inheritWorkHeight(nbt, v -> workAbove = v, v -> workBelow = v);
		clamp();
	}

	public void clamp() {
		for (SettingOption<FarmSettings> option : OPTIONS) {
			option.clamp(this);
		}
	}
}
