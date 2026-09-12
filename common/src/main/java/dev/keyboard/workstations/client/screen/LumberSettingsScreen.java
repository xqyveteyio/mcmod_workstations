package dev.keyboard.workstations.client.screen;

import dev.keyboard.workstations.WorkstationsMod;
import dev.keyboard.workstations.client.network.StationNetworkingClient;
import dev.keyboard.workstations.work.LumberSettings;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.function.Consumer;

/**
 * The lumber station's settings. Everything the farm screen does for its mix tab, pointed at
 * saplings rather than seeds, and without the field-survey button: a wood is found each scan,
 * so there is nothing to retake by hand.
 */
public class LumberSettingsScreen extends WorkerSettingsScreen<LumberSettings> {
	/** The saplings in the station's stores when the screen was opened. */
	private final List<Item> palette;

	public LumberSettingsScreen(BlockPos pos, LumberSettings settings, List<Item> palette) {
		super(new ItemStack(WorkstationsMod.LUMBER_BLOCK.get()).getName(), pos, settings);
		this.palette = palette;
	}

	@Override
	protected void save() {
		StationNetworkingClient.saveLumberSettings(pos, settings);
	}

	@Override
	protected void addExtraRows(String category, Consumer<Row> add) {
		if (LumberSettings.SAPLINGS.equals(category)) {
			addMixRows(settings.saplingMix, palette,
					"config.villager_workstations.no_saplings",
					"config.villager_workstations.sapling_weight.tooltip", add);
		}
	}
}
