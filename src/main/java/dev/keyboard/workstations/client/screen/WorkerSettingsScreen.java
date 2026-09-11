package dev.keyboard.workstations.client.screen;

import dev.keyboard.workstations.Mc;

import dev.keyboard.workstations.ModConfig;
import dev.keyboard.workstations.client.HighlightState;
import dev.keyboard.workstations.client.network.StationNetworkingClient;
import dev.keyboard.workstations.work.SeedMix;
import dev.keyboard.workstations.work.SettingOption;
import dev.keyboard.workstations.work.WorkerSettings;
//? if >=1.20 {
import net.minecraft.client.gui.DrawContext;
//?} else {
/* import net.minecraft.client.gui.Drawable;
import net.minecraft.client.util.math.MatrixStack; */
//?}
import net.minecraft.client.gui.Element;
//? if >=1.17 {
import net.minecraft.client.gui.Selectable;
//?}
import net.minecraft.client.gui.screen.Screen;
//? if >=1.19.3 {
import net.minecraft.client.gui.tooltip.Tooltip;
//?}
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.item.Item;
//? if >=1.19.3 {
import net.minecraft.screen.ScreenTexts;
//?} else {
/* import net.minecraft.client.gui.screen.ScreenTexts; */
//?}
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.List;
import java.util.function.Consumer;

/**
 * The settings screen for one station, opened by sneaking and using the block.
 *
 * <p>Rows are generated from the station's own option list, so a new setting appears here with no
 * work: it lands under its own tab with the label and description already wired to the existing
 * {@code config.keyboard_workstations.*} translations. Categories become the row of buttons along the
 * top, the current one marked by colouring its label rather than by any change of shape.
 *
 * <p>Everything here belongs to the station that was clicked. The one exception is the highlight
 * toggle on the display tab, which is a local drawing preference and stays in the config file.
 *
 * @param <S> the kind of orders this station keeps
 */
public abstract class WorkerSettingsScreen<S extends WorkerSettings<S>> extends Screen {
	protected static final int CONTROL_WIDTH = 100;
	protected static final int CONTROL_HEIGHT = 20;
	private static final int ROW_WIDTH = 320;
	/** Total width of the tab row and the footer row, so the two line up with each other. */
	private static final int BUTTON_ROW_WIDTH = 308;
	private static final int BUTTON_GAP = 4;
	private static final int TABS_TOP = 6;
	/** Breathing room between the tabs and the first row. */
	private static final int LIST_GAP = 8;
	private static final int FOOTER_HEIGHT = 40;

	protected final BlockPos pos;
	/** Edited in place, and sent to the server when the screen is dismissed. */
	protected final S settings;
	private final List<String> categories;

	/** Kept across a rebuild so changing a setting does not throw you back to the first tab. */
	private String activeCategory;

	/** Titled with the station's own block name, so the two can never drift apart. */
	protected WorkerSettingsScreen(Text title, BlockPos pos, S settings) {
		super(title);
		this.pos = pos;
		this.settings = settings;
		this.categories = settings.categories();
		this.activeCategory = categories.get(0);
	}

	/** Hands the edited settings to the server. Subclasses know which station they belong to. */
	protected abstract void save();

	/**
	 * Rows a tab wants that no option in the list describes, such as the farm's seed ratios. Called
	 * once per rebuild with the tab being built.
	 */
	protected void addExtraRows(String category, Consumer<Row> add) {
	}

	/**
	 * The ratio rows a planting station's mix tab is made of: one slider per item in the palette,
	 * or a line saying the container is empty.
	 *
	 * <p>Shared so a farm and a lumber station cannot drift apart on how a weight is shown or when
	 * the other rows' shares are refreshed. The keys are the caller's because a sapling tab should
	 * not talk about seeds.
	 */
	protected void addMixRows(SeedMix mix, List<Item> palette, String emptyKey, String tooltipKey,
			Consumer<Row> add) {
		if (palette.isEmpty()) {
			add.accept(new Row(Mc.translatable(emptyKey)));
			return;
		}

		for (Item item : palette) {
			add.accept(new Row(item.getName().copy(), new MixSlider(mix, item, palette),
					Mc.translatable(tooltipKey)));
		}
	}

