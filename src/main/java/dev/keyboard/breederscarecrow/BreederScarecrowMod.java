package dev.keyboard.breederscarecrow;

import dev.keyboard.breederscarecrow.block.ScarecrowBlock;
import dev.keyboard.breederscarecrow.block.ScarecrowBlockEntity;
import dev.keyboard.breederscarecrow.entity.RancherEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.block.AbstractBlock;
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
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BreederScarecrowMod implements ModInitializer {
	public static final String MOD_ID = "breeder_scarecrow";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final Identifier SCARECROW_ID = id("scarecrow");
	public static final Identifier RANCHER_ID = id("rancher");

	public static final ScarecrowBlock SCARECROW_BLOCK = new ScarecrowBlock(AbstractBlock.Settings.create()
			.mapColor(MapColor.OAK_TAN)
			.strength(1.5F)
			.sounds(BlockSoundGroup.WOOD)
			.burnable()
			.nonOpaque()
			.pistonBehavior(PistonBehavior.DESTROY)
			.solidBlock((state, world, pos) -> false));

	public static final BlockItem SCARECROW_ITEM = new BlockItem(SCARECROW_BLOCK, new Item.Settings());

	public static BlockEntityType<ScarecrowBlockEntity> SCARECROW_BLOCK_ENTITY;
	public static EntityType<RancherEntity> RANCHER;

	@Override
	public void onInitialize() {
		ModConfig.get();

		Registry.register(Registries.BLOCK, SCARECROW_ID, SCARECROW_BLOCK);
		Registry.register(Registries.ITEM, SCARECROW_ID, SCARECROW_ITEM);
		SCARECROW_BLOCK_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, SCARECROW_ID,
				FabricBlockEntityTypeBuilder.create(ScarecrowBlockEntity::new, SCARECROW_BLOCK).build());

		// No spawn egg and no natural spawning: the station is the only thing that makes a rancher.
		RANCHER = Registry.register(Registries.ENTITY_TYPE, RANCHER_ID,
				EntityType.Builder.<RancherEntity>create(RancherEntity::new, SpawnGroup.MISC)
						.setDimensions(0.6F, 1.95F)
						.maxTrackingRange(10)
						.build(RANCHER_ID.getPath()));
		FabricDefaultAttributeRegistry.register(RANCHER, RancherEntity.createRancherAttributes());

		ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> entries.add(SCARECROW_ITEM));

		LOGGER.info("Breeder Scarecrow initialized");
	}

	public static Identifier id(String path) {
		return new Identifier(MOD_ID, path);
	}
}
