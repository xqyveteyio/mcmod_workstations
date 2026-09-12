package dev.keyboard.workstations;

import dev.architectury.event.events.common.InteractionEvent;
import dev.architectury.registry.CreativeTabRegistry;
import dev.architectury.registry.level.entity.EntityAttributeRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
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
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public class WorkstationsMod {
	public static final String MOD_ID = "villager_workstations";
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
	public static final Identifier GROUP_ID = id("villager_workstations");

	public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(MOD_ID, Registries.BLOCK);
	public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(MOD_ID, Registries.ITEM);
	public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(MOD_ID, Registries.BLOCK_ENTITY_TYPE);
	public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(MOD_ID, Registries.ENTITY_TYPE);
	public static final DeferredRegister<CreativeModeTab> ITEM_GROUPS = DeferredRegister.create(MOD_ID, Registries.CREATIVE_MODE_TAB);

	public static final RegistrySupplier<RanchBlock> RANCH_BLOCK = BLOCKS.register(RANCH_ID.getPath(),
			() -> new RanchBlock(stationSettings(RANCH_ID)));
	public static final RegistrySupplier<FarmBlock> FARM_BLOCK = BLOCKS.register(FARM_ID.getPath(),
			() -> new FarmBlock(stationSettings(FARM_ID)));
	public static final RegistrySupplier<LumberBlock> LUMBER_BLOCK = BLOCKS.register(LUMBER_ID.getPath(),
			() -> new LumberBlock(stationSettings(LUMBER_ID)));
	public static final RegistrySupplier<MilkBarrelBlock> MILK_BARREL_BLOCK = BLOCKS.register(MILK_BARREL_ID.getPath(),
			() -> new MilkBarrelBlock(barrelSettings(MILK_BARREL_ID)));
	public static final RegistrySupplier<FeedBarrelBlock> FEED_BARREL_BLOCK = BLOCKS.register(FEED_BARREL_ID.getPath(),
			() -> new FeedBarrelBlock(chestSettings(FEED_BARREL_ID)));
	public static final RegistrySupplier<SeedBoxBlock> SEED_BOX_BLOCK = BLOCKS.register(SEED_BOX_ID.getPath(),
			() -> new SeedBoxBlock(chestSettings(SEED_BOX_ID)));

	public static final RegistrySupplier<BlockItem> RANCH_ITEM = ITEMS.register(RANCH_ID.getPath(),
			() -> new BlockItem(RANCH_BLOCK.get(), blockItemSettings(RANCH_ID)));
	public static final RegistrySupplier<BlockItem> FARM_ITEM = ITEMS.register(FARM_ID.getPath(),
			() -> new BlockItem(FARM_BLOCK.get(), blockItemSettings(FARM_ID)));
	public static final RegistrySupplier<BlockItem> LUMBER_ITEM = ITEMS.register(LUMBER_ID.getPath(),
			() -> new BlockItem(LUMBER_BLOCK.get(), blockItemSettings(LUMBER_ID)));
	public static final RegistrySupplier<BlockItem> MILK_BARREL_ITEM = ITEMS.register(MILK_BARREL_ID.getPath(),
			() -> new BlockItem(MILK_BARREL_BLOCK.get(), blockItemSettings(MILK_BARREL_ID)));
	public static final RegistrySupplier<BlockItem> FEED_BARREL_ITEM = ITEMS.register(FEED_BARREL_ID.getPath(),
			() -> new BlockItem(FEED_BARREL_BLOCK.get(), blockItemSettings(FEED_BARREL_ID)));
	public static final RegistrySupplier<BlockItem> SEED_BOX_ITEM = ITEMS.register(SEED_BOX_ID.getPath(),
			() -> new BlockItem(SEED_BOX_BLOCK.get(), blockItemSettings(SEED_BOX_ID)));
	public static final RegistrySupplier<Item> UNIVERSAL_FEED = ITEMS.register(UNIVERSAL_FEED_ID.getPath(),
			() -> new Item(itemSettings(UNIVERSAL_FEED_ID)));

	/**
	 * One creative tab for everything the mod adds, rather than scattering four things through the
	 * vanilla ones. Small enough a mod that a player looking for its parts wants them together, and
	 * a station is no more a "functional block" than the feed is an "ingredient".
	 */
	public static final RegistrySupplier<CreativeModeTab> GROUP = ITEM_GROUPS.register(GROUP_ID.getPath(), () ->
			CreativeTabRegistry.create(builder -> builder
					.icon(() -> new ItemStack(RANCH_ITEM.get()))
					.title(Component.translatable("itemGroup.villager_workstations"))
					.displayItems((context, entries) -> {
						entries.accept(RANCH_ITEM.get());
						entries.accept(FARM_ITEM.get());
						entries.accept(LUMBER_ITEM.get());
						entries.accept(MILK_BARREL_ITEM.get());
						entries.accept(FEED_BARREL_ITEM.get());
						entries.accept(SEED_BOX_ITEM.get());
						entries.accept(UNIVERSAL_FEED.get());
					})));

	public static final RegistrySupplier<BlockEntityType<RanchBlockEntity>> RANCH_BLOCK_ENTITY =
			BLOCK_ENTITY_TYPES.register(RANCH_ID.getPath(),
					() -> blockEntityType(RanchBlockEntity::new, RANCH_BLOCK.get()));
	public static final RegistrySupplier<BlockEntityType<FarmBlockEntity>> FARM_BLOCK_ENTITY =
			BLOCK_ENTITY_TYPES.register(FARM_ID.getPath(),
					() -> blockEntityType(FarmBlockEntity::new, FARM_BLOCK.get()));
	public static final RegistrySupplier<BlockEntityType<LumberBlockEntity>> LUMBER_BLOCK_ENTITY =
			BLOCK_ENTITY_TYPES.register(LUMBER_ID.getPath(),
					() -> blockEntityType(LumberBlockEntity::new, LUMBER_BLOCK.get()));
	public static final RegistrySupplier<BlockEntityType<MilkBarrelBlockEntity>> MILK_BARREL_BLOCK_ENTITY =
			BLOCK_ENTITY_TYPES.register(MILK_BARREL_ID.getPath(),
					() -> blockEntityType(MilkBarrelBlockEntity::new, MILK_BARREL_BLOCK.get()));
	public static final RegistrySupplier<BlockEntityType<FeedBarrelBlockEntity>> FEED_BARREL_BLOCK_ENTITY =
			BLOCK_ENTITY_TYPES.register(FEED_BARREL_ID.getPath(),
					() -> blockEntityType(FeedBarrelBlockEntity::new, FEED_BARREL_BLOCK.get()));
	public static final RegistrySupplier<BlockEntityType<SeedBoxBlockEntity>> SEED_BOX_BLOCK_ENTITY =
			BLOCK_ENTITY_TYPES.register(SEED_BOX_ID.getPath(),
					() -> blockEntityType(SeedBoxBlockEntity::new, SEED_BOX_BLOCK.get()));

	public static final RegistrySupplier<EntityType<RancherEntity>> RANCHER = ENTITY_TYPES.register(RANCHER_ID.getPath(),
			() -> EntityType.Builder.<RancherEntity>of(RancherEntity::new, MobCategory.MISC)
					.sized(0.6F, 1.95F)
					.clientTrackingRange(10)
					.build(ResourceKey.create(Registries.ENTITY_TYPE, RANCHER_ID)));
	public static final RegistrySupplier<EntityType<FarmerEntity>> FARMER = ENTITY_TYPES.register(FARMER_ID.getPath(),
			() -> EntityType.Builder.<FarmerEntity>of(FarmerEntity::new, MobCategory.MISC)
					.sized(0.6F, 1.95F)
					.clientTrackingRange(10)
					.build(ResourceKey.create(Registries.ENTITY_TYPE, FARMER_ID)));
	public static final RegistrySupplier<EntityType<LumberjackEntity>> LUMBERJACK = ENTITY_TYPES.register(LUMBERJACK_ID.getPath(),
			() -> EntityType.Builder.<LumberjackEntity>of(LumberjackEntity::new, MobCategory.MISC)
					.sized(0.6F, 1.95F)
					.clientTrackingRange(10)
					.build(ResourceKey.create(Registries.ENTITY_TYPE, LUMBERJACK_ID)));

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

	private static <T extends BlockEntity> BlockEntityType<T> blockEntityType(
			BlockEntityType.BlockEntitySupplier<T> factory, Block block) {
		return new BlockEntityType<>(factory, Set.of(block));
	}

	public static void init() {
		ModConfig.get();

		BLOCKS.register();
		ITEMS.register();
		BLOCK_ENTITY_TYPES.register();
		ENTITY_TYPES.register();
		ITEM_GROUPS.register();

		EntityAttributeRegistry.register(RANCHER, RancherEntity::createRancherAttributes);
		EntityAttributeRegistry.register(FARMER, FarmerEntity::createFarmerAttributes);
		EntityAttributeRegistry.register(LUMBERJACK, LumberjackEntity::createLumberjackAttributes);

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
			BlockState state = player.level().getBlockState(pos);

			if (!player.isShiftKeyDown() || !(state.is(RANCH_BLOCK.get()) || state.is(FARM_BLOCK.get())
					|| state.is(LUMBER_BLOCK.get()))) {
				return InteractionResult.PASS;
			}

			if (player instanceof ServerPlayer serverPlayer
					&& player.level().getBlockEntity(pos) instanceof WorkStationBlockEntity<?, ?>) {
				StationNetworking.openScreen(serverPlayer, pos);
			}

			return InteractionResult.SUCCESS;
		});
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
