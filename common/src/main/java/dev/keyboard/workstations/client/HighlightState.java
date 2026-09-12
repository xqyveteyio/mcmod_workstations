package dev.keyboard.workstations.client;

import dev.keyboard.workstations.WorkstationsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import dev.keyboard.workstations.ModConfig;

/**
 * Decides whether the debug work area highlight is drawn. Always on while a station block of either
 * kind is held, plus a manual toggle for looking at the area from outside.
 */
public final class HighlightState {
	private static Boolean forced;

	private HighlightState() {
	}

	public static boolean toggle() {
		forced = !isForced();
		return forced;
	}

	/** Drops the manual toggle so the value from the config screen takes effect right away. */
	public static void applyConfig() {
		forced = ModConfig.get().highlightAlwaysOn;
	}

	public static boolean shouldRender() {
		if (isForced()) {
			return true;
		}

		LocalPlayer player = Minecraft.getInstance().player;

		if (player == null) {
			return false;
		}

		return isStation(player.getMainHandItem()) || isStation(player.getOffhandItem());
	}

	private static boolean isStation(ItemStack stack) {
		return stack.is(WorkstationsMod.RANCH_ITEM.get())
				|| stack.is(WorkstationsMod.FARM_ITEM.get())
				|| stack.is(WorkstationsMod.LUMBER_ITEM.get());
	}

	private static boolean isForced() {
		if (forced == null) {
			forced = ModConfig.get().highlightAlwaysOn;
		}

		return forced;
	}
}
