package dev.keyboard.workstations.neoforge;

import dev.keyboard.workstations.WorkstationsMod;
import dev.keyboard.workstations.client.WorkstationsClient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

public final class WorkstationsNeoForgeClient {
	private WorkstationsNeoForgeClient() {
	}

	static void register(IEventBus modBus) {
		WorkstationsClient.registerKeyMappings();
		WorkstationsClient.registerEntityRenderers();
		modBus.addListener(WorkstationsNeoForgeClient::onClientSetup);
		modBus.addListener(WorkstationsNeoForgeClient::registerClientExtensions);
	}

	private static void onClientSetup(FMLClientSetupEvent event) {
		WorkstationsClient.init();
	}

	private static void registerClientExtensions(RegisterClientExtensionsEvent event) {
		event.registerItem(StationItemExtensions.INSTANCE,
				WorkstationsMod.RANCH_ITEM.get(),
				WorkstationsMod.LUMBER_ITEM.get(),
				WorkstationsMod.SEED_BOX_ITEM.get(),
				WorkstationsMod.FEED_BARREL_ITEM.get());
	}
}
