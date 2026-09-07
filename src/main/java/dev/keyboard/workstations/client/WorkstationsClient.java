package dev.keyboard.workstations.client;

import dev.keyboard.workstations.WorkstationsMod;
import dev.keyboard.workstations.client.network.StationNetworkingClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class WorkstationsClient implements ClientModInitializer {
	public static final KeyBinding TOGGLE_HIGHLIGHT = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.workstations.toggle_highlight",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_G,
			"category.workstations"));

	@Override
	public void onInitializeClient() {
		StationNetworkingClient.registerClientReceivers();

		BlockEntityRendererRegistry.register(WorkstationsMod.SCARECROW_BLOCK_ENTITY, StationBlockEntityRenderer::new);
		BuiltinItemRendererRegistry.INSTANCE.register(WorkstationsMod.SCARECROW_ITEM, new StationItemRenderer());
		EntityRendererRegistry.register(WorkstationsMod.RANCHER, RancherEntityRenderer::new);

		// The sprouts on the tabletop are a crop sprite, and nearly all of that texture is
		// transparent. On the default solid layer the alpha is simply ignored, which paints the
		// planes as black walls, so the block needs the same cutout layer vanilla gives its crops.
		BlockRenderLayerMap.INSTANCE.putBlock(WorkstationsMod.FARM_BLOCK, RenderLayer.getCutout());
		BlockEntityRendererRegistry.register(WorkstationsMod.FARM_BLOCK_ENTITY, FarmBlockEntityRenderer::new);
		EntityRendererRegistry.register(WorkstationsMod.FARMER, FarmerEntityRenderer::new);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (TOGGLE_HIGHLIGHT.wasPressed()) {
				boolean enabled = HighlightState.toggle();

				if (client.player != null) {
					client.player.sendMessage(Text.translatable(enabled
							? "message.workstations.highlight_on"
							: "message.workstations.highlight_off"), true);
				}
			}
		});
	}
}
