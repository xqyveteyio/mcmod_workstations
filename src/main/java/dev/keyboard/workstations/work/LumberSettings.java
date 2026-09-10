package dev.keyboard.workstations.work;

import dev.keyboard.workstations.ModConfig;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

import java.util.ArrayList;
import java.util.List;

/**
 * One lumber station's own orders, the counterpart to {@link FarmSettings}. Same framework, so a
 * wood saves, syncs and draws itself through the code the farm already uses.
 *
 * <p>The sapling mix sits outside the option list because it is not a fixed set of settings: its
 * rows are whatever saplings the station has been given, so it cannot be declared up front and
 * gets a tab of its own that the screen builds from the container's contents. The mix itself is
 * {@link SeedMix} reused as-is: a weight table over {@link net.minecraft.item.Item} does not care
 * whether the item is a seed or a sapling.
 */
public final class LumberSettings implements WorkerSettings<LumberSettings> {
	public boolean enableChopping;
	public boolean enableReplanting;
	public boolean autoPlanting;
	public boolean forceGrowing;
	public boolean openFenceGates;
	public boolean shoveBlockers;
	public boolean showWorkerState;
	public int workRadius;
	public int workHeight;
	public int workIntervalTicks;
	public int lumberIntervalTicks;
	public int workerRespawnTicks;
	/** Position in {@link WorkerSkin#LUMBERJACK} of the look this station's lumberjack wears. */
	public int workerSkin;

	/** Weights per sapling. Edited on its own tab rather than through an {@link SettingOption}. */
	public final SeedMix saplingMix = new SeedMix();

	private static final String MIX_KEY = "SaplingMix";

	/** Groups for the settings screen, in tab order. Keys are {@code config.keyboard_workstations.*}. */
	public static final String WOODS = "category.lumber";
	/** The tab the sapling ratio rows are built onto, which has no fixed options of its own. */
	public static final String SAPLINGS = "category.saplings";
	public static final String AREA = WorkerSettings.AREA;
	public static final String DISPLAY = WorkerSettings.DISPLAY;

	public static final List<SettingOption<LumberSettings>> OPTIONS = List.of(
			SettingOption.flag(WOODS, "enable_chopping", s -> s.enableChopping, (s, v) -> s.enableChopping = v),
			SettingOption.flag(WOODS, "enable_replanting", s -> s.enableReplanting, (s, v) -> s.enableReplanting = v),
			SettingOption.flag(WOODS, "auto_planting", s -> s.autoPlanting, (s, v) -> s.autoPlanting = v),
			SettingOption.flag(WOODS, "force_growing", s -> s.forceGrowing, (s, v) -> s.forceGrowing = v),
			SettingOption.range(WOODS, "lumber_interval_ticks", 1, 200, s -> s.lumberIntervalTicks, (s, v) -> s.lumberIntervalTicks = v),

			SettingOption.range(AREA, "work_radius", 1, 64, s -> s.workRadius, (s, v) -> s.workRadius = v),
			SettingOption.range(AREA, "work_height", 1, 32, s -> s.workHeight, (s, v) -> s.workHeight = v),
			SettingOption.range(AREA, "work_interval_ticks", 1, 200, s -> s.workIntervalTicks, (s, v) -> s.workIntervalTicks = v),
			SettingOption.range(AREA, "worker_respawn_ticks", 20, 2400, s -> s.workerRespawnTicks, (s, v) -> s.workerRespawnTicks = v),
			SettingOption.flag(AREA, "open_fence_gates", s -> s.openFenceGates, (s, v) -> s.openFenceGates = v),
			SettingOption.flag(AREA, "shove_blockers", s -> s.shoveBlockers, (s, v) -> s.shoveBlockers = v),
			SettingOption.flag(DISPLAY, "show_worker_state", s -> s.showWorkerState, (s, v) -> s.showWorkerState = v),
			SettingOption.choice(DISPLAY, "worker_skin", WorkerSkin.ids(WorkerSkin.LUMBERJACK), s -> s.workerSkin, (s, v) -> s.workerSkin = v));

	public LumberSettings() {
		this(ModConfig.get());
	}

	public LumberSettings(ModConfig config) {
		enableChopping = config.enableChopping;
		enableReplanting = config.enableReplanting;
		autoPlanting = config.autoPlanting;
		forceGrowing = config.forceGrowing;
		openFenceGates = config.openFenceGates;
		shoveBlockers = config.shoveBlockers;
		showWorkerState = config.showWorkerState;
		workRadius = config.workRadius;
		workHeight = config.workHeight;
		workIntervalTicks = config.workIntervalTicks;
		lumberIntervalTicks = config.lumberIntervalTicks;
		workerRespawnTicks = config.workerRespawnTicks;
		clamp();
	}

	@Override
	public List<SettingOption<LumberSettings>> options() {
		return OPTIONS;
	}

	/** The sapling tab is appended by hand, being the one tab no option in the list mentions. */
	@Override
	public List<String> categories() {
		List<String> tabs = new ArrayList<>(WorkerSettings.super.categories());
		tabs.add(1, SAPLINGS);
		return tabs;
	}

	@Override
	public LumberSettings shippedDefaults() {
		return new LumberSettings();
	}

	@Override
	public LumberSettings copy() {
		LumberSettings clone = new LumberSettings();
		clone.copyFrom(this);
		return clone;
	}

	@Override
	public void copyFrom(LumberSettings other) {
		NbtCompound carrier = new NbtCompound();
		other.writeNbt(carrier);
		readNbt(carrier);
	}

	@Override
	public void writeNbt(NbtCompound nbt) {
		for (SettingOption<LumberSettings> option : OPTIONS) {
			option.write(this, nbt);
		}

		NbtCompound mix = new NbtCompound();
		saplingMix.writeNbt(mix);
		nbt.put(MIX_KEY, mix);
	}

	@Override
	public void readNbt(NbtCompound nbt) {
		for (SettingOption<LumberSettings> option : OPTIONS) {
			option.read(this, nbt);
		}

		if (nbt.contains(MIX_KEY, NbtElement.COMPOUND_TYPE)) {
			saplingMix.readNbt(nbt.getCompound(MIX_KEY));
		}

		clamp();
	}

	public void clamp() {
		for (SettingOption<LumberSettings> option : OPTIONS) {
			option.clamp(this);
		}
	}
}
