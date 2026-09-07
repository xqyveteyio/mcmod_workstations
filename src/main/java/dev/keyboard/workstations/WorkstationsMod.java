package dev.keyboard.workstations;

import dev.keyboard.workstations.block.FarmBlock;
import dev.keyboard.workstations.block.FarmBlockEntity;
import dev.keyboard.workstations.block.ScarecrowBlock;
import dev.keyboard.workstations.block.ScarecrowBlockEntity;
import dev.keyboard.workstations.block.WorkStationBlockEntity;
import dev.keyboard.workstations.entity.FarmerEntity;
import dev.keyboard.workstations.entity.RancherEntity;
import dev.keyboard.workstations.network.StationNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
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
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkstationsMod implements ModInitializer {
	public static final String MOD_ID = "workstations";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final Identifier SCARECROW_ID = id("scarecrow");
	public static final Identifier RANCHER_ID = id("rancher");
	public static final Identifier FARM_ID = id("farm_station");
	public static final Identifier FARMER_ID = id("farmer");

	public static final ScarecrowBlock SCARECROW_BLOCK = new ScarecrowBlock(stationSettings());
	public static final FarmBlock FARM_BLOCK = new FarmBlock(stationSettings());

	public static final BlockItem SCARECROW_ITEM = new BlockItem(SCARECROW_BLOCK, new Item.Settings());
	public static final BlockItem FARM_ITEM = new BlockItem(FARM_BLOCK, new Item.Settings());

	public static BlockEntityType<ScarecrowBlockEntity> SCARECROW_BLOCK_ENTITY;
	public static BlockEntityType<FarmBlockEntity> FARM_BLOCK_ENTITY;
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

	@Override
	public void onInitialize() {
		ModConfig.get();

		Registry.register(Registries.BLOCK, SCARECROW_ID, SCARECROW_BLOCK);
		Registry.register(Registries.ITEM, SCARECROW_ID, SCARECROW_ITEM);
		SCARECROW_BLOCK_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, SCARECROW_ID,
				FabricBlockEntityTypeBuilder.create(ScarecrowBlockEntity::new, SCARECROW_BLOCK).build());

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

		StationNetworking.registerServerReceivers();
		registerSettingsGesture();

		ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> {
			entries.add(SCARECROW_ITEM);
			entries.add(FARM_ITEM);
		});

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

			if (!player.isSneaking() || !(state.isOf(SCARECROW_BLOCK) || state.isOf(FARM_BLOCK))) {
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
