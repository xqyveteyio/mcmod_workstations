package dev.keyboard.workstations.forge;

import dev.keyboard.workstations.WorkstationsMod;
import dev.keyboard.workstations.client.WorkstationsClient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = WorkstationsMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WorkstationsForgeClient {
	private WorkstationsForgeClient() {
	}

	@SubscribeEvent
	public static void onClientSetup(FMLClientSetupEvent event) {
		WorkstationsClient.init();
	}
}
