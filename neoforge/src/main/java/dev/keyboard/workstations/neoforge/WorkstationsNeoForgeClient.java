package dev.keyboard.workstations.neoforge;

import dev.keyboard.workstations.WorkstationsMod;
import dev.keyboard.workstations.client.WorkstationsClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@Mod(value = WorkstationsMod.MOD_ID, dist = Dist.CLIENT)
public class WorkstationsNeoForgeClient {
	public WorkstationsNeoForgeClient(IEventBus modBus) {
		WorkstationsClient.registerKeyMappings();
		WorkstationsClient.registerEntityRenderers();
		modBus.addListener(WorkstationsNeoForgeClient::onClientSetup);
	}

	private static void onClientSetup(FMLClientSetupEvent event) {
		WorkstationsClient.init();
	}
}
