package dev.keyboard.breederscarecrow.client.screen;

import dev.keyboard.breederscarecrow.ModConfig;
import dev.keyboard.breederscarecrow.client.HighlightState;
import dev.keyboard.breederscarecrow.client.network.StationNetworkingClient;
import dev.keyboard.breederscarecrow.work.StationSettings;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.List;

/**
 * The settings screen for one station, opened by sneaking and using the block.
 *
 * <p>Rows are generated from {@link StationSettings#OPTIONS}, so a new setting appears here with no
 * work: it is listed under its own category with the label and description already wired to the
 * existing {@code config.breeder_scarecrow.*} translations.
 *
 * <p>Everything here belongs to the station that was clicked. The one exception is the highlight
 * toggle at the bottom, which is a local drawing preference and stays in the config file.
 */
public class StationSettingsScreen extends Screen {
	private static final int CONTROL_WIDTH = 100;
	private static final int CONTROL_HEIGHT = 20;
	private static final int ROW_WIDTH = 320;
	private static final int LIST_TOP = 52;
	private static final int LIST_BOTTOM_MARGIN = 44;

	private final BlockPos pos;
	/** Edited in place, and sent to the server when the screen is dismissed. */
	private final StationSettings settings;
	private Text workerStatus;

	public StationSettingsScreen(BlockPos pos, StationSettings settings, Text workerStatus) {
		super(Text.translatable("config.breeder_scarecrow.title"));
		this.pos = pos;
		this.settings = settings;
		this.workerStatus = workerStatus;
	}

	public void setWorkerStatus(Text status) {
		workerStatus = status;
	}

