package dev.keyboard.workstations;

import dev.keyboard.workstations.block.FarmBlock;
import dev.keyboard.workstations.block.FarmBlockEntity;
import dev.keyboard.workstations.block.MilkBarrelBlock;
import dev.keyboard.workstations.block.MilkBarrelBlockEntity;
import dev.keyboard.workstations.block.RanchBlock;
import dev.keyboard.workstations.block.RanchBlockEntity;
import dev.keyboard.workstations.block.SeedBoxBlock;
import dev.keyboard.workstations.block.SeedBoxBlockEntity;
import dev.keyboard.workstations.block.WorkStationBlockEntity;
import dev.keyboard.workstations.entity.FarmerEntity;
import dev.keyboard.workstations.entity.RancherEntity;
import dev.keyboard.workstations.network.StationNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.block.AbstractBlock;
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
import net.minecraft.util.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class WorkstationsMod implements ModInitializer {
	public static final String MOD_ID = "keyboard_workstations";
	public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

	public static final Identifier RANCH_ID = id("ranch_station");
	public static final Identifier RANCHER_ID = id("rancher");
	public static final Identifier FARM_ID = id("farm_station");
	public static final Identifier FARMER_ID = id("farmer");
	public static final Identifier MILK_BARREL_ID = id("milk_barrel");
	public static final Identifier SEED_BOX_ID = id("seed_box");
	public static final Identifier UNIVERSAL_FEED_ID = id("universal_feed");
	public static final Identifier GROUP_ID = id("keyboard_workstations");

	public static final RanchBlock RANCH_BLOCK = new RanchBlock(stationSettings());
	public static final FarmBlock FARM_BLOCK = new FarmBlock(stationSettings());
	public static final MilkBarrelBlock MILK_BARREL_BLOCK = new MilkBarrelBlock(barrelSettings());
	public static final SeedBoxBlock SEED_BOX_BLOCK = new SeedBoxBlock(chestSettings());

	public static final BlockItem RANCH_ITEM = new BlockItem(RANCH_BLOCK, new Item.Settings());
	public static final BlockItem FARM_ITEM = new BlockItem(FARM_BLOCK, new Item.Settings());
	public static final BlockItem MILK_BARREL_ITEM = new BlockItem(MILK_BARREL_BLOCK, new Item.Settings());
	public static final BlockItem SEED_BOX_ITEM = new BlockItem(SEED_BOX_BLOCK, new Item.Settings());
	public static final Item UNIVERSAL_FEED = new Item(new Item.Settings());
public static final ItemGroup GROUP = FabricItemGroupBuilder.create(
WorkstationsMod.id("keyboard_workstations"))
.icon(() -> new ItemStack(RANCH_ITEM))
.appendItems(stacks -> {
stacks.add(new ItemStack(RANCH_ITEM));
stacks.add(new ItemStack(FARM_ITEM));
stacks.add(new ItemStack(MILK_BARREL_ITEM));
stacks.add(new ItemStack(SEED_BOX_ITEM));
stacks.add(new ItemStack(UNIVERSAL_FEED));
})
.build();



public static BlockEntityType<RanchBlockEntity> RANCH_BLOCK_ENTITY;
	public static BlockEntityType<FarmBlockEntity> FARM_BLOCK_ENTITY;
	public static BlockEntityType<MilkBarrelBlockEntity> MILK_BARREL_BLOCK_ENTITY;
	public static BlockEntityType<SeedBoxBlockEntity> SEED_BOX_BLOCK_ENTITY;
	public static EntityType<RancherEntity> RANCHER;
	public static EntityType<FarmerEntity> FARMER;

	/** Both stations are the same wooden table underneath, so they are built the same way. */
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

	@Override
	public void onInitialize() {
		ModConfig.get();

		Registry.register(Registry.BLOCK, RANCH_ID, RANCH_BLOCK);
		Registry.register(Registry.ITEM, RANCH_ID, RANCH_ITEM);
		RANCH_BLOCK_ENTITY = Registry.register(Registry.BLOCK_ENTITY_TYPE, RANCH_ID,
				BlockEntityType.Builder.create(RanchBlockEntity::new, RANCH_BLOCK).build(null));

		Registry.register(Registry.BLOCK, FARM_ID, FARM_BLOCK);
		Registry.register(Registry.ITEM, FARM_ID, FARM_ITEM);
		FARM_BLOCK_ENTITY = Registry.register(Registry.BLOCK_ENTITY_TYPE, FARM_ID,
				BlockEntityType.Builder.create(FarmBlockEntity::new, FARM_BLOCK).build(null));

		// No spawn egg and no natural spawning: a station is the only thing that makes a worker.
		RANCHER = Registry.register(Registry.ENTITY_TYPE, RANCHER_ID,
				EntityType.Builder.<RancherEntity>create(RancherEntity::new, SpawnGroup.MISC)
						.setDimensions(0.6F, 1.95F)
						.maxTrackingRange(10)
						.build(RANCHER_ID.getPath()));
		FabricDefaultAttributeRegistry.register(RANCHER, RancherEntity.createRancherAttributes());

		FARMER = Registry.register(Registry.ENTITY_TYPE, FARMER_ID,
				EntityType.Builder.<FarmerEntity>create(FarmerEntity::new, SpawnGroup.MISC)
						.setDimensions(0.6F, 1.95F)
						.maxTrackingRange(10)
						.build(FARMER_ID.getPath()));
		FabricDefaultAttributeRegistry.register(FARMER, FarmerEntity.createFarmerAttributes());

		Registry.register(Registry.BLOCK, MILK_BARREL_ID, MILK_BARREL_BLOCK);
		Registry.register(Registry.ITEM, MILK_BARREL_ID, MILK_BARREL_ITEM);
		MILK_BARREL_BLOCK_ENTITY = Registry.register(Registry.BLOCK_ENTITY_TYPE, MILK_BARREL_ID,
				BlockEntityType.Builder.create(MilkBarrelBlockEntity::new, MILK_BARREL_BLOCK).build(null));

		Registry.register(Registry.BLOCK, SEED_BOX_ID, SEED_BOX_BLOCK);
		Registry.register(Registry.ITEM, SEED_BOX_ID, SEED_BOX_ITEM);
		SEED_BOX_BLOCK_ENTITY = Registry.register(Registry.BLOCK_ENTITY_TYPE, SEED_BOX_ID,
				BlockEntityType.Builder.create(SeedBoxBlockEntity::new, SEED_BOX_BLOCK).build(null));

		Registry.register(Registry.ITEM, UNIVERSAL_FEED_ID, UNIVERSAL_FEED);

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

	public static Identifier id(String path) {
		return new Identifier(MOD_ID, path);
	}
}
