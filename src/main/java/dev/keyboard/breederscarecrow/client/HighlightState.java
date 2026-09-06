package dev.keyboard.breederscarecrow.client;

import dev.keyboard.breederscarecrow.BreederScarecrowMod;
import dev.keyboard.breederscarecrow.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Decides whether the debug pen highlight is drawn. Always on while a scarecrow is held,
 * plus a manual toggle for looking at the pen from outside.
 */
public final class HighlightState {
	private static Boolean forced;

	private HighlightState() {
	}

	public static boolean toggle() {
		forced = !isForced();
		return forced;
	}

	public static boolean shouldRender() {
		if (isForced()) {
			return true;
		}

		ClientPlayerEntity player = MinecraftClient.getInstance().player;

		if (player == null) {
			return false;
		}

		return player.getMainHandStack().isOf(BreederScarecrowMod.SCARECROW_ITEM)
				|| player.getOffHandStack().isOf(BreederScarecrowMod.SCARECROW_ITEM);
	}

	private static boolean isForced() {
		if (forced == null) {
			forced = ModConfig.get().highlightAlwaysOn;
		}

		return forced;
	}
}