	@Override
	protected void init() {
		// Drawable, not merely selectable: a list added as a plain child is never rendered.
		addDrawableChild(new OptionList());

		addDrawableChild(ButtonWidget.builder(Text.translatable("config.breeder_scarecrow.worker_check"),
						button -> StationNetworkingClient.requestWorker(pos))
				.dimensions(width / 2 - 154, height - 32, 100, CONTROL_HEIGHT)
				.build());

		addDrawableChild(ButtonWidget.builder(Text.translatable("config.breeder_scarecrow.reset"),
						button -> resetToDefaults())
				.dimensions(width / 2 - 50, height - 32, 100, CONTROL_HEIGHT)
				.build());

		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close())
				.dimensions(width / 2 + 54, height - 32, 100, CONTROL_HEIGHT)
				.build());
	}

	private void resetToDefaults() {
		// The values the mod ships with rather than whatever is in the config file, so the button
		// means the same thing on a server whose file you have never seen.
		settings.copyFrom(StationSettings.builtInDefaults());
		clearAndInit();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);
		context.drawCenteredTextWithShadow(textRenderer,
				Text.translatable("config.breeder_scarecrow.per_station_note", pos.getX(), pos.getY(), pos.getZ()),
				width / 2, 26, 0x9A9A9A);
		context.drawCenteredTextWithShadow(textRenderer, workerStatus, width / 2, 38, 0xE0C060);
	}

	/** Closing saves, so leaving by Escape keeps the changes rather than quietly binning them. */
	@Override
	public void close() {
		StationNetworkingClient.saveSettings(pos, settings);
		super.close();
	}

	private static Text onOff(boolean value) {
		return value ? ScreenTexts.ON : ScreenTexts.OFF;
	}

	private ClickableWidget controlFor(StationSettings.Option option) {
		if (option instanceof StationSettings.Flag flag) {
			return ButtonWidget.builder(onOff(flag.get(settings)), button -> {
						flag.set(settings, !flag.get(settings));
						button.setMessage(onOff(flag.get(settings)));
					})
					.dimensions(0, 0, CONTROL_WIDTH, CONTROL_HEIGHT)
					.build();
		}

		return new IntSlider((StationSettings.Range) option, settings);
	}

	/**
	 * Writes to the local config rather than to the station, because whether the box is drawn is a
	 * question about this client and nothing to do with how the ranch is run.
	 */
	private ClickableWidget highlightControl() {
		ModConfig config = ModConfig.get();

		return ButtonWidget.builder(onOff(config.highlightAlwaysOn), button -> {
					config.highlightAlwaysOn = !config.highlightAlwaysOn;
					config.save();
					HighlightState.applyConfig();
					button.setMessage(onOff(config.highlightAlwaysOn));
				})
				.dimensions(0, 0, CONTROL_WIDTH, CONTROL_HEIGHT)
				.build();
	}

	private abstract class Row extends ElementListWidget.Entry<Row> {
	}

	/** A category name, standing in the list where a Cloth Config tab used to be. */
	private class HeaderRow extends Row {
		private final Text label;

		HeaderRow(Text label) {
			this.label = label;
		}

		@Override
		public List<? extends Element> children() {
			return List.of();
		}

		@Override
		public List<? extends Selectable> selectableChildren() {
			return List.of();
		}

		@Override
		public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight,
				int mouseX, int mouseY, boolean hovered, float tickDelta) {
			context.drawTextWithShadow(textRenderer, label.copy().formatted(Formatting.YELLOW), x,
					y + entryHeight - textRenderer.fontHeight - 3, 0xFFFFFF);
		}
	}

	/** A label on the left with its control on the right, the way vanilla's options screens read. */
	private class OptionRow extends Row {
		private final Text label;
		private final ClickableWidget control;

		OptionRow(Text label, ClickableWidget control, Text tooltip) {
			this.label = label;
			this.control = control;
			control.setTooltip(Tooltip.of(tooltip));
		}

		@Override
		public List<? extends Element> children() {
			return List.of(control);
		}

		@Override
		public List<? extends Selectable> selectableChildren() {
			return List.of(control);
		}

		@Override
		public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight,
				int mouseX, int mouseY, boolean hovered, float tickDelta) {
			context.drawTextWithShadow(textRenderer, label, x, y + (entryHeight - textRenderer.fontHeight) / 2,
					0xFFFFFF);
			control.setX(x + entryWidth - CONTROL_WIDTH);
			control.setY(y);
			control.render(context, mouseX, mouseY, tickDelta);
		}
	}

	private class OptionList extends ElementListWidget<Row> {
		OptionList() {
			super(StationSettingsScreen.this.client, StationSettingsScreen.this.width,
					StationSettingsScreen.this.height, LIST_TOP,
					StationSettingsScreen.this.height - LIST_BOTTOM_MARGIN, CONTROL_HEIGHT + 5);

			String category = null;

			for (StationSettings.Option option : StationSettings.OPTIONS) {
				if (!option.category().equals(category)) {
					category = option.category();
					addEntry(new HeaderRow(Text.translatable("config.breeder_scarecrow." + category)));
				}

				addEntry(new OptionRow(Text.translatable(option.labelKey()), controlFor(option),
						Text.translatable(option.tooltipKey())));
			}

			addEntry(new OptionRow(Text.translatable("config.breeder_scarecrow.highlight_always_on"),
					highlightControl(), Text.translatable("config.breeder_scarecrow.highlight_always_on.tooltip")));
		}

		@Override
		public int getRowWidth() {
			return ROW_WIDTH;
		}

		@Override
		protected int getScrollbarPositionX() {
			return StationSettingsScreen.this.width / 2 + ROW_WIDTH / 2 + 8;
		}
	}

	/** A whole number slider that reports the value itself rather than a percentage. */
	private static class IntSlider extends SliderWidget {
		private final StationSettings.Range option;
		private final StationSettings settings;

		IntSlider(StationSettings.Range option, StationSettings settings) {
			super(0, 0, CONTROL_WIDTH, CONTROL_HEIGHT, Text.empty(), fraction(option, settings));
			this.option = option;
			this.settings = settings;
			updateMessage();
		}

		private static double fraction(StationSettings.Range option, StationSettings settings) {
			return (double) (option.get(settings) - option.min()) / (option.max() - option.min());
		}

		@Override
		protected void updateMessage() {
			setMessage(Text.literal(String.valueOf(option.get(settings))));
		}

		@Override
		protected void applyValue() {
			option.set(settings, (int) Math.round(MathHelper.lerp(value, option.min(), option.max())));
		}
	}
}
