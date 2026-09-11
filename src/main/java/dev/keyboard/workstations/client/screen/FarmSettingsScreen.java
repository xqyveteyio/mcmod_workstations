package dev.keyboard.workstations.client.screen;

import dev.keyboard.workstations.Mc;

import dev.keyboard.workstations.WorkstationsMod;
import dev.keyboard.workstations.client.network.StationNetworkingClient;
import dev.keyboard.workstations.work.FarmSettings;
import net.minecraft.item.Item;
import net.minecraft.text.Text;
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
			add.accept(new Row(Mc.translatable("config.keyboard_workstations.rescan_plots"),
					button(0, 0, CONTROL_WIDTH, CONTROL_HEIGHT,
							Mc.translatable("config.keyboard_workstations.rescan_plots.action"),
							ignored -> surveyAndLeave()),
					Mc.translatable("config.keyboard_workstations.rescan_plots.tooltip")));
			return;
		}

		if (FarmSettings.SEEDS.equals(category)) {
			addMixRows(settings.seedMix, palette,
					"config.keyboard_workstations.no_seeds",
					"config.keyboard_workstations.seed_weight.tooltip", add);
		}
	}
}
