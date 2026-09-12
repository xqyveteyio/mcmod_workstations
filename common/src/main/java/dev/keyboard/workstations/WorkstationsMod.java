package dev.keyboard.workstations;

import me.shedaniel.architectury.event.events.InteractionEvent;
import me.shedaniel.architectury.registry.CreativeTabs;
import me.shedaniel.architectury.registry.DeferredRegister;
import me.shedaniel.architectury.registry.RegistrySupplier;
import me.shedaniel.architectury.registry.entity.EntityAttributes;
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
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.block.Material;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class WorkstationsMod {
	public static final String MOD_ID = "villager_workstations";
	public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

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
	public static final Identifier GROUP_ID = id("villager_workstations");

	public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(MOD_ID, Registry.BLOCK_KEY);
	public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(MOD_ID, Registry.ITEM_KEY);
	public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
			DeferredRegister.create(MOD_ID, Registry.BLOCK_ENTITY_TYPE_KEY);
	public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
			DeferredRegister.create(MOD_ID, Registry.ENTITY_TYPE_KEY);

	public static final RegistrySupplier<RanchBlock> RANCH_BLOCK = BLOCKS.register(RANCH_ID.getPath(),
			() -> new RanchBlock(stationSettings()));
	public static final RegistrySupplier<FarmBlock> FARM_BLOCK = BLOCKS.register(FARM_ID.getPath(),
			() -> new FarmBlock(stationSettings()));
	public static final RegistrySupplier<LumberBlock> LUMBER_BLOCK = BLOCKS.register(LUMBER_ID.getPath(),
			() -> new LumberBlock(stationSettings()));
	public static final RegistrySupplier<MilkBarrelBlock> MILK_BARREL_BLOCK = BLOCKS.register(MILK_BARREL_ID.getPath(),
			() -> new MilkBarrelBlock(barrelSettings()));
	public static final RegistrySupplier<FeedBarrelBlock> FEED_BARREL_BLOCK = BLOCKS.register(FEED_BARREL_ID.getPath(),
			() -> new FeedBarrelBlock(chestSettings()));
	public static final RegistrySupplier<SeedBoxBlock> SEED_BOX_BLOCK = BLOCKS.register(SEED_BOX_ID.getPath(),
			() -> new SeedBoxBlock(chestSettings()));

	public static final ItemGroup GROUP = CreativeTabs.create(GROUP_ID, WorkstationsMod::groupIcon);

	public static final RegistrySupplier<BlockItem> RANCH_ITEM = ITEMS.register(RANCH_ID.getPath(),
			() -> ModItems.renderedBlockItem(RANCH_BLOCK.get(), new Item.Settings().group(GROUP)));
	public static final RegistrySupplier<BlockItem> FARM_ITEM = ITEMS.register(FARM_ID.getPath(),
			() -> new BlockItem(FARM_BLOCK.get(), new Item.Settings().group(GROUP)));
	public static final RegistrySupplier<BlockItem> LUMBER_ITEM = ITEMS.register(LUMBER_ID.getPath(),
			() -> ModItems.renderedBlockItem(LUMBER_BLOCK.get(), new Item.Settings().group(GROUP)));
	public static final RegistrySupplier<BlockItem> MILK_BARREL_ITEM = ITEMS.register(MILK_BARREL_ID.getPath(),
			() -> new BlockItem(MILK_BARREL_BLOCK.get(), new Item.Settings().group(GROUP)));
	public static final RegistrySupplier<BlockItem> FEED_BARREL_ITEM = ITEMS.register(FEED_BARREL_ID.getPath(),
			() -> ModItems.renderedBlockItem(FEED_BARREL_BLOCK.get(), new Item.Settings().group(GROUP)));
	public static final RegistrySupplier<BlockItem> SEED_BOX_ITEM = ITEMS.register(SEED_BOX_ID.getPath(),
			() -> ModItems.renderedBlockItem(SEED_BOX_BLOCK.get(), new Item.Settings().group(GROUP)));
	public static final RegistrySupplier<Item> UNIVERSAL_FEED = ITEMS.register(UNIVERSAL_FEED_ID.getPath(),
			() -> new Item(new Item.Settings().group(GROUP)));

	public static final RegistrySupplier<BlockEntityType<RanchBlockEntity>> RANCH_BLOCK_ENTITY =
			BLOCK_ENTITY_TYPES.register(RANCH_ID.getPath(), () ->
					BlockEntityType.Builder.create(RanchBlockEntity::new, RANCH_BLOCK.get()).build(null));
	public static final RegistrySupplier<BlockEntityType<FarmBlockEntity>> FARM_BLOCK_ENTITY =
			BLOCK_ENTITY_TYPES.register(FARM_ID.getPath(), () ->
					BlockEntityType.Builder.create(FarmBlockEntity::new, FARM_BLOCK.get()).build(null));
	public static final RegistrySupplier<BlockEntityType<LumberBlockEntity>> LUMBER_BLOCK_ENTITY =
			BLOCK_ENTITY_TYPES.register(LUMBER_ID.getPath(), () ->
					BlockEntityType.Builder.create(LumberBlockEntity::new, LUMBER_BLOCK.get()).build(null));
	public static final RegistrySupplier<BlockEntityType<MilkBarrelBlockEntity>> MILK_BARREL_BLOCK_ENTITY =
			BLOCK_ENTITY_TYPES.register(MILK_BARREL_ID.getPath(), () ->
					BlockEntityType.Builder.create(MilkBarrelBlockEntity::new, MILK_BARREL_BLOCK.get()).build(null));
	public static final RegistrySupplier<BlockEntityType<FeedBarrelBlockEntity>> FEED_BARREL_BLOCK_ENTITY =
			BLOCK_ENTITY_TYPES.register(FEED_BARREL_ID.getPath(), () ->
					BlockEntityType.Builder.create(FeedBarrelBlockEntity::new, FEED_BARREL_BLOCK.get()).build(null));
	public static final RegistrySupplier<BlockEntityType<SeedBoxBlockEntity>> SEED_BOX_BLOCK_ENTITY =
			BLOCK_ENTITY_TYPES.register(SEED_BOX_ID.getPath(), () ->
					BlockEntityType.Builder.create(SeedBoxBlockEntity::new, SEED_BOX_BLOCK.get()).build(null));

	public static final RegistrySupplier<EntityType<RancherEntity>> RANCHER = ENTITY_TYPES.register(RANCHER_ID.getPath(),
			() -> EntityType.Builder.<RancherEntity>create(RancherEntity::new, SpawnGroup.MISC)
					.setDimensions(0.6F, 1.95F)
					.maxTrackingRange(10)
					.build(RANCHER_ID.getPath()));
	public static final RegistrySupplier<EntityType<FarmerEntity>> FARMER = ENTITY_TYPES.register(FARMER_ID.getPath(),
			() -> EntityType.Builder.<FarmerEntity>create(FarmerEntity::new, SpawnGroup.MISC)
					.setDimensions(0.6F, 1.95F)
					.maxTrackingRange(10)
					.build(FARMER_ID.getPath()));
	public static final RegistrySupplier<EntityType<LumberjackEntity>> LUMBERJACK = ENTITY_TYPES.register(LUMBERJACK_ID.getPath(),
			() -> EntityType.Builder.<LumberjackEntity>create(LumberjackEntity::new, SpawnGroup.MISC)
					.setDimensions(0.6F, 1.95F)
					.maxTrackingRange(10)
					.build(LUMBERJACK_ID.getPath()));

	/** Every station is the same wooden table underneath, so they are built the same way. */
	private static AbstractBlock.Settings stationSettings() {
		return AbstractBlock.Settings.of(Material.WOOD, MapColor.OAK_TAN)
				.strength(1.5F)
				.sounds(BlockSoundGroup.WOOD)
				.nonOpaque();
	}

	/**
	 * A full cube of staves, unlike either station, so it keeps none of the table's exceptions: it
	 * can be stood on, walked around and built against like any other solid block. Only the seeing
	 * through it is unusual, because the top is open and the inside of the far wall shows.
	 */
	private static AbstractBlock.Settings barrelSettings() {
		return AbstractBlock.Settings.of(Material.WOOD, MapColor.OAK_TAN)
				.strength(1.5F)
				.sounds(BlockSoundGroup.WOOD)
				.nonOpaque();
	}

	/** A chest in all but name, so it is built out of what vanilla gives its own chests. */
	private static AbstractBlock.Settings chestSettings() {
		return AbstractBlock.Settings.of(Material.WOOD, MapColor.OAK_TAN)
				.strength(2.5F)
				.sounds(BlockSoundGroup.WOOD);
	}

	public static void init() {
		ModConfig.get();

		BLOCKS.register();
		ITEMS.register();
		BLOCK_ENTITY_TYPES.register();
		ENTITY_TYPES.register();

		EntityAttributes.register(RANCHER::get, RancherEntity::createRancherAttributes);
		EntityAttributes.register(FARMER::get, FarmerEntity::createFarmerAttributes);
		EntityAttributes.register(LUMBERJACK::get, LumberjackEntity::createLumberjackAttributes);

		StationNetworking.registerServerReceivers();
		registerSettingsGesture();

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
		InteractionEvent.RIGHT_CLICK_BLOCK.register((player, hand, pos, face) -> {
			BlockState state = player.getEntityWorld().getBlockState(pos);

			if (!player.isSneaking() || !(state.isOf(RANCH_BLOCK.get()) || state.isOf(FARM_BLOCK.get())
					|| state.isOf(LUMBER_BLOCK.get()))) {
				return ActionResult.PASS;
			}

			if (player instanceof ServerPlayerEntity serverPlayer
					&& player.getEntityWorld().getBlockEntity(pos) instanceof WorkStationBlockEntity<?, ?>) {
				StationNetworking.openScreen(serverPlayer, pos);
			}

			return ActionResult.SUCCESS;
		});
	}

	private static ItemStack groupIcon() {
		return new ItemStack(RANCH_ITEM.get());
	}

	public static Identifier id(String path) {
		return new Identifier(MOD_ID, path);
	}
}
