package dev.keyboard.workstations.client.screen;

import dev.keyboard.workstations.ModConfig;
import dev.keyboard.workstations.client.HighlightState;
import dev.keyboard.workstations.client.network.StationNetworkingClient;
import dev.keyboard.workstations.work.SeedMix;
import dev.keyboard.workstations.work.SettingOption;
import dev.keyboard.workstations.work.WorkerSettings;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;

/**
 * The settings screen for one station, opened by sneaking and using the block.
 *
 * <p>Rows are generated from the station's own option list, so a new setting appears here with no
 * work: it lands under its own tab with the label and description already wired to the existing
 * {@code config.villager_workstations.*} translations. Categories become the row of buttons along the
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
	protected WorkerSettingsScreen(Component title, BlockPos pos, S settings) {
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
			add.accept(new Row(Component.translatable(emptyKey)));
			return;
		}

		for (Item item : palette) {
			add.accept(new Row(item.getName(item.getDefaultInstance()).copy(), new MixSlider(mix, item, palette),
					Component.translatable(tooltipKey)));
		}
	}

	@Override
	protected void init() {
		int tabWidth = (BUTTON_ROW_WIDTH - BUTTON_GAP * (categories.size() - 1)) / categories.size();
		int x = width / 2 - BUTTON_ROW_WIDTH / 2;

		for (String category : categories) {
			boolean selected = category.equals(activeCategory);
			Component label = Component.translatable("config.villager_workstations." + category);

			addRenderableWidget(Button.builder(selected ? label.copy().withStyle(ChatFormatting.YELLOW) : label,
							button -> showCategory(category))
					.bounds(x, TABS_TOP, tabWidth, CONTROL_HEIGHT)
					.build());

			x += tabWidth + BUTTON_GAP;
		}

		addRenderableWidget(new OptionList(activeCategory, listTop(), height - FOOTER_HEIGHT));

		int footerWidth = (BUTTON_ROW_WIDTH - BUTTON_GAP * 2) / 3;
		int footerX = width / 2 - BUTTON_ROW_WIDTH / 2;
		int footerY = height - FOOTER_HEIGHT + 10;

		addRenderableWidget(Button.builder(Component.translatable("config.villager_workstations.worker_recall"),
						button -> StationNetworkingClient.recallWorker(pos))
				.bounds(footerX, footerY, footerWidth, CONTROL_HEIGHT)
				.build());

		addRenderableWidget(Button.builder(Component.translatable("config.villager_workstations.reset"),
						button -> resetToDefaults())
				.bounds(footerX + footerWidth + BUTTON_GAP, footerY, footerWidth, CONTROL_HEIGHT)
				.build());

		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
				.bounds(footerX + (footerWidth + BUTTON_GAP) * 2, footerY, footerWidth, CONTROL_HEIGHT)
				.build());
	}

	private int listTop() {
		return TABS_TOP + CONTROL_HEIGHT + LIST_GAP;
	}

	private void showCategory(String category) {
		// Rebuilding the same tab would only throw away the scroll position for nothing.
		if (!category.equals(activeCategory)) {
			activeCategory = category;
			rebuildWidgets();
		}
	}

	/** Restores this station to the values in the config file. */
	private void resetToDefaults() {
		settings.copyFrom(settings.shippedDefaults());
		rebuildWidgets();
	}

	/** Rebuilds the current tab, for a control whose row set depends on what it just changed. */
	protected void refresh() {
		rebuildWidgets();
	}

	/** Closing saves, so leaving by Escape keeps the changes rather than quietly binning them. */
	@Override
	public void onClose() {
		save();
		super.onClose();
	}

	/**
	 * Leaves the screen without saving, for a button that has already saved on its own account and
	 * would otherwise send the same settings twice.
	 */
	protected void dismiss() {
		super.onClose();
	}

	protected static Component onOff(boolean value) {
		return value ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF;
	}

	private AbstractWidget controlFor(SettingOption<S> option) {
		if (option instanceof SettingOption.Flag<S> flag) {
			return Button.builder(onOff(flag.get(settings)), button -> {
						flag.set(settings, !flag.get(settings));
						button.setMessage(onOff(flag.get(settings)));
					})
					.bounds(0, 0, CONTROL_WIDTH, CONTROL_HEIGHT)
					.build();
		}

		if (option instanceof SettingOption.Choice<S> choice) {
			return Button.builder(Component.translatable(choice.valueLabelKey(settings)), button -> {
						choice.next(settings);
						button.setMessage(Component.translatable(choice.valueLabelKey(settings)));
					})
					.bounds(0, 0, CONTROL_WIDTH, CONTROL_HEIGHT)
					.build();
		}

		return new OptionSlider<>((SettingOption.Range<S>) option, settings);
	}

	/**
	 * Writes to the local config rather than to the station, because whether the box is drawn is a
	 * question about this client and nothing to do with how the station is run.
	 */
	private AbstractWidget highlightControl() {
		ModConfig config = ModConfig.get();

		return Button.builder(onOff(config.highlightAlwaysOn), button -> {
					config.highlightAlwaysOn = !config.highlightAlwaysOn;
					config.save();
					HighlightState.applyConfig();
					button.setMessage(onOff(config.highlightAlwaysOn));
				})
				.bounds(0, 0, CONTROL_WIDTH, CONTROL_HEIGHT)
				.build();
	}

	/** A label on the left with its control on the right, the way vanilla's options screens read. */
	protected class Row extends ContainerObjectSelectionList.Entry<Row> {
		private final Component label;
		private final AbstractWidget control;

		public Row(Component label, AbstractWidget control, Component tooltip) {
			this.label = label;
			this.control = control;
			control.setTooltip(Tooltip.create(tooltip));
		}

		/** A row that is only text, for saying why a tab is empty. */
		public Row(Component label) {
			this.label = label;
			this.control = null;
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return control == null ? List.of() : List.of(control);
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return control == null ? List.of() : List.of(control);
		}

		@Override
		public void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered,
				float tickDelta) {
			context.text(font, label, getContentX(), getY() + (getHeight() - font.lineHeight) / 2,
					ARGB.opaque(0xFFFFFF));

			if (control != null) {
				control.setX(getContentX() + getContentWidth() - CONTROL_WIDTH);
				control.setY(getY());
				control.extractRenderState(context, mouseX, mouseY, tickDelta);
			}
		}
	}

	private class OptionList extends ContainerObjectSelectionList<Row> {
		OptionList(String category, int top, int bottom) {
			super(WorkerSettingsScreen.this.minecraft, WorkerSettingsScreen.this.width,
					bottom - top, top, CONTROL_HEIGHT + 5);

			for (SettingOption<S> option : settings.options()) {
				if (option.category().equals(category)) {
					addEntry(new Row(Component.translatable(option.labelKey()), controlFor(option),
							Component.translatable(option.tooltipKey())));
				}
			}

			addExtraRows(category, this::addEntry);

			if (WorkerSettings.DISPLAY.equals(category)) {
				addEntry(new Row(Component.translatable("config.villager_workstations.highlight_always_on"),
						highlightControl(),
						Component.translatable("config.villager_workstations.highlight_always_on.tooltip")));
			}
		}

		@Override
		public int getRowWidth() {
			return ROW_WIDTH;
		}

		@Override
		protected int scrollBarX() {
			return WorkerSettingsScreen.this.width / 2 + ROW_WIDTH / 2 + 8;
		}
	}

	/**
	 * One kind's share of the plantings. The slider carries a weight rather than a percentage, and
	 * reports the percentage that weight currently works out to beside it: the percentages depend
	 * on every other kind, so they all move when any one of them does.
	 */
	private class MixSlider extends AbstractSliderButton {
		private final SeedMix mix;
		private final Item item;
		private final List<Item> palette;

		MixSlider(SeedMix mix, Item item, List<Item> palette) {
			super(0, 0, CONTROL_WIDTH, CONTROL_HEIGHT, Component.empty(), fraction(mix.weight(item)));
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
			setMessage(Component.translatable("config.villager_workstations.seed_weight",
					mix.weight(item), mix.share(item, palette)));
		}

		@Override
		protected void applyValue() {
			mix.setWeight(item, (int) Math.round(
					Mth.lerp(value, SeedMix.MIN_WEIGHT, SeedMix.MAX_WEIGHT)));
		}

		/** Letting go rebuilds the tab, which is what brings the other kinds' shares up to date. */
		@Override
		public void onRelease(MouseButtonEvent event) {
			super.onRelease(event);
			refresh();
		}
	}

	/** A whole number slider that reports the value itself rather than a percentage. */
	private static class OptionSlider<S extends WorkerSettings<S>> extends AbstractSliderButton {
		private final SettingOption.Range<S> option;
		private final S settings;

		OptionSlider(SettingOption.Range<S> option, S settings) {
			super(0, 0, CONTROL_WIDTH, CONTROL_HEIGHT, Component.empty(), fraction(option, settings));
			this.option = option;
			this.settings = settings;
			updateMessage();
		}

		private static <S extends WorkerSettings<S>> double fraction(SettingOption.Range<S> option, S settings) {
			return (double) (option.get(settings) - option.min()) / (option.max() - option.min());
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.literal(String.valueOf(option.get(settings))));
		}

		@Override
		protected void applyValue() {
			option.set(settings, (int) Math.round(Mth.lerp(value, option.min(), option.max())));
		}
	}
}
