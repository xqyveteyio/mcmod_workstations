package dev.keyboard.workstations.fabric;

import dev.keyboard.workstations.client.WorkstationsClient;
import net.fabricmc.api.ClientModInitializer;

public class WorkstationsClientFabric implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		WorkstationsClient.init();
	}
}
