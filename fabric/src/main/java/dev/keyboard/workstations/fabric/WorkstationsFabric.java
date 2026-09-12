package dev.keyboard.workstations.fabric;

import dev.keyboard.workstations.WorkstationsMod;
import net.fabricmc.api.ModInitializer;

public class WorkstationsFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		WorkstationsMod.init();
	}
}
