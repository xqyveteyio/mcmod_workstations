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
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkstationsMod implements ModInitializer {
	public static final String MOD_ID = "keyboard_workstations";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

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

	public static final RanchBlock RANCH_BLOCK = new RanchBlock(stationSettings(RANCH_ID));
	public static final FarmBlock FARM_BLOCK = new FarmBlock(stationSettings(FARM_ID));
	public static final LumberBlock LUMBER_BLOCK = new LumberBlock(stationSettings(LUMBER_ID));
	public static final MilkBarrelBlock MILK_BARREL_BLOCK = new MilkBarrelBlock(barrelSettings(MILK_BARREL_ID));
	public static final FeedBarrelBlock FEED_BARREL_BLOCK = new FeedBarrelBlock(chestSettings(FEED_BARREL_ID));
	public static final SeedBoxBlock SEED_BOX_BLOCK = new SeedBoxBlock(chestSettings(SEED_BOX_ID));

	public static final BlockItem RANCH_ITEM = new BlockItem(RANCH_BLOCK, blockItemSettings(RANCH_ID));
	public static final BlockItem FARM_ITEM = new BlockItem(FARM_BLOCK, blockItemSettings(FARM_ID));
	public static final BlockItem LUMBER_ITEM = new BlockItem(LUMBER_BLOCK, blockItemSettings(LUMBER_ID));
	public static final BlockItem MILK_BARREL_ITEM = new BlockItem(MILK_BARREL_BLOCK, blockItemSettings(MILK_BARREL_ID));
	public static final BlockItem FEED_BARREL_ITEM = new BlockItem(FEED_BARREL_BLOCK, blockItemSettings(FEED_BARREL_ID));
	public static final BlockItem SEED_BOX_ITEM = new BlockItem(SEED_BOX_BLOCK, blockItemSettings(SEED_BOX_ID));
	public static final Item UNIVERSAL_FEED = new Item(itemSettings(UNIVERSAL_FEED_ID));

	/**
	 * One creative tab for everything the mod adds, rather than scattering four things through the
	 * vanilla ones. Small enough a mod that a player looking for its parts wants them together, and
	 * a station is no more a "functional block" than the feed is an "ingredient".
	 */
	public static final CreativeModeTab GROUP = FabricCreativeModeTab.builder()
			.icon(() -> new ItemStack(RANCH_ITEM))
			.title(Component.translatable("itemGroup.keyboard_workstations"))
			.displayItems((context, entries) -> {
				entries.accept(RANCH_ITEM);
				entries.accept(FARM_ITEM);
				entries.accept(LUMBER_ITEM);
				entries.accept(MILK_BARREL_ITEM);
				entries.accept(FEED_BARREL_ITEM);
				entries.accept(SEED_BOX_ITEM);
				entries.accept(UNIVERSAL_FEED);
			})
			.build();

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
	private static BlockBehaviour.Properties stationSettings(Identifier id) {
		return BlockBehaviour.Properties.of()
				.setId(ResourceKey.create(Registries.BLOCK, id))
				.mapColor(MapColor.WOOD)
				.strength(1.5F)
				.sound(SoundType.WOOD)
				.ignitedByLava()
				.noOcclusion()
				.pushReaction(PushReaction.DESTROY)
				.isRedstoneConductor((state, world, pos) -> false);
	}

	/**
	 * A full cube of staves, unlike either station, so it keeps none of the table's exceptions: it
	 * can be stood on, walked around and built against like any other solid block. Only the seeing
	 * through it is unusual, because the top is open and the inside of the far wall shows.
	 */
	private static BlockBehaviour.Properties barrelSettings(Identifier id) {
		return BlockBehaviour.Properties.of()
				.setId(ResourceKey.create(Registries.BLOCK, id))
				.mapColor(MapColor.WOOD)
				.strength(1.5F)
				.sound(SoundType.WOOD)
				.ignitedByLava()
				.noOcclusion();
	}

	/** A chest in all but name, so it is built out of what vanilla gives its own chests. */
	private static BlockBehaviour.Properties chestSettings(Identifier id) {
		return BlockBehaviour.Properties.of()
				.setId(ResourceKey.create(Registries.BLOCK, id))
				.mapColor(MapColor.WOOD)
				.strength(2.5F)
				.sound(SoundType.WOOD)
				.ignitedByLava();
	}

	private static Item.Properties itemSettings(Identifier id) {
		return new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id));
	}

	private static Item.Properties blockItemSettings(Identifier id) {
		return itemSettings(id).useBlockDescriptionPrefix();
	}

	@Override
	public void onInitialize() {
		ModConfig.get();

		registerBlock(RANCH_ID, RANCH_BLOCK, RANCH_ITEM);
		RANCH_BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, RANCH_ID,
				FabricBlockEntityTypeBuilder.create(RanchBlockEntity::new, RANCH_BLOCK).build());

		registerBlock(FARM_ID, FARM_BLOCK, FARM_ITEM);
		FARM_BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, FARM_ID,
				FabricBlockEntityTypeBuilder.create(FarmBlockEntity::new, FARM_BLOCK).build());

		registerBlock(LUMBER_ID, LUMBER_BLOCK, LUMBER_ITEM);
		LUMBER_BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, LUMBER_ID,
				FabricBlockEntityTypeBuilder.create(LumberBlockEntity::new, LUMBER_BLOCK).build());

		// No spawn egg and no natural spawning: a station is the only thing that makes a worker.
		RANCHER = registerWorker(RANCHER_ID, EntityType.Builder.<RancherEntity>of(RancherEntity::new, MobCategory.MISC)
				.sized(0.6F, 1.95F)
				.clientTrackingRange(10));
		FabricDefaultAttributeRegistry.register(RANCHER, RancherEntity.createRancherAttributes());

		FARMER = registerWorker(FARMER_ID, EntityType.Builder.<FarmerEntity>of(FarmerEntity::new, MobCategory.MISC)
				.sized(0.6F, 1.95F)
				.clientTrackingRange(10));
		FabricDefaultAttributeRegistry.register(FARMER, FarmerEntity.createFarmerAttributes());

		LUMBERJACK = registerWorker(LUMBERJACK_ID, EntityType.Builder.<LumberjackEntity>of(LumberjackEntity::new, MobCategory.MISC)
				.sized(0.6F, 1.95F)
				.clientTrackingRange(10));
		FabricDefaultAttributeRegistry.register(LUMBERJACK, LumberjackEntity.createLumberjackAttributes());

		registerBlock(MILK_BARREL_ID, MILK_BARREL_BLOCK, MILK_BARREL_ITEM);
		MILK_BARREL_BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, MILK_BARREL_ID,
				FabricBlockEntityTypeBuilder.create(MilkBarrelBlockEntity::new, MILK_BARREL_BLOCK).build());

		registerBlock(FEED_BARREL_ID, FEED_BARREL_BLOCK, FEED_BARREL_ITEM);
		FEED_BARREL_BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, FEED_BARREL_ID,
				FabricBlockEntityTypeBuilder.create(FeedBarrelBlockEntity::new, FEED_BARREL_BLOCK).build());

		registerBlock(SEED_BOX_ID, SEED_BOX_BLOCK, SEED_BOX_ITEM);
		SEED_BOX_BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, SEED_BOX_ID,
				FabricBlockEntityTypeBuilder.create(SeedBoxBlockEntity::new, SEED_BOX_BLOCK).build());

		Registry.register(BuiltInRegistries.ITEM, UNIVERSAL_FEED_ID, UNIVERSAL_FEED);
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, GROUP_ID, GROUP);

		StationNetworking.registerServerReceivers();
		registerSettingsGesture();

		LOGGER.info("Workstations initialized");
	}

	private static void registerBlock(Identifier id, Block block, BlockItem item) {
		Registry.register(BuiltInRegistries.BLOCK, id, block);
		Registry.register(BuiltInRegistries.ITEM, id, item);
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> registerWorker(
			Identifier id, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id);
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, id, builder.build(key));
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

			if (!player.isShiftKeyDown() || !(state.is(RANCH_BLOCK) || state.is(FARM_BLOCK)
					|| state.is(LUMBER_BLOCK))) {
				return InteractionResult.PASS;
			}

			if (player instanceof ServerPlayer serverPlayer
					&& world.getBlockEntity(pos) instanceof WorkStationBlockEntity<?, ?>) {
				StationNetworking.openScreen(serverPlayer, pos);
			}

			return InteractionResult.SUCCESS;
		});
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
