package dev.keyboard.workstations;

import dev.keyboard.workstations.block.FarmBlock;
import dev.keyboard.workstations.block.FarmBlockEntity;
import dev.keyboard.workstations.block.MilkBarrelBlock;
import dev.keyboard.workstations.block.MilkBarrelBlockEntity;
import dev.keyboard.workstations.block.RanchBlock;
import dev.keyboard.workstations.block.RanchBlockEntity;
import dev.keyboard.workstations.block.WorkStationBlockEntity;
import dev.keyboard.workstations.entity.FarmerEntity;
import dev.keyboard.workstations.entity.RancherEntity;
import dev.keyboard.workstations.item.UniversalFeedItem;
import dev.keyboard.workstations.network.StationNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkstationsMod implements ModInitializer {
	public static final String MOD_ID = "workstations";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final Identifier RANCH_ID = id("ranch_station");
	public static final Identifier RANCHER_ID = id("rancher");
	public static final Identifier FARM_ID = id("farm_station");
	public static final Identifier FARMER_ID = id("farmer");
	public static final Identifier MILK_BARREL_ID = id("milk_barrel");
	public static final Identifier UNIVERSAL_FEED_ID = id("universal_feed");
	public static final Identifier GROUP_ID = id("workstations");

	public static final RanchBlock RANCH_BLOCK = new RanchBlock(stationSettings());
	public static final FarmBlock FARM_BLOCK = new FarmBlock(stationSettings());
	public static final MilkBarrelBlock MILK_BARREL_BLOCK = new MilkBarrelBlock(barrelSettings());

	public static final BlockItem RANCH_ITEM = new BlockItem(RANCH_BLOCK, new Item.Settings());
	public static final BlockItem FARM_ITEM = new BlockItem(FARM_BLOCK, new Item.Settings());
	public static final BlockItem MILK_BARREL_ITEM = new BlockItem(MILK_BARREL_BLOCK, new Item.Settings());
	public static final UniversalFeedItem UNIVERSAL_FEED = new UniversalFeedItem(new Item.Settings());

	/**
	 * One creative tab for everything the mod adds, rather than scattering four things through the
	 * vanilla ones. Small enough a mod that a player looking for its parts wants them together, and
	 * a station is no more a "functional block" than the feed is an "ingredient".
	 */
	public static final ItemGroup GROUP = FabricItemGroup.builder()
			.icon(() -> new ItemStack(RANCH_ITEM))
			.displayName(Text.translatable("itemGroup.workstations"))
			.entries((context, entries) -> {
				entries.add(RANCH_ITEM);
				entries.add(FARM_ITEM);
				entries.add(MILK_BARREL_ITEM);
				entries.add(UNIVERSAL_FEED);
			})
			.build();

	public static BlockEntityType<RanchBlockEntity> RANCH_BLOCK_ENTITY;
	public static BlockEntityType<FarmBlockEntity> FARM_BLOCK_ENTITY;
	public static BlockEntityType<MilkBarrelBlockEntity> MILK_BARREL_BLOCK_ENTITY;
	public static EntityType<RancherEntity> RANCHER;
	public static EntityType<FarmerEntity> FARMER;

	/** Both stations are the same wooden table underneath, so they are built the same way. */
	private static AbstractBlock.Settings stationSettings() {
		return AbstractBlock.Settings.create()
				.mapColor(MapColor.OAK_TAN)
				.strength(1.5F)
				.sounds(BlockSoundGroup.WOOD)
				.burnable()
				.nonOpaque()
				.pistonBehavior(PistonBehavior.DESTROY)
				.solidBlock((state, world, pos) -> false);
	}

	/**
	 * A full cube of staves, unlike either station, so it keeps none of the table's exceptions: it
	 * can be stood on, walked around and built against like any other solid block. Only the seeing
	 * through it is unusual, because the top is open and the inside of the far wall shows.
	 */
	private static AbstractBlock.Settings barrelSettings() {
		return AbstractBlock.Settings.create()
				.mapColor(MapColor.OAK_TAN)
				.strength(1.5F)
				.sounds(BlockSoundGroup.WOOD)
				.burnable()
				.nonOpaque();
	}

	@Override
	public void onInitialize() {
		ModConfig.get();

		Registry.register(Registries.BLOCK, RANCH_ID, RANCH_BLOCK);
		Registry.register(Registries.ITEM, RANCH_ID, RANCH_ITEM);
		RANCH_BLOCK_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, RANCH_ID,
				FabricBlockEntityTypeBuilder.create(RanchBlockEntity::new, RANCH_BLOCK).build());

		Registry.register(Registries.BLOCK, FARM_ID, FARM_BLOCK);
		Registry.register(Registries.ITEM, FARM_ID, FARM_ITEM);
		FARM_BLOCK_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, FARM_ID,
				FabricBlockEntityTypeBuilder.create(FarmBlockEntity::new, FARM_BLOCK).build());

		// No spawn egg and no natural spawning: a station is the only thing that makes a worker.
		RANCHER = Registry.register(Registries.ENTITY_TYPE, RANCHER_ID,
				EntityType.Builder.<RancherEntity>create(RancherEntity::new, SpawnGroup.MISC)
						.setDimensions(0.6F, 1.95F)
						.maxTrackingRange(10)
						.build(RANCHER_ID.getPath()));
		FabricDefaultAttributeRegistry.register(RANCHER, RancherEntity.createRancherAttributes());

		FARMER = Registry.register(Registries.ENTITY_TYPE, FARMER_ID,
				EntityType.Builder.<FarmerEntity>create(FarmerEntity::new, SpawnGroup.MISC)
						.setDimensions(0.6F, 1.95F)
						.maxTrackingRange(10)
						.build(FARMER_ID.getPath()));
		FabricDefaultAttributeRegistry.register(FARMER, FarmerEntity.createFarmerAttributes());

		Registry.register(Registries.BLOCK, MILK_BARREL_ID, MILK_BARREL_BLOCK);
		Registry.register(Registries.ITEM, MILK_BARREL_ID, MILK_BARREL_ITEM);
		MILK_BARREL_BLOCK_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, MILK_BARREL_ID,
				FabricBlockEntityTypeBuilder.create(MilkBarrelBlockEntity::new, MILK_BARREL_BLOCK).build());

		Registry.register(Registries.ITEM, UNIVERSAL_FEED_ID, UNIVERSAL_FEED);
		Registry.register(Registries.ITEM_GROUP, GROUP_ID, GROUP);

		StationNetworking.registerServerReceivers();
		registerSettingsGesture();
		announceMcaRefusal();

		LOGGER.info("Workstations initialized");
	}

	/**
	 * Sneak and use a station to set it up.
	 *
	 * <p>Hooked to this event rather than to the block's own use handler because vanilla skips that
	 * handler entirely when a sneaking player has anything in either hand, going straight to using
	 * the held item. The screen would then only have opened with both hands empty. This event runs
	 * ahead of that decision, and answering SUCCESS is also what tells the client to report the
	 * click to the server instead of trying to place whatever it is holding.
	 */
	private static void registerSettingsGesture() {
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			BlockPos pos = hit.getBlockPos();
			BlockState state = world.getBlockState(pos);

			if (!player.isSneaking() || !(state.isOf(RANCH_BLOCK) || state.isOf(FARM_BLOCK))) {
				return ActionResult.PASS;
			}

			if (player instanceof ServerPlayerEntity serverPlayer
					&& world.getBlockEntity(pos) instanceof WorkStationBlockEntity<?, ?>) {
				StationNetworking.openScreen(serverPlayer, pos);
			}

			return ActionResult.SUCCESS;
		});
	}

	/**
	 * Says once, to each player as they arrive, that the installed Minecraft Comes Alive is not one
	 * workers can be dressed from.
	 *
	 * <p>Sent from the server rather than shown on the client so that it reaches the people who can
	 * do something about it: on a server it is the jar sitting there that decides how workers look,
	 * and a player has no way of telling from their own side which of the two ends turned it down.
	 * Registered only when there is something to say, so the usual case costs nothing at all.
	 */
	private static void announceMcaRefusal() {
		Text notice = McaSupport.refusalNotice();

		if (notice == null) {
			return;
		}

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				handler.getPlayer().sendMessage(notice, false));
	}

	public static Identifier id(String path) {
		return new Identifier(MOD_ID, path);
	}
}
