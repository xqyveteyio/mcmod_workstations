package dev.keyboard.workstations.client.screen;

import dev.keyboard.workstations.WorkstationsMod;
import dev.keyboard.workstations.client.network.StationNetworkingClient;
import dev.keyboard.workstations.work.FarmSettings;
import dev.keyboard.workstations.work.SeedMix;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.item.Item;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.function.Consumer;

/**
 * The farm station's settings. Everything the ranch screen does, plus a tab of planting ratios
 * built from the seeds the station is actually holding.
 *
 * <p>The seed list cannot come from the block the way the rest of the settings do, because a
 * station deliberately does not send its contents to the client. The server puts the list in the
 * packet that opens this screen instead, so the rows are the seeds as the server sees them.
 */
public class FarmSettingsScreen extends WorkerSettingsScreen<FarmSettings> {
	/** The seeds in the station's container when the screen was opened. */
	private final List<Item> palette;

	public FarmSettingsScreen(BlockPos pos, FarmSettings settings, List<Item> palette) {
		super(WorkstationsMod.FARM_BLOCK.getName(), pos, settings);
		this.palette = palette;
	}

	@Override
	protected void save() {
		StationNetworkingClient.saveFarmSettings(pos, settings);
	}

	/**
	 * Surveys the field and gets out of the way, so the count comes up over the hotbar with nothing
	 * covering the ground it is talking about.
	 *
	 * <p>Saved before the survey rather than on the way out. The area is what the survey reads, so
	 * a radius widened in this very screen has to reach the station first or the sweep would go by
	 * the old one and report a number that does not match what was just asked for.
	 */
	private void surveyAndLeave() {
		save();
		StationNetworkingClient.rescanPlots(pos);
		dismiss();
	}

	@Override
	protected void addExtraRows(String category, Consumer<Row> add) {
		if (FarmSettings.FIELD.equals(category)) {
			add.accept(new Row(Text.translatable("config.workstations.rescan_plots"),
					ButtonWidget.builder(Text.translatable("config.workstations.rescan_plots.action"),
									button -> surveyAndLeave())
							.dimensions(0, 0, CONTROL_WIDTH, CONTROL_HEIGHT)
							.build(),
					Text.translatable("config.workstations.rescan_plots.tooltip")));
			return;
		}

		if (!FarmSettings.SEEDS.equals(category)) {
			return;
		}

		// An empty container is the usual reason this tab looks broken, so it says so rather than
		// showing nothing at all.
		if (palette.isEmpty()) {
			add.accept(new Row(Text.translatable("config.workstations.no_seeds")));
			return;
		}

		for (Item seed : palette) {
			add.accept(new Row(seed.getName().copy(), new SeedSlider(seed),
					Text.translatable("config.workstations.seed_weight.tooltip")));
		}
	}

	/**
	 * One seed's share of the field. The slider carries a weight rather than a percentage, and
	 * reports the percentage that weight currently works out to beside it: the percentages depend
	 * on every other seed, so they all move when any one of them does.
	 */
	private class SeedSlider extends SliderWidget {
		private final Item seed;

		SeedSlider(Item seed) {
			super(0, 0, CONTROL_WIDTH, CONTROL_HEIGHT, Text.empty(),
					fraction(settings.seedMix.weight(seed)));
			this.seed = seed;
			updateMessage();
		}

		private static double fraction(int weight) {
			return (double) (weight - SeedMix.MIN_WEIGHT) / (SeedMix.MAX_WEIGHT - SeedMix.MIN_WEIGHT);
		}

		@Override
		protected void updateMessage() {
			setMessage(Text.translatable("config.workstations.seed_weight",
					settings.seedMix.weight(seed), settings.seedMix.share(seed, palette)));
		}

		@Override
		protected void applyValue() {
			settings.seedMix.setWeight(seed, (int) Math.round(
					MathHelper.lerp(value, SeedMix.MIN_WEIGHT, SeedMix.MAX_WEIGHT)));
		}

		/** Letting go rebuilds the tab, which is what brings the other seeds' shares up to date. */
		@Override
		public void onRelease(double mouseX, double mouseY) {
			super.onRelease(mouseX, mouseY);
			refresh();
		}
	}
}
