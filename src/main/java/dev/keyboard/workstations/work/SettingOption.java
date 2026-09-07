package dev.keyboard.workstations.work;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.math.MathHelper;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.ObjIntConsumer;
import java.util.function.ToIntFunction;

/**
 * One setting, described in a single place so the save file, the packet and the screen cannot
 * disagree about it. Declaring a setting is a field plus one entry in its owner's option list, and
 * it cannot then go missing from any of the three the way it could when all were written by hand.
 *
 * <p>Parameterised on the settings object it belongs to, because a ranch and a farm keep different
 * orders but want identical saving, sending and drawing.
 */
public abstract class SettingOption<S> {
	private final String category;
	private final String key;

	private SettingOption(String category, String key) {
		this.category = category;
		this.key = key;
	}

	/** An on/off setting. */
	public static <S> Flag<S> flag(String category, String key, Function<S, Boolean> getter,
			BiConsumer<S, Boolean> setter) {
		return new Flag<>(category, key, getter, setter);
	}

	/** A whole number setting, its bounds doubling as the ends of the screen's slider. */
	public static <S> Range<S> range(String category, String key, int min, int max, ToIntFunction<S> getter,
			ObjIntConsumer<S> setter) {
		return new Range<>(category, key, min, max, getter, setter);
	}

	/**
	 * A setting picked from a short fixed list, kept as the position in that list and shown as a
	 * button that steps through it.
	 */
	public static <S> Choice<S> choice(String category, String key, List<String> valueIds, ToIntFunction<S> getter,
			ObjIntConsumer<S> setter) {
		return new Choice<>(category, key, valueIds, getter, setter);
	}

	/** Which tab of the settings screen this lands under. */
	public String category() {
		return category;
	}

	/** Doubles as the NBT key, so there is no second naming scheme to keep in step. */
	public String key() {
		return key;
	}

	public String labelKey() {
		return "config.workstations." + key;
	}

	public String tooltipKey() {
		return labelKey() + ".tooltip";
	}

	public abstract void write(S settings, NbtCompound nbt);

	public abstract void read(S settings, NbtCompound nbt);

	public abstract void clamp(S settings);

	public static final class Flag<S> extends SettingOption<S> {
		private final Function<S, Boolean> getter;
		private final BiConsumer<S, Boolean> setter;

		private Flag(String category, String key, Function<S, Boolean> getter, BiConsumer<S, Boolean> setter) {
			super(category, key);
			this.getter = getter;
			this.setter = setter;
		}

		public boolean get(S settings) {
			return getter.apply(settings);
		}

		public void set(S settings, boolean value) {
			setter.accept(settings, value);
		}

		@Override
		public void write(S settings, NbtCompound nbt) {
			nbt.putBoolean(key(), get(settings));
		}

		@Override
		public void read(S settings, NbtCompound nbt) {
			if (nbt.contains(key(), NbtElement.BYTE_TYPE)) {
				set(settings, nbt.getBoolean(key()));
			}
		}

		@Override
		public void clamp(S settings) {
		}
	}

	public static final class Range<S> extends SettingOption<S> {
		private final int min;
		private final int max;
		private final ToIntFunction<S> getter;
		private final ObjIntConsumer<S> setter;

		private Range(String category, String key, int min, int max, ToIntFunction<S> getter,
				ObjIntConsumer<S> setter) {
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

		public int get(S settings) {
			return getter.applyAsInt(settings);
		}

		public void set(S settings, int value) {
			setter.accept(settings, MathHelper.clamp(value, min, max));
		}

		@Override
		public void write(S settings, NbtCompound nbt) {
			nbt.putInt(key(), get(settings));
		}

		@Override
		public void read(S settings, NbtCompound nbt) {
			if (nbt.contains(key(), NbtElement.INT_TYPE)) {
				set(settings, nbt.getInt(key()));
			}
		}

		/** Re-applying the value through the setter is what pulls a stale save back into bounds. */
		@Override
		public void clamp(S settings) {
			set(settings, get(settings));
		}
	}

	public static final class Choice<S> extends SettingOption<S> {
		private final List<String> valueIds;
		private final ToIntFunction<S> getter;
		private final ObjIntConsumer<S> setter;

		private Choice(String category, String key, List<String> valueIds, ToIntFunction<S> getter,
				ObjIntConsumer<S> setter) {
			super(category, key);
			this.valueIds = List.copyOf(valueIds);
			this.getter = getter;
			this.setter = setter;
		}

		public int get(S settings) {
			return getter.applyAsInt(settings);
		}

		public void set(S settings, int value) {
			setter.accept(settings, MathHelper.clamp(value, 0, valueIds.size() - 1));
		}

		/** Wraps round, so the button finds its way back rather than dead ending on the last value. */
		public void next(S settings) {
			set(settings, (get(settings) + 1) % valueIds.size());
		}

		/** Names the chosen value, so the button reads as that value rather than as its number. */
		public String valueLabelKey(S settings) {
			return labelKey() + "." + valueIds.get(get(settings));
		}

		@Override
		public void write(S settings, NbtCompound nbt) {
			nbt.putInt(key(), get(settings));
		}

		@Override
		public void read(S settings, NbtCompound nbt) {
			if (nbt.contains(key(), NbtElement.INT_TYPE)) {
				set(settings, nbt.getInt(key()));
			}
		}

		/** A save naming a value this build no longer offers is pulled back to one that exists. */
		@Override
		public void clamp(S settings) {
			set(settings, get(settings));
		}
	}
}
