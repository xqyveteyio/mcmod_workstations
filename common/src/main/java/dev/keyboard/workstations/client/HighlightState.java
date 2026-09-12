package dev.keyboard.workstations.client;

import dev.keyboard.workstations.WorkstationsMod;
import dev.keyboard.workstations.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;

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

		ClientPlayerEntity player = MinecraftClient.getInstance().player;

		if (player == null) {
			return false;
		}

		return isStation(player.getMainHandStack()) || isStation(player.getOffHandStack());
	}

	private static boolean isStation(ItemStack stack) {
		return stack.getItem() == WorkstationsMod.RANCH_ITEM.get()
				|| stack.getItem() == WorkstationsMod.FARM_ITEM.get()
				|| stack.getItem() == WorkstationsMod.LUMBER_ITEM.get();
	}

	private static boolean isForced() {
		if (forced == null) {
			forced = ModConfig.get().highlightAlwaysOn;
		}

		return forced;
	}
}