	protected ButtonWidget button(int x, int y, int w, int h, Text text, ButtonWidget.PressAction action) {
		//? if >=1.19.4 {
		return ButtonWidget.builder(text, action).dimensions(x, y, w, h).build();
		//?} else {
		/* return new ButtonWidget(x, y, w, h, text, action); */
		//?}
	}

	private void addControl(ClickableWidget widget) {
		//? if >=1.17 {
		addDrawableChild(widget);
		//?} else {
		/* addButton(widget); */
		//?}
	}

	private void rebuild() {
		//? if >=1.19.3 {
		clearAndInit();
		//?} else {
		/* children.clear();
		buttons.clear();
		init(); */
		//?}
	}

	@Override
	protected void init() {
		int tabWidth = (BUTTON_ROW_WIDTH - BUTTON_GAP * (categories.size() - 1)) / categories.size();
		int x = width / 2 - BUTTON_ROW_WIDTH / 2;

		for (String category : categories) {
			boolean selected = category.equals(activeCategory);
			Text label = Mc.translatable("config.keyboard_workstations." + category);

			addControl(button(x, TABS_TOP, tabWidth, CONTROL_HEIGHT,
					selected ? label.copy().formatted(Formatting.YELLOW) : label,
					ignored -> showCategory(category)));

			x += tabWidth + BUTTON_GAP;
		}

		OptionList list = new OptionList(activeCategory, listTop(), height - FOOTER_HEIGHT);
		//? if >=1.17 {
		addDrawableChild(list);
		//?} else {
		/* addChild(list); */
		//?}

		int footerWidth = (BUTTON_ROW_WIDTH - BUTTON_GAP * 2) / 3;
		int footerX = width / 2 - BUTTON_ROW_WIDTH / 2;
		int footerY = height - FOOTER_HEIGHT + 10;

		addControl(button(footerX, footerY, footerWidth, CONTROL_HEIGHT,
				Mc.translatable("config.keyboard_workstations.worker_recall"),
				ignored -> StationNetworkingClient.recallWorker(pos)));

		addControl(button(footerX + footerWidth + BUTTON_GAP, footerY, footerWidth, CONTROL_HEIGHT,
				Mc.translatable("config.keyboard_workstations.reset"),
				ignored -> resetToDefaults()));

		addControl(button(footerX + (footerWidth + BUTTON_GAP) * 2, footerY, footerWidth, CONTROL_HEIGHT,
				ScreenTexts.DONE, ignored -> done()));
	}

	private int listTop() {
		return TABS_TOP + CONTROL_HEIGHT + LIST_GAP;
	}

	private void showCategory(String category) {
		// Rebuilding the same tab would only throw away the scroll position for nothing.
		if (!category.equals(activeCategory)) {
			activeCategory = category;
			rebuild();
		}
	}

	/** Restores this station to the values in the config file. */
	private void resetToDefaults() {
		settings.copyFrom(settings.shippedDefaults());
		rebuild();
	}

	/** Rebuilds the current tab, for a control whose row set depends on what it just changed. */
	protected void refresh() {
		rebuild();
	}

