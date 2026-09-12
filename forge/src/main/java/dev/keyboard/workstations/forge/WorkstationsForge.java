package dev.keyboard.workstations.forge;

import me.shedaniel.architectury.platform.forge.EventBuses;
import dev.keyboard.workstations.WorkstationsMod;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(WorkstationsMod.MOD_ID)
public class WorkstationsForge {
	public WorkstationsForge() {
		EventBuses.registerModEventBus(WorkstationsMod.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());
		WorkstationsMod.init();
	}
}
