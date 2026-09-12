package dev.keyboard.workstations.neoforge;

import dev.keyboard.workstations.client.WorkstationsClient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

public final class WorkstationsNeoForgeClient {
	private WorkstationsNeoForgeClient() {
	}

	static void register(IEventBus modBus) {
		WorkstationsClient.registerKeyMappings();
		WorkstationsClient.registerEntityRenderers();
		modBus.addListener(WorkstationsNeoForgeClient::onClientSetup);
	}

	private static void onClientSetup(FMLClientSetupEvent event) {
		WorkstationsClient.init();
	}
}
