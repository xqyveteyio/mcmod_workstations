package dev.keyboard.workstations.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import dev.architectury.registry.client.level.entity.EntityRendererRegistry;
import dev.architectury.registry.client.rendering.BlockEntityRendererRegistry;
import dev.keyboard.workstations.WorkstationsMod;
import dev.keyboard.workstations.client.network.StationNetworkingClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class WorkstationsClient {
	public static final KeyMapping.Category KEYS = KeyMapping.Category.register(
			WorkstationsMod.id("villager_workstations"));

	public static final KeyMapping TOGGLE_HIGHLIGHT = new KeyMapping(
			"key.villager_workstations.toggle_highlight",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_G,
			KEYS);

	public static void registerKeyMappings() {
		KeyMappingRegistry.register(TOGGLE_HIGHLIGHT);
	}

	public static void registerEntityRenderers() {
		EntityRendererRegistry.register(WorkstationsMod.RANCHER, RancherEntityRenderer::new);
		EntityRendererRegistry.register(WorkstationsMod.FARMER, FarmerEntityRenderer::new);
		EntityRendererRegistry.register(WorkstationsMod.LUMBERJACK, LumberjackEntityRenderer::new);
	}

	public static void init() {
		StationNetworkingClient.registerClientReceivers();
		StationItemModels.register();

		BlockEntityRendererRegistry.register(WorkstationsMod.RANCH_BLOCK_ENTITY.get(), StationBlockEntityRenderer::new);
		BlockEntityRendererRegistry.register(WorkstationsMod.FARM_BLOCK_ENTITY.get(), FarmBlockEntityRenderer::new);
		BlockEntityRendererRegistry.register(WorkstationsMod.LUMBER_BLOCK_ENTITY.get(), LumberBlockEntityRenderer::new);
		BlockEntityRendererRegistry.register(WorkstationsMod.SEED_BOX_BLOCK_ENTITY.get(), SeedBoxBlockEntityRenderer::new);
		BlockEntityRendererRegistry.register(WorkstationsMod.FEED_BARREL_BLOCK_ENTITY.get(), FeedBarrelBlockEntityRenderer::new);

		ClientTickEvent.CLIENT_POST.register(client -> {
			while (TOGGLE_HIGHLIGHT.consumeClick()) {
				boolean enabled = HighlightState.toggle();

				if (client.player != null) {
					client.player.sendOverlayMessage(Component.translatable(enabled
							? "message.villager_workstations.highlight_on"
							: "message.villager_workstations.highlight_off"));
				}
			}
		});
	}
}
