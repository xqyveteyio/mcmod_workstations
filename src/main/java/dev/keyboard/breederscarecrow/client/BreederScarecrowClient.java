package dev.keyboard.breederscarecrow.client;

import dev.keyboard.breederscarecrow.BreederScarecrowMod;
import dev.keyboard.breederscarecrow.client.network.StationNetworkingClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class BreederScarecrowClient implements ClientModInitializer {
	public static final KeyBinding TOGGLE_HIGHLIGHT = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.breeder_scarecrow.toggle_highlight",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_G,
			"category.breeder_scarecrow"));

	@Override
	public void onInitializeClient() {
		StationNetworkingClient.registerClientReceivers();

		BlockEntityRendererRegistry.register(BreederScarecrowMod.SCARECROW_BLOCK_ENTITY, StationBlockEntityRenderer::new);
		BuiltinItemRendererRegistry.INSTANCE.register(BreederScarecrowMod.SCARECROW_ITEM, new StationItemRenderer());
		EntityRendererRegistry.register(BreederScarecrowMod.RANCHER, RancherEntityRenderer::new);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (TOGGLE_HIGHLIGHT.wasPressed()) {
				boolean enabled = HighlightState.toggle();

				if (client.player != null) {
					client.player.sendMessage(Text.translatable(enabled
							? "message.breeder_scarecrow.highlight_on"
							: "message.breeder_scarecrow.highlight_off"), true);
				}
			}
		});
	}
}
