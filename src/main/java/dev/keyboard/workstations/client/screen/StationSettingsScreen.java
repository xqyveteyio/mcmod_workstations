package dev.keyboard.workstations.client.screen;

import dev.keyboard.workstations.WorkstationsMod;
import dev.keyboard.workstations.client.network.StationNetworkingClient;
import dev.keyboard.workstations.work.StationSettings;
import net.minecraft.core.BlockPos;

/** The ranch station's settings, which are entirely described by its option list. */
public class StationSettingsScreen extends WorkerSettingsScreen<StationSettings> {
	public StationSettingsScreen(BlockPos pos, StationSettings settings) {
		super(WorkstationsMod.RANCH_BLOCK.getName(), pos, settings);
	}

	@Override
	protected void save() {
		StationNetworkingClient.saveRanchSettings(pos, settings);
	}
}
