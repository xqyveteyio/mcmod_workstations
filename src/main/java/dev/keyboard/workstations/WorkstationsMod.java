package dev.keyboard.workstations;

import dev.keyboard.workstations.block.FarmBlock;
import dev.keyboard.workstations.block.FarmBlockEntity;
import dev.keyboard.workstations.block.FeedBarrelBlock;
import dev.keyboard.workstations.block.FeedBarrelBlockEntity;
import dev.keyboard.workstations.block.LumberBlock;
import dev.keyboard.workstations.block.LumberBlockEntity;
import dev.keyboard.workstations.block.MilkBarrelBlock;
import dev.keyboard.workstations.block.MilkBarrelBlockEntity;
import dev.keyboard.workstations.block.RanchBlock;
import dev.keyboard.workstations.block.RanchBlockEntity;
import dev.keyboard.workstations.block.SeedBoxBlock;
import dev.keyboard.workstations.block.SeedBoxBlockEntity;
import dev.keyboard.workstations.block.WorkStationBlockEntity;
import dev.keyboard.workstations.entity.FarmerEntity;
import dev.keyboard.workstations.entity.LumberjackEntity;
import dev.keyboard.workstations.entity.RancherEntity;
import dev.keyboard.workstations.network.StationNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
//? if >=1.19.3 {
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
//?} else {
/* import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder; */
//?}
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
//? if >=1.17 {
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
//?}
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
//? if <1.20 {
/* import net.minecraft.block.Material; */
//?}
import net.minecraft.block.entity.BlockEntityType;
//? if >=1.20 {
import net.minecraft.block.piston.PistonBehavior;
//?}
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
//? if >=1.19.3 {
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
//?} else {
/* import net.minecraft.util.registry.Registry; */
//?}
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
//? if >=1.18 {
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
//?} else {
/* import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger; */
//?}

public class WorkstationsMod implements ModInitializer {
	public static final String MOD_ID = "keyboard_workstations";
	public static final Logger LOGGER =
			//? if >=1.18 {
			LoggerFactory.getLogger(MOD_ID);
			//?} else {
			/* LogManager.getLogger(MOD_ID); */
			//?}

	public static final Identifier RANCH_ID = id("ranch_station");
	public static final Identifier RANCHER_ID = id("rancher");
	public static final Identifier FARM_ID = id("farm_station");
	public static final Identifier FARMER_ID = id("farmer");
	public static final Identifier LUMBER_ID = id("lumber_station");
	public static final Identifier LUMBERJACK_ID = id("lumberjack");
	public static final Identifier MILK_BARREL_ID = id("milk_barrel");
	public static final Identifier FEED_BARREL_ID = id("feed_barrel");
	public static final Identifier SEED_BOX_ID = id("seed_box");
	public static final Identifier UNIVERSAL_FEED_ID = id("universal_feed");
	public static final Identifier GROUP_ID = id("keyboard_workstations");

	public static final RanchBlock RANCH_BLOCK = new RanchBlock(stationSettings());
	public static final FarmBlock FARM_BLOCK = new FarmBlock(stationSettings());
	public static final LumberBlock LUMBER_BLOCK = new LumberBlock(stationSettings());
	public static final MilkBarrelBlock MILK_BARREL_BLOCK = new MilkBarrelBlock(barrelSettings());
	public static final FeedBarrelBlock FEED_BARREL_BLOCK = new FeedBarrelBlock(chestSettings());
	public static final SeedBoxBlock SEED_BOX_BLOCK = new SeedBoxBlock(chestSettings());

	public static final BlockItem RANCH_ITEM = new BlockItem(RANCH_BLOCK, new Item.Settings());
	public static final BlockItem FARM_ITEM = new BlockItem(FARM_BLOCK, new Item.Settings());
	public static final BlockItem LUMBER_ITEM = new BlockItem(LUMBER_BLOCK, new Item.Settings());
	public static final BlockItem MILK_BARREL_ITEM = new BlockItem(MILK_BARREL_BLOCK, new Item.Settings());
	public static final BlockItem FEED_BARREL_ITEM = new BlockItem(FEED_BARREL_BLOCK, new Item.Settings());
	public static final BlockItem SEED_BOX_ITEM = new BlockItem(SEED_BOX_BLOCK, new Item.Settings());
	public static final Item UNIVERSAL_FEED = new Item(new Item.Settings());

