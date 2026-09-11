package dev.keyboard.workstations.forge;

import dev.architectury.platform.forge.EventBuses;
import dev.keyboard.workstations.WorkstationsMod;
import dev.keyboard.workstations.client.WorkstationsClient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(WorkstationsMod.MOD_ID)
public class WorkstationsForge {
	public WorkstationsForge() {
		EventBuses.registerModEventBus(WorkstationsMod.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());
		WorkstationsMod.init();
		DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSetup.registerClient());
	}

	static final class ClientSetup {
		static void registerClient() {
			WorkstationsClient.registerKeyMappings();
			WorkstationsClient.registerEntityRenderers();
		}
	}
}

