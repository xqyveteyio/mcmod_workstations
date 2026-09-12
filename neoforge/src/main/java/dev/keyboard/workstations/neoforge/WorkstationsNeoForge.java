package dev.keyboard.workstations.neoforge;

import dev.keyboard.workstations.WorkstationsMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLLoader;

@Mod(WorkstationsMod.MOD_ID)
public class WorkstationsNeoForge {
	public WorkstationsNeoForge(IEventBus modBus) {
		WorkstationsMod.init();

		if (FMLLoader.getDist() == Dist.CLIENT) {
			WorkstationsNeoForgeClient.register(modBus);
		}
	}
}
