package dev.keyboard.workstations.neoforge;

import dev.keyboard.workstations.WorkstationsMod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(WorkstationsMod.MOD_ID)
public class WorkstationsNeoForge {
	public WorkstationsNeoForge(IEventBus modBus) {
		WorkstationsMod.init();
	}
}