	/**
	 * One creative tab for everything the mod adds, rather than scattering four things through the
	 * vanilla ones. Small enough a mod that a player looking for its parts wants them together, and
	 * a station is no more a "functional block" than the feed is an "ingredient".
	 */
	public static final ItemGroup GROUP =
			//? if >=1.19.3 {
			FabricItemGroup.builder()
			.icon(() -> new ItemStack(RANCH_ITEM))
			.displayName(Mc.translatable("itemGroup.keyboard_workstations"))
			.entries((context, entries) -> {
				entries.add(RANCH_ITEM);
				entries.add(FARM_ITEM);
				entries.add(LUMBER_ITEM);
				entries.add(MILK_BARREL_ITEM);
				entries.add(FEED_BARREL_ITEM);
				entries.add(SEED_BOX_ITEM);
				entries.add(UNIVERSAL_FEED);
			})
			.build();
			//?} else {
			/* FabricItemGroupBuilder.create(GROUP_ID)
			.icon(() -> new ItemStack(RANCH_ITEM))
			.appendItems(stacks -> {
				stacks.add(new ItemStack(RANCH_ITEM));
				stacks.add(new ItemStack(FARM_ITEM));
				stacks.add(new ItemStack(LUMBER_ITEM));
				stacks.add(new ItemStack(MILK_BARREL_ITEM));
				stacks.add(new ItemStack(FEED_BARREL_ITEM));
				stacks.add(new ItemStack(SEED_BOX_ITEM));
				stacks.add(new ItemStack(UNIVERSAL_FEED));
			})
			.build(); */
			//?}

	public static BlockEntityType<RanchBlockEntity> RANCH_BLOCK_ENTITY;
	public static BlockEntityType<FarmBlockEntity> FARM_BLOCK_ENTITY;
	public static BlockEntityType<LumberBlockEntity> LUMBER_BLOCK_ENTITY;
	public static BlockEntityType<MilkBarrelBlockEntity> MILK_BARREL_BLOCK_ENTITY;
	public static BlockEntityType<FeedBarrelBlockEntity> FEED_BARREL_BLOCK_ENTITY;
	public static BlockEntityType<SeedBoxBlockEntity> SEED_BOX_BLOCK_ENTITY;
	public static EntityType<RancherEntity> RANCHER;
	public static EntityType<FarmerEntity> FARMER;
	public static EntityType<LumberjackEntity> LUMBERJACK;

	/** Every station is the same wooden table underneath, so they are built the same way. */
	private static AbstractBlock.Settings stationSettings() {
		//? if >=1.20 {
		return AbstractBlock.Settings.create()
				.mapColor(MapColor.OAK_TAN)
				.strength(1.5F)
				.sounds(BlockSoundGroup.WOOD)
				.burnable()
				.nonOpaque()
				.pistonBehavior(PistonBehavior.DESTROY)
				.solidBlock((state, world, pos) -> false);
		//?} else {
		/* return AbstractBlock.Settings.of(Material.WOOD, MapColor.OAK_TAN)
				.strength(1.5F)
				.sounds(BlockSoundGroup.WOOD)
				.nonOpaque(); */
		//?}
	}

	/**
	 * A full cube of staves, unlike either station, so it keeps none of the table's exceptions: it
	 * can be stood on, walked around and built against like any other solid block. Only the seeing
	 * through it is unusual, because the top is open and the inside of the far wall shows.
	 */
	private static AbstractBlock.Settings barrelSettings() {
		//? if >=1.20 {
		return AbstractBlock.Settings.create()
				.mapColor(MapColor.OAK_TAN)
				.strength(1.5F)
				.sounds(BlockSoundGroup.WOOD)
				.burnable()
				.nonOpaque();
		//?} else {
		/* return AbstractBlock.Settings.of(Material.WOOD, MapColor.OAK_TAN)
				.strength(1.5F)
				.sounds(BlockSoundGroup.WOOD)
				.nonOpaque(); */
		//?}
	}

	/** A chest in all but name, so it is built out of what vanilla gives its own chests. */
	private static AbstractBlock.Settings chestSettings() {
		//? if >=1.20 {
		return AbstractBlock.Settings.create()
				.mapColor(MapColor.OAK_TAN)
				.strength(2.5F)
				.sounds(BlockSoundGroup.WOOD)
				.burnable();
		//?} else {
		/* return AbstractBlock.Settings.of(Material.WOOD, MapColor.OAK_TAN)
				.strength(2.5F)
				.sounds(BlockSoundGroup.WOOD); */
		//?}
	}

	@Override
	public void onInitialize() {
		ModConfig.get();
		registerContent();
		StationNetworking.registerServerReceivers();
		registerSettingsGesture();
		announceMcaRefusal();
		LOGGER.info("Workstations initialized");
	}

	/** Registers blocks, items, block entities and workers. Yarn split the registry class in 1.19.3. */
	private static void registerContent() {
		//? if >=1.19.3 {
		register(Registries.BLOCK, RANCH_ID, RANCH_BLOCK);
		register(Registries.ITEM, RANCH_ID, RANCH_ITEM);
		RANCH_BLOCK_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, RANCH_ID,
				FabricBlockEntityTypeBuilder.create(RanchBlockEntity::new, RANCH_BLOCK).build());

