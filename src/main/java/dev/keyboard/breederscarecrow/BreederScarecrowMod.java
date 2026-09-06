package dev.keyboard.breederscarecrow;

import dev.keyboard.breederscarecrow.block.ScarecrowBlock;
import dev.keyboard.breederscarecrow.block.ScarecrowBlockEntity;
import dev.keyboard.breederscarecrow.pen.PenWatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.piston.PistonBehavior;
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

	@Override
	public void onInitialize() {
		ModConfig.get();

		Registry.register(Registries.BLOCK, SCARECROW_ID, SCARECROW_BLOCK);
		Registry.register(Registries.ITEM, SCARECROW_ID, SCARECROW_ITEM);
		SCARECROW_BLOCK_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, SCARECROW_ID,
				FabricBlockEntityTypeBuilder.create(ScarecrowBlockEntity::new, SCARECROW_BLOCK).build());

		ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> entries.add(SCARECROW_ITEM));
		PenWatcher.register();

		LOGGER.info("Breeder Scarecrow initialized");
	}

	public static Identifier id(String path) {
		return new Identifier(MOD_ID, path);
	}
}
