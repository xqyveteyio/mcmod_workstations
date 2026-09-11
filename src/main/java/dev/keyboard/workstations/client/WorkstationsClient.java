package dev.keyboard.workstations.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.keyboard.workstations.WorkstationsMod;
import dev.keyboard.workstations.client.network.StationNetworkingClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class WorkstationsClient implements ClientModInitializer {
	public static final KeyMapping.Category KEYS = KeyMapping.Category.register(
			WorkstationsMod.id("keyboard_workstations"));

	public static final KeyMapping TOGGLE_HIGHLIGHT = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.keyboard_workstations.toggle_highlight",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_G,
			KEYS));

	@Override
	public void onInitializeClient() {
		StationNetworkingClient.registerClientReceivers();
		StationItemModels.register();

		BlockEntityRendererRegistry.register(WorkstationsMod.RANCH_BLOCK_ENTITY, StationBlockEntityRenderer::new);
		EntityRendererRegistry.register(WorkstationsMod.RANCHER, RancherEntityRenderer::new);

		BlockEntityRendererRegistry.register(WorkstationsMod.FARM_BLOCK_ENTITY, FarmBlockEntityRenderer::new);
		EntityRendererRegistry.register(WorkstationsMod.FARMER, FarmerEntityRenderer::new);

		BlockEntityRendererRegistry.register(WorkstationsMod.LUMBER_BLOCK_ENTITY, LumberBlockEntityRenderer::new);
		EntityRendererRegistry.register(WorkstationsMod.LUMBERJACK, LumberjackEntityRenderer::new);

		BlockEntityRendererRegistry.register(WorkstationsMod.SEED_BOX_BLOCK_ENTITY, SeedBoxBlockEntityRenderer::new);
		BlockEntityRendererRegistry.register(WorkstationsMod.FEED_BARREL_BLOCK_ENTITY, FeedBarrelBlockEntityRenderer::new);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (TOGGLE_HIGHLIGHT.consumeClick()) {
				boolean enabled = HighlightState.toggle();

				if (client.player != null) {
					client.player.sendOverlayMessage(Component.translatable(enabled
							? "message.keyboard_workstations.highlight_on"
							: "message.keyboard_workstations.highlight_off"));
				}
			}
		});
	}
}