		register(Registries.BLOCK, FARM_ID, FARM_BLOCK);
		register(Registries.ITEM, FARM_ID, FARM_ITEM);
		FARM_BLOCK_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, FARM_ID,
				FabricBlockEntityTypeBuilder.create(FarmBlockEntity::new, FARM_BLOCK).build());

		register(Registries.BLOCK, LUMBER_ID, LUMBER_BLOCK);
		register(Registries.ITEM, LUMBER_ID, LUMBER_ITEM);
		LUMBER_BLOCK_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, LUMBER_ID,
				FabricBlockEntityTypeBuilder.create(LumberBlockEntity::new, LUMBER_BLOCK).build());

		RANCHER = Registry.register(Registries.ENTITY_TYPE, RANCHER_ID, rancherType());
		FabricDefaultAttributeRegistry.register(RANCHER, RancherEntity.createRancherAttributes());
		FARMER = Registry.register(Registries.ENTITY_TYPE, FARMER_ID, farmerType());
		FabricDefaultAttributeRegistry.register(FARMER, FarmerEntity.createFarmerAttributes());
		LUMBERJACK = Registry.register(Registries.ENTITY_TYPE, LUMBERJACK_ID, lumberjackType());
		FabricDefaultAttributeRegistry.register(LUMBERJACK, LumberjackEntity.createLumberjackAttributes());

		register(Registries.BLOCK, MILK_BARREL_ID, MILK_BARREL_BLOCK);
		register(Registries.ITEM, MILK_BARREL_ID, MILK_BARREL_ITEM);
		MILK_BARREL_BLOCK_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, MILK_BARREL_ID,
				FabricBlockEntityTypeBuilder.create(MilkBarrelBlockEntity::new, MILK_BARREL_BLOCK).build());

		register(Registries.BLOCK, FEED_BARREL_ID, FEED_BARREL_BLOCK);
		register(Registries.ITEM, FEED_BARREL_ID, FEED_BARREL_ITEM);
		FEED_BARREL_BLOCK_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, FEED_BARREL_ID,
				FabricBlockEntityTypeBuilder.create(FeedBarrelBlockEntity::new, FEED_BARREL_BLOCK).build());

		register(Registries.BLOCK, SEED_BOX_ID, SEED_BOX_BLOCK);
		register(Registries.ITEM, SEED_BOX_ID, SEED_BOX_ITEM);
		SEED_BOX_BLOCK_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, SEED_BOX_ID,
				FabricBlockEntityTypeBuilder.create(SeedBoxBlockEntity::new, SEED_BOX_BLOCK).build());

		register(Registries.ITEM, UNIVERSAL_FEED_ID, UNIVERSAL_FEED);
		register(Registries.ITEM_GROUP, GROUP_ID, GROUP);
		//?} else {
		/* register(Registry.BLOCK, RANCH_ID, RANCH_BLOCK);
		register(Registry.ITEM, RANCH_ID, RANCH_ITEM);
		RANCH_BLOCK_ENTITY = register(Registry.BLOCK_ENTITY_TYPE, RANCH_ID,
				BlockEntityType.Builder.create(RanchBlockEntity::new, RANCH_BLOCK).build(null));

		register(Registry.BLOCK, FARM_ID, FARM_BLOCK);
		register(Registry.ITEM, FARM_ID, FARM_ITEM);
		FARM_BLOCK_ENTITY = register(Registry.BLOCK_ENTITY_TYPE, FARM_ID,
				BlockEntityType.Builder.create(FarmBlockEntity::new, FARM_BLOCK).build(null));

		register(Registry.BLOCK, LUMBER_ID, LUMBER_BLOCK);
		register(Registry.ITEM, LUMBER_ID, LUMBER_ITEM);
		LUMBER_BLOCK_ENTITY = register(Registry.BLOCK_ENTITY_TYPE, LUMBER_ID,
				BlockEntityType.Builder.create(LumberBlockEntity::new, LUMBER_BLOCK).build(null));

		RANCHER = Registry.register(Registry.ENTITY_TYPE, RANCHER_ID, rancherType());
		FabricDefaultAttributeRegistry.register(RANCHER, RancherEntity.createRancherAttributes());
		FARMER = Registry.register(Registry.ENTITY_TYPE, FARMER_ID, farmerType());
		FabricDefaultAttributeRegistry.register(FARMER, FarmerEntity.createFarmerAttributes());
		LUMBERJACK = Registry.register(Registry.ENTITY_TYPE, LUMBERJACK_ID, lumberjackType());
		FabricDefaultAttributeRegistry.register(LUMBERJACK, LumberjackEntity.createLumberjackAttributes());

		register(Registry.BLOCK, MILK_BARREL_ID, MILK_BARREL_BLOCK);
		register(Registry.ITEM, MILK_BARREL_ID, MILK_BARREL_ITEM);
		MILK_BARREL_BLOCK_ENTITY = register(Registry.BLOCK_ENTITY_TYPE, MILK_BARREL_ID,
				BlockEntityType.Builder.create(MilkBarrelBlockEntity::new, MILK_BARREL_BLOCK).build(null));

		register(Registry.BLOCK, FEED_BARREL_ID, FEED_BARREL_BLOCK);
		register(Registry.ITEM, FEED_BARREL_ID, FEED_BARREL_ITEM);
		FEED_BARREL_BLOCK_ENTITY = register(Registry.BLOCK_ENTITY_TYPE, FEED_BARREL_ID,
				BlockEntityType.Builder.create(FeedBarrelBlockEntity::new, FEED_BARREL_BLOCK).build(null));

		register(Registry.BLOCK, SEED_BOX_ID, SEED_BOX_BLOCK);
		register(Registry.ITEM, SEED_BOX_ID, SEED_BOX_ITEM);
		SEED_BOX_BLOCK_ENTITY = register(Registry.BLOCK_ENTITY_TYPE, SEED_BOX_ID,
				BlockEntityType.Builder.create(SeedBoxBlockEntity::new, SEED_BOX_BLOCK).build(null));

		register(Registry.ITEM, UNIVERSAL_FEED_ID, UNIVERSAL_FEED); */
		//?}
	}

	@SuppressWarnings("unchecked")
	private static <T> T register(Registry<? super T> registry, Identifier id, T value) {
		return (T) Registry.register(registry, id, value);
	}

	private static EntityType<RancherEntity> rancherType() {
		//? if >=26.1 {
		/* return EntityType.Builder.of(RancherEntity::new, SpawnGroup.MISC)
				.sized(0.6F, 1.95F)
				.clientTrackingRange(10)
				.build(net.minecraft.resources.ResourceKey.create(
						net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.key(), RANCHER_ID)); */
		//?} else {
		return EntityType.Builder.<RancherEntity>create(RancherEntity::new, SpawnGroup.MISC)
				//? if >=1.21 {
				/* .dimensions(0.6F, 1.95F) */
				//?} else {
				.setDimensions(0.6F, 1.95F)
				//?}
				.maxTrackingRange(10)
				.build(RANCHER_ID.getPath());
		//?}
	}

	private static EntityType<FarmerEntity> farmerType() {
		//? if >=26.1 {
		/* return EntityType.Builder.of(FarmerEntity::new, SpawnGroup.MISC)
				.sized(0.6F, 1.95F)
				.clientTrackingRange(10)
				.build(net.minecraft.resources.ResourceKey.create(
						net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.key(), FARMER_ID)); */
		//?} else {
		return EntityType.Builder.<FarmerEntity>create(FarmerEntity::new, SpawnGroup.MISC)
				//? if >=1.21 {
				/* .dimensions(0.6F, 1.95F) */
				//?} else {
				.setDimensions(0.6F, 1.95F)
				//?}
				.maxTrackingRange(10)
				.build(FARMER_ID.getPath());
		//?}
	}

	private static EntityType<LumberjackEntity> lumberjackType() {
		//? if >=26.1 {
		/* return EntityType.Builder.of(LumberjackEntity::new, SpawnGroup.MISC)
				.sized(0.6F, 1.95F)
				.clientTrackingRange(10)
				.build(net.minecraft.resources.ResourceKey.create(
						net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.key(), LUMBERJACK_ID)); */
		//?} else {
		return EntityType.Builder.<LumberjackEntity>create(LumberjackEntity::new, SpawnGroup.MISC)
				//? if >=1.21 {
				/* .dimensions(0.6F, 1.95F) */
				//?} else {
				.setDimensions(0.6F, 1.95F)
				//?}
				.maxTrackingRange(10)
				.build(LUMBERJACK_ID.getPath());
		//?}
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

			if (!player.isSneaking() || !(state.isOf(RANCH_BLOCK) || state.isOf(FARM_BLOCK)
					|| state.isOf(LUMBER_BLOCK))) {
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

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			//? if >=1.17 {
			Mc.tell(handler.getPlayer(), notice, false);
			//?} else {
			/* Mc.tell(handler.player, notice, false); */
			//?}
		});
	}

	public static Identifier id(String path) {
		return id(MOD_ID, path);
	}

	public static Identifier id(String namespace, String path) {
		//? if >=26.1 {
		/* return Identifier.fromNamespaceAndPath(namespace, path); */
		//?} elif >=1.21 {
		/* return Identifier.of(namespace, path); */
		//?} else {
		return new Identifier(namespace, path);
		//?}
	}
}
