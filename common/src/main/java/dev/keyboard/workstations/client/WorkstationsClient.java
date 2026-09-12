package dev.keyboard.workstations.client;

import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import dev.architectury.registry.client.level.entity.EntityRendererRegistry;
import dev.architectury.registry.client.rendering.BlockEntityRendererRegistry;
import dev.architectury.registry.client.rendering.RenderTypeRegistry;
import dev.keyboard.workstations.WorkstationsMod;
import dev.keyboard.workstations.client.network.StationNetworkingClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class WorkstationsClient {
	public static final KeyBinding TOGGLE_HIGHLIGHT = new KeyBinding(
			"key.villager_workstations.toggle_highlight",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_G,
			"category.villager_workstations");

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

		BlockEntityRendererRegistry.register(WorkstationsMod.RANCH_BLOCK_ENTITY.get(), StationBlockEntityRenderer::new);
		BuiltinItemRenderers.register(WorkstationsMod.RANCH_ITEM.get(), new StationItemRenderer());

		// The sprouts on the tabletop are a crop sprite, and nearly all of that texture is
		// transparent. On the default solid layer the alpha is simply ignored, which paints the
		// planes as black walls, so the block needs the same cutout layer vanilla gives its crops.
		RenderTypeRegistry.register(RenderLayer.getCutout(), WorkstationsMod.FARM_BLOCK.get());
		BlockEntityRendererRegistry.register(WorkstationsMod.FARM_BLOCK_ENTITY.get(), FarmBlockEntityRenderer::new);

		BlockEntityRendererRegistry.register(WorkstationsMod.LUMBER_BLOCK_ENTITY.get(), LumberBlockEntityRenderer::new);
		BuiltinItemRenderers.register(WorkstationsMod.LUMBER_ITEM.get(), new LumberItemRenderer());

		BlockEntityRendererRegistry.register(WorkstationsMod.SEED_BOX_BLOCK_ENTITY.get(),
				ctx -> new StationChestBlockEntityRenderer<>(ctx, WorkstationsMod.id("textures/entity/seed_box.png")));
		BuiltinItemRenderers.register(WorkstationsMod.SEED_BOX_ITEM.get(),
				new StationChestItemRenderer(WorkstationsMod.SEED_BOX_BLOCK.get(),
						WorkstationsMod.id("textures/entity/seed_box.png")));

		BlockEntityRendererRegistry.register(WorkstationsMod.FEED_BARREL_BLOCK_ENTITY.get(),
				ctx -> new StationChestBlockEntityRenderer<>(ctx, WorkstationsMod.id("textures/entity/feed_barrel.png")));
		BuiltinItemRenderers.register(WorkstationsMod.FEED_BARREL_ITEM.get(),
				new StationChestItemRenderer(WorkstationsMod.FEED_BARREL_BLOCK.get(),
						WorkstationsMod.id("textures/entity/feed_barrel.png")));

		ClientTickEvent.CLIENT_POST.register(client -> {
			while (TOGGLE_HIGHLIGHT.wasPressed()) {
				boolean enabled = HighlightState.toggle();

				if (client.player != null) {
					client.player.sendMessage(Text.translatable(enabled
							? "message.villager_workstations.highlight_on"
							: "message.villager_workstations.highlight_off"), true);
				}
			}
		});
	}
}
