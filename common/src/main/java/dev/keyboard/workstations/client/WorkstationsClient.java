package dev.keyboard.workstations.client;

import me.shedaniel.architectury.event.events.client.ClientTickEvent;
import me.shedaniel.architectury.registry.BlockEntityRenderers;
import me.shedaniel.architectury.registry.KeyBindings;
import me.shedaniel.architectury.registry.RenderTypes;
import me.shedaniel.architectury.registry.entity.EntityRenderers;
import dev.keyboard.workstations.WorkstationsMod;
import dev.keyboard.workstations.client.network.StationNetworkingClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.TranslatableText;
import org.lwjgl.glfw.GLFW;

public class WorkstationsClient {
	public static final KeyBinding TOGGLE_HIGHLIGHT = new KeyBinding(
			"key.villager_workstations.toggle_highlight",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_G,
			"category.villager_workstations");

	public static void init() {
		KeyBindings.registerKeyBinding(TOGGLE_HIGHLIGHT);
		StationNetworkingClient.registerClientReceivers();

		EntityRenderers.register(WorkstationsMod.RANCHER.get(), RancherEntityRenderer::new);
		EntityRenderers.register(WorkstationsMod.FARMER.get(), FarmerEntityRenderer::new);
		EntityRenderers.register(WorkstationsMod.LUMBERJACK.get(), LumberjackEntityRenderer::new);

		BlockEntityRenderers.registerRenderer(WorkstationsMod.RANCH_BLOCK_ENTITY.get(), StationBlockEntityRenderer::new);
		BuiltinItemRenderers.register(WorkstationsMod.RANCH_ITEM.get(), new StationItemRenderer());

		// The sprouts on the tabletop are a crop sprite, and nearly all of that texture is
		// transparent. On the default solid layer the alpha is simply ignored, which paints the
		// planes as black walls, so the block needs the same cutout layer vanilla gives its crops.
		RenderTypes.register(RenderLayer.getCutout(), WorkstationsMod.FARM_BLOCK.get());
		BlockEntityRenderers.registerRenderer(WorkstationsMod.FARM_BLOCK_ENTITY.get(), FarmBlockEntityRenderer::new);

		BlockEntityRenderers.registerRenderer(WorkstationsMod.LUMBER_BLOCK_ENTITY.get(), LumberBlockEntityRenderer::new);
		BuiltinItemRenderers.register(WorkstationsMod.LUMBER_ITEM.get(), new LumberItemRenderer());

		BlockEntityRenderers.registerRenderer(WorkstationsMod.SEED_BOX_BLOCK_ENTITY.get(), SeedBoxBlockEntityRenderer::new);
		BuiltinItemRenderers.register(WorkstationsMod.SEED_BOX_ITEM.get(), new SeedBoxItemRenderer());

		BlockEntityRenderers.registerRenderer(WorkstationsMod.FEED_BARREL_BLOCK_ENTITY.get(), FeedBarrelBlockEntityRenderer::new);
		BuiltinItemRenderers.register(WorkstationsMod.FEED_BARREL_ITEM.get(), new FeedBarrelItemRenderer());

		ClientTickEvent.CLIENT_POST.register(client -> {
			while (TOGGLE_HIGHLIGHT.wasPressed()) {
				boolean enabled = HighlightState.toggle();

				if (client.player != null) {
					client.player.sendMessage(new TranslatableText(enabled
							? "message.villager_workstations.highlight_on"
							: "message.villager_workstations.highlight_off"), true);
				}
			}
		});
	}
}
