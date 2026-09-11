package dev.keyboard.workstations.client;

import dev.keyboard.workstations.Mc;

import dev.keyboard.workstations.WorkstationsMod;
import dev.keyboard.workstations.client.network.StationNetworkingClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
//? if >=1.17 {
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
//?} else {
/* import net.fabricmc.fabric.api.client.rendereregistry.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendereregistry.v1.EntityRendererRegistry; */
//?}
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class WorkstationsClient implements ClientModInitializer {
	public static final KeyBinding TOGGLE_HIGHLIGHT = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.keyboard_workstations.toggle_highlight",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_G,
			"category.keyboard_workstations"));

	@Override
	public void onInitializeClient() {
		StationNetworkingClient.registerClientReceivers();

		//? if >=1.17 {
		BlockEntityRendererRegistry.register(WorkstationsMod.RANCH_BLOCK_ENTITY, StationBlockEntityRenderer::new);
		BuiltinItemRendererRegistry.INSTANCE.register(WorkstationsMod.RANCH_ITEM, new StationItemRenderer());
		EntityRendererRegistry.register(WorkstationsMod.RANCHER, RancherEntityRenderer::new);

		BlockRenderLayerMap.INSTANCE.putBlock(WorkstationsMod.FARM_BLOCK, RenderLayer.getCutout());
		BlockEntityRendererRegistry.register(WorkstationsMod.FARM_BLOCK_ENTITY, FarmBlockEntityRenderer::new);
		EntityRendererRegistry.register(WorkstationsMod.FARMER, FarmerEntityRenderer::new);

		BlockEntityRendererRegistry.register(WorkstationsMod.LUMBER_BLOCK_ENTITY, LumberBlockEntityRenderer::new);
		BuiltinItemRendererRegistry.INSTANCE.register(WorkstationsMod.LUMBER_ITEM, new LumberItemRenderer());
		EntityRendererRegistry.register(WorkstationsMod.LUMBERJACK, LumberjackEntityRenderer::new);

		BlockEntityRendererRegistry.register(WorkstationsMod.SEED_BOX_BLOCK_ENTITY,
				ctx -> new StationChestBlockEntityRenderer<>(ctx, WorkstationsMod.id("textures/entity/seed_box.png")));
		BuiltinItemRendererRegistry.INSTANCE.register(WorkstationsMod.SEED_BOX_ITEM,
				new StationChestItemRenderer(WorkstationsMod.SEED_BOX_BLOCK,
						WorkstationsMod.id("textures/entity/seed_box.png")));

		BlockEntityRendererRegistry.register(WorkstationsMod.FEED_BARREL_BLOCK_ENTITY,
				ctx -> new StationChestBlockEntityRenderer<>(ctx, WorkstationsMod.id("textures/entity/feed_barrel.png")));
		BuiltinItemRendererRegistry.INSTANCE.register(WorkstationsMod.FEED_BARREL_ITEM,
				new StationChestItemRenderer(WorkstationsMod.FEED_BARREL_BLOCK,
						WorkstationsMod.id("textures/entity/feed_barrel.png")));
		//?} else {
		/* BlockEntityRendererRegistry.INSTANCE.register(WorkstationsMod.RANCH_BLOCK_ENTITY, StationBlockEntityRenderer::new);
		BuiltinItemRendererRegistry.INSTANCE.register(WorkstationsMod.RANCH_ITEM, new StationItemRenderer());
		EntityRendererRegistry.INSTANCE.register(WorkstationsMod.RANCHER,
				(dispatcher, context) -> new RancherEntityRenderer(dispatcher));

		BlockRenderLayerMap.INSTANCE.putBlock(WorkstationsMod.FARM_BLOCK, RenderLayer.getCutout());
		BlockEntityRendererRegistry.INSTANCE.register(WorkstationsMod.FARM_BLOCK_ENTITY, FarmBlockEntityRenderer::new);
		EntityRendererRegistry.INSTANCE.register(WorkstationsMod.FARMER,
				(dispatcher, context) -> new FarmerEntityRenderer(dispatcher));

		BlockEntityRendererRegistry.INSTANCE.register(WorkstationsMod.LUMBER_BLOCK_ENTITY, LumberBlockEntityRenderer::new);
		BuiltinItemRendererRegistry.INSTANCE.register(WorkstationsMod.LUMBER_ITEM, new LumberItemRenderer());
		EntityRendererRegistry.INSTANCE.register(WorkstationsMod.LUMBERJACK,
				(dispatcher, context) -> new LumberjackEntityRenderer(dispatcher));

		BlockEntityRendererRegistry.INSTANCE.register(WorkstationsMod.SEED_BOX_BLOCK_ENTITY,
				dispatcher -> new StationChestBlockEntityRenderer<>(dispatcher, WorkstationsMod.id("textures/entity/seed_box.png")));
		BuiltinItemRendererRegistry.INSTANCE.register(WorkstationsMod.SEED_BOX_ITEM,
				new StationChestItemRenderer(WorkstationsMod.SEED_BOX_BLOCK,
						WorkstationsMod.id("textures/entity/seed_box.png")));

		BlockEntityRendererRegistry.INSTANCE.register(WorkstationsMod.FEED_BARREL_BLOCK_ENTITY,
				dispatcher -> new StationChestBlockEntityRenderer<>(dispatcher, WorkstationsMod.id("textures/entity/feed_barrel.png")));
		BuiltinItemRendererRegistry.INSTANCE.register(WorkstationsMod.FEED_BARREL_ITEM,
				new StationChestItemRenderer(WorkstationsMod.FEED_BARREL_BLOCK,
						WorkstationsMod.id("textures/entity/feed_barrel.png"))); */
		//?}

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (TOGGLE_HIGHLIGHT.wasPressed()) {
				boolean enabled = HighlightState.toggle();

				if (client.player != null) {
					client.player.sendMessage(Mc.translatable(enabled
							? "message.keyboard_workstations.highlight_on"
							: "message.keyboard_workstations.highlight_off"), true);
				}
			}
		});
	}
}