	@Override
	//? if >=1.20 {
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		//? if >=1.21 {
		/* super.render(context, mouseX, mouseY, delta); */
		//?} else {
		renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
		//?}
	}
	//?} else {
	/* public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
		renderBackground(matrices);
		for (Element child : children) {
			if (!(child instanceof ClickableWidget) && child instanceof Drawable drawable) {
				drawable.render(matrices, mouseX, mouseY, delta);
			}
		}
		for (Element child : children) {
			if (child instanceof ClickableWidget widget) {
				widget.render(matrices, mouseX, mouseY, delta);
			}
		}
	} */
	//?}

	private void done() {
		//? if >=1.19 {
		close();
		//?} else {
		/* onClose(); */
		//?}
	}

	/** Closing saves, so leaving by Escape keeps the changes rather than quietly binning them. */
	@Override
	//? if >=1.19 {
	public void close() {
		save();
		super.close();
	}
	//?} else {
	/* public void onClose() {
		save();
		super.onClose();
	} */
	//?}

	/**
	 * Leaves the screen without saving, for a button that has already saved on its own account and
	 * would otherwise send the same settings twice.
	 */
	protected void dismiss() {
		//? if >=1.19 {
		super.close();
		//?} else {
		/* super.onClose(); */
		//?}
	}

	protected static Text onOff(boolean value) {
		return value ? ScreenTexts.ON : ScreenTexts.OFF;
	}

	private ClickableWidget controlFor(SettingOption<S> option) {
		if (option instanceof SettingOption.Flag<S> flag) {
			return button(0, 0, CONTROL_WIDTH, CONTROL_HEIGHT, onOff(flag.get(settings)), button -> {
				flag.set(settings, !flag.get(settings));
				button.setMessage(onOff(flag.get(settings)));
			});
		}

		if (option instanceof SettingOption.Choice<S> choice) {
			return button(0, 0, CONTROL_WIDTH, CONTROL_HEIGHT, Mc.translatable(choice.valueLabelKey(settings)),
					button -> {
						choice.next(settings);
						button.setMessage(Mc.translatable(choice.valueLabelKey(settings)));
					});
		}

		return new OptionSlider<>((SettingOption.Range<S>) option, settings);
	}

	/**
	 * Writes to the local config rather than to the station, because whether the box is drawn is a
	 * question about this client and nothing to do with how the station is run.
	 */
	private ClickableWidget highlightControl() {
		ModConfig config = ModConfig.get();

		return button(0, 0, CONTROL_WIDTH, CONTROL_HEIGHT, onOff(config.highlightAlwaysOn), button -> {
			config.highlightAlwaysOn = !config.highlightAlwaysOn;
			config.save();
			HighlightState.applyConfig();
			button.setMessage(onOff(config.highlightAlwaysOn));
		});
	}

	/** A label on the left with its control on the right, the way vanilla's options screens read. */
	protected class Row extends ElementListWidget.Entry<Row> {
		private final Text label;
		private final ClickableWidget control;

		public Row(Text label, ClickableWidget control, Text tooltip) {
			this.label = label;
			this.control = control;
			//? if >=1.19.3 {
			control.setTooltip(Tooltip.of(tooltip));
			//?}
		}

		/** A row that is only text, for saying why a tab is empty. */
		public Row(Text label) {
			this.label = label;
			this.control = null;
		}

		@Override
		public List<? extends Element> children() {
			return control == null ? List.of() : List.of(control);
		}

		//? if >=1.17 {
		@Override
		public List<? extends Selectable> selectableChildren() {
			return control == null ? List.of() : List.of(control);
		}
		//?}

		@Override
		//? if >=1.20 {
		public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight,
				int mouseX, int mouseY, boolean hovered, float tickDelta) {
			context.drawTextWithShadow(textRenderer, label, x, y + (entryHeight - textRenderer.fontHeight) / 2,
					0xFFFFFF);

			if (control != null) {
				control.setX(x + entryWidth - CONTROL_WIDTH);
				control.setY(y);
				control.render(context, mouseX, mouseY, tickDelta);
			}
		}
		//?} else {
		/* public void render(MatrixStack matrices, int index, int y, int x, int entryWidth, int entryHeight,
				int mouseX, int mouseY, boolean hovered, float tickDelta) {
			textRenderer.draw(matrices, label, x, y + (entryHeight - textRenderer.fontHeight) / 2, 0xFFFFFF);

			if (control != null) {
				control.x = x + entryWidth - CONTROL_WIDTH;
				control.y = y;
				control.render(matrices, mouseX, mouseY, tickDelta);
			}
		} */
		//?}
	}

	private class OptionList extends ElementListWidget<Row> {
		OptionList(String category, int top, int bottom) {
			super(WorkerSettingsScreen.this.client, WorkerSettingsScreen.this.width,
					//? if >=1.21 {
					/* bottom - top, top, CONTROL_HEIGHT + 5); */
					//?} else {
					WorkerSettingsScreen.this.height, top, bottom, CONTROL_HEIGHT + 5);
					//?}

			for (SettingOption<S> option : settings.options()) {
				if (option.category().equals(category)) {
					addEntry(new Row(Mc.translatable(option.labelKey()), controlFor(option),
							Mc.translatable(option.tooltipKey())));
				}
			}

			addExtraRows(category, this::addEntry);

			if (WorkerSettings.DISPLAY.equals(category)) {
				addEntry(new Row(Mc.translatable("config.keyboard_workstations.highlight_always_on"),
						highlightControl(),
						Mc.translatable("config.keyboard_workstations.highlight_always_on.tooltip")));
			}
		}

		@Override
		public int getRowWidth() {
			return ROW_WIDTH;
		}

		@Override
		//? if >=1.21 {
		/* protected int getScrollbarX() { */
		//?} else {
		protected int getScrollbarPositionX() {
		//?}
			return WorkerSettingsScreen.this.width / 2 + ROW_WIDTH / 2 + 8;
		}
	}

	/**
	 * One kind's share of the plantings. The slider carries a weight rather than a percentage, and
	 * reports the percentage that weight currently works out to beside it: the percentages depend
	 * on every other kind, so they all move when any one of them does.
	 */
	private class MixSlider extends SliderWidget {
		private final SeedMix mix;
		private final Item item;
		private final List<Item> palette;

		MixSlider(SeedMix mix, Item item, List<Item> palette) {
			super(0, 0, CONTROL_WIDTH, CONTROL_HEIGHT, Mc.empty(), fraction(mix.weight(item)));
			this.mix = mix;
			this.item = item;
			this.palette = palette;
			updateMessage();
		}

		private static double fraction(int weight) {
			return (double) (weight - SeedMix.MIN_WEIGHT) / (SeedMix.MAX_WEIGHT - SeedMix.MIN_WEIGHT);
		}

		@Override
		protected void updateMessage() {
			setMessage(Mc.translatable("config.keyboard_workstations.seed_weight",
					mix.weight(item), mix.share(item, palette)));
		}

		@Override
		protected void applyValue() {
			mix.setWeight(item, (int) Math.round(
					MathHelper.lerp(value, SeedMix.MIN_WEIGHT, SeedMix.MAX_WEIGHT)));
		}

		/** Letting go rebuilds the tab, which is what brings the other kinds' shares up to date. */
		@Override
		public void onRelease(double mouseX, double mouseY) {
			super.onRelease(mouseX, mouseY);
			refresh();
		}
	}

	/** A whole number slider that reports the value itself rather than a percentage. */
	private static class OptionSlider<S extends WorkerSettings<S>> extends SliderWidget {
		private final SettingOption.Range<S> option;
		private final S settings;

		OptionSlider(SettingOption.Range<S> option, S settings) {
			super(0, 0, CONTROL_WIDTH, CONTROL_HEIGHT, Mc.empty(), fraction(option, settings));
			this.option = option;
			this.settings = settings;
			updateMessage();
		}

		private static <S extends WorkerSettings<S>> double fraction(SettingOption.Range<S> option, S settings) {
			return (double) (option.get(settings) - option.min()) / (option.max() - option.min());
		}

		@Override
		protected void updateMessage() {
			setMessage(Mc.literal(String.valueOf(option.get(settings))));
		}

		@Override
		protected void applyValue() {
			option.set(settings, (int) Math.round(MathHelper.lerp(value, option.min(), option.max())));
		}
	}
}
