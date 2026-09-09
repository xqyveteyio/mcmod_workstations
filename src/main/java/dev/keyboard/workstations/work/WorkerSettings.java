package dev.keyboard.workstations.work;

import net.minecraft.nbt.NbtCompound;

import java.util.ArrayList;
import java.util.List;

/**
 * What every kind of station's orders have in common: a list of settings that describes itself, and
 * the ability to be saved, loaded, copied and reset. The settings screen and the packets that carry
 * settings about are written against this, so a new kind of station needs neither of its own.
 *
 * @param <S> the implementing type, so options and copying stay type safe
 */
public interface WorkerSettings<S extends WorkerSettings<S>> {
	/** Tabs that mean the same thing whatever the station is for, so the screen can rely on them. */
	String AREA = "category.area";
	String DISPLAY = "category.display";

	/** Every setting this kind of station has, in the order the screen should lay them out. */
	List<SettingOption<S>> options();

	void writeNbt(NbtCompound nbt);

	void readNbt(NbtCompound nbt);

	void copyFrom(S other);

	S copy();

	/**
	 * The values from {@code config/keyboard_workstations.json}, so the reset button restores what
	 * a newly placed station would start with.
	 */
	S shippedDefaults();

	/** The tabs the screen shows: the categories the option list mentions, in that order. */
	default List<String> categories() {
		List<String> tabs = new ArrayList<>();

		for (SettingOption<S> option : options()) {
			if (!tabs.contains(option.category())) {
				tabs.add(option.category());
			}
		}

		return tabs;
	}
}
