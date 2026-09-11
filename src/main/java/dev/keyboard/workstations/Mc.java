package dev.keyboard.workstations;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.data.TrackedDataHandler;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
//? if >=1.19.3 {
import net.minecraft.registry.Registries;
//?} else {
/* import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.registry.Registry; */
//?}

/**
 * Yarn names that moved between the versions this project builds, kept in one place so the rest of
 * the source can read as one program.
 */
public final class Mc {
	/** NBT compound tag type, which is 10 in every version the format has existed. */
	public static final int NBT_COMPOUND = 10;
	/** NBT int tag type. */
	public static final int NBT_INT = 3;
	/** NBT list tag type. */
	public static final int NBT_LIST = 9;
	/** NBT byte tag type, which is also how a boolean is stored. */
	public static final int NBT_BYTE = 1;

	/**
	 * Neighbours should be told. The named constant only arrived in 1.17; the value is the same
	 * flag vanilla always used.
	 */
	public static final int NOTIFY_LISTENERS = 2;
	/** Neighbours and clients should both be told. */
	public static final int NOTIFY_ALL = 3;

	private Mc() {
	}

	public static MutableText translatable(String key, Object... args) {
		//? if >=1.19 {
		return Text.translatable(key, args);
		//?} else {
		/* return new TranslatableText(key, args); */
		//?}
	}

	public static MutableText literal(String text) {
		//? if >=1.19 {
		return Text.literal(text);
		//?} else {
		/* return new LiteralText(text); */
		//?}
	}

	public static Text empty() {
		//? if >=1.19 {
		return Text.empty();
		//?} else {
		/* return LiteralText.EMPTY; */
		//?}
	}

	/** The world an entity is in. 1.17 folded the public field into a getter. */
	public static World world(Entity entity) {
		//? if >=26.1 {
		/* return entity.level(); */
		//?} elif >=1.17 {
		return entity.getWorld();
		//?} else {
		/* return entity.world; */
		//?}
	}

	public static World world(net.minecraft.block.entity.BlockEntity blockEntity) {
		//? if >=26.1 {
		/* return blockEntity.getLevel(); */
		//?} else {
		return blockEntity.getWorld();
		//?}
	}

	public static net.minecraft.util.math.BlockPos pos(net.minecraft.block.entity.BlockEntity blockEntity) {
		//? if >=26.1 {
		/* return blockEntity.getBlockPos(); */
		//?} else {
		return blockEntity.getPos();
		//?}
	}

	public static net.minecraft.block.BlockState cached(net.minecraft.block.entity.BlockEntity blockEntity) {
		//? if >=26.1 {
		/* return blockEntity.getBlockState(); */
		//?} else {
		return blockEntity.getCachedState();
		//?}
	}

	public static Identifier itemId(Item item) {
		//? if >=26.1 {
		/* return Registries.ITEM.getKey(item); */
		//?} elif >=1.19.3 {
		return Registries.ITEM.getId(item);
		//?} else {
		/* return Registry.ITEM.getId(item); */
		//?}
	}

	public static Item item(Identifier id) {
		//? if >=26.1 {
		/* return Registries.ITEM.getValue(id); */
		//?} elif >=1.19.3 {
		return Registries.ITEM.get(id);
		//?} else {
		/* return Registry.ITEM.get(id); */
		//?}
	}

	public static boolean hasItem(Identifier id) {
		//? if >=26.1 {
		/* return Registries.ITEM.containsKey(id); */
		//?} elif >=1.19.3 {
		return Registries.ITEM.containsId(id);
		//?} else {
		/* return Registry.ITEM.containsId(id); */
		//?}
	}

	public static Identifier blockId(Block block) {
		//? if >=26.1 {
		/* return Registries.BLOCK.getKey(block); */
		//?} elif >=1.19.3 {
		return Registries.BLOCK.getId(block);
		//?} else {
		/* return Registry.BLOCK.getId(block); */
		//?}
	}

	public static SoundEvent soundEvent(Identifier id) {
		//? if >=26.1 {
		/* return Registries.SOUND_EVENT.getValue(id); */
		//?} elif >=1.19.3 {
		return Registries.SOUND_EVENT.get(id);
		//?} else {
		/* return Registry.SOUND_EVENT.get(id); */
		//?}
	}

	public static boolean isOf(ItemStack stack, Item item) {
		//? if >=26.1 {
		/* return stack.is(item); */
		//?} elif >=1.18.2 {
		return stack.isOf(item);
		//?} else {
		/* return stack.getItem() == item; */
		//?}
	}

	public static Direction horizontalFacing(ItemPlacementContext ctx) {
		//? if >=26.1 {
		/* return ctx.getHorizontalDirection(); */
		//?} elif >=1.17 {
		return ctx.getHorizontalPlayerFacing();
		//?} else {
		/* return ctx.getPlayerFacing(); */
		//?}
	}

	public static int bottomY(World world) {
		//? if >=26.1 {
		/* return world.getMinY(); */
		//?} elif >=1.17 {
		return world.getBottomY();
		//?} else {
		/* return 0; */
		//?}
	}

	public static int topY(World world) {
		//? if >=26.1 {
		/* return world.getMinY() + world.getHeight(); */
		//?} elif >=1.17 {
		return world.getTopY();
		//?} else {
		/* return world.getHeight(); */
		//?}
	}

	public static boolean removed(Entity entity) {
		//? if >=1.17 {
		return entity.isRemoved();
		//?} else {
		/* return entity.removed; */
		//?}
	}

	public static int entityId(Entity entity) {
		//? if >=1.17 {
		return entity.getId();
		//?} else {
		/* return entity.getEntityId(); */
		//?}
	}

	public static void discard(Entity entity) {
		//? if >=1.17 {
		entity.discard();
		//?} else {
		/* entity.remove(); */
		//?}
	}

	public static TrackedDataHandler<NbtCompound> nbtTracker() {
		//? if >=26.1 {
		/* return net.minecraft.network.syncher.EntityDataSerializer.forValueType(
				net.minecraft.network.codec.ByteBufCodecs.COMPOUND_TAG); */
		//?} elif >=1.17 {
		return TrackedDataHandlerRegistry.NBT_COMPOUND;
		//?} else {
		/* return TrackedDataHandlerRegistry.TAG_COMPOUND; */
		//?}
	}

	public static boolean stacksMatch(ItemStack a, ItemStack b) {
		//? if >=26.1 {
		/* return ItemStack.isSameItemSameComponents(a, b); */
		//?} elif >=1.21 {
		/* return ItemStack.areItemsAndComponentsEqual(a, b); */
		//?} elif >=1.17 {
		return ItemStack.canCombine(a, b);
		//?} else {
		/* return ItemStack.areItemsEqual(a, b); */
		//?}
	}

	public static boolean creative(net.minecraft.entity.player.PlayerEntity player) {
		//? if >=26.1 {
		/* return player.getAbilities().instabuild; */
		//?} elif >=1.17 {
		return player.getAbilities().creativeMode;
		//?} else {
		/* return player.abilities.creativeMode; */
		//?}
	}

	public static int slots(net.minecraft.inventory.Inventory inventory) {
		//? if >=26.1 {
		/* return inventory.getContainerSize(); */
		//?} else {
		return inventory.size();
		//?}
	}

	public static ItemStack stack(net.minecraft.inventory.Inventory inventory, int slot) {
		//? if >=26.1 {
		/* return inventory.getItem(slot); */
		//?} else {
		return inventory.getStack(slot);
		//?}
	}

	public static void stack(net.minecraft.inventory.Inventory inventory, int slot, ItemStack stack) {
		//? if >=26.1 {
		/* inventory.setItem(slot, stack); */
		//?} else {
		inventory.setStack(slot, stack);
		//?}
	}

	public static ItemStack take(net.minecraft.inventory.Inventory inventory, int slot, int count) {
		//? if >=26.1 {
		/* return inventory.removeItem(slot, count); */
		//?} else {
		return inventory.removeStack(slot, count);
		//?}
	}

	public static void dirty(net.minecraft.inventory.Inventory inventory) {
		//? if >=26.1 {
		/* inventory.setChanged(); */
		//?} else {
		inventory.markDirty();
		//?}
	}

	public static boolean has(NbtCompound nbt, String key, int type) {
		//? if >=26.1 {
		/* return nbt.contains(key); */
		//?} else {
		return nbt.contains(key, type);
		//?}
	}

	public static NbtCompound compound(NbtCompound nbt, String key) {
		//? if >=26.1 {
		/* return nbt.getCompoundOrEmpty(key); */
		//?} else {
		return nbt.getCompound(key);
		//?}
	}

	public static int integer(NbtCompound nbt, String key) {
		//? if >=26.1 {
		/* return nbt.getIntOr(key, 0); */
		//?} else {
		return nbt.getInt(key);
		//?}
	}

	public static void putUuid(NbtCompound nbt, String key, java.util.UUID uuid) {
		//? if >=26.1 {
		/* nbt.store(key, net.minecraft.core.UUIDUtil.CODEC, uuid); */
		//?} else {
		nbt.putUuid(key, uuid);
		//?}
	}

	public static boolean hasUuid(NbtCompound nbt, String key) {
		//? if >=26.1 {
		/* return nbt.contains(key); */
		//?} else {
		return nbt.containsUuid(key);
		//?}
	}

	public static java.util.UUID uuid(NbtCompound nbt, String key) {
		//? if >=26.1 {
		/* return nbt.read(key, net.minecraft.core.UUIDUtil.CODEC).orElse(null); */
		//?} else {
		return nbt.getUuid(key);
		//?}
	}

	public static void tell(net.minecraft.entity.player.PlayerEntity player, Text text, boolean overlay) {
		//? if >=26.1 {
		/* if (overlay) {
			player.sendOverlayMessage(text);
		} else {
			player.sendSystemMessage(text);
		} */
		//?} else {
		player.sendMessage(text, overlay);
		//?}
	}

	public static net.minecraft.entity.player.PlayerInventory inventory(net.minecraft.entity.player.PlayerEntity player) {
		//? if >=1.17 {
		return player.getInventory();
		//?} else {
		/* return player.inventory; */
		//?}
	}

	public static int boxMinX(net.minecraft.util.math.BlockBox box) {
		//? if >=1.17 {
		return box.getMinX();
		//?} else {
		/* return box.minX; */
		//?}
	}

	public static int boxMinY(net.minecraft.util.math.BlockBox box) {
		//? if >=1.17 {
		return box.getMinY();
		//?} else {
		/* return box.minY; */
		//?}
	}

	public static int boxMinZ(net.minecraft.util.math.BlockBox box) {
		//? if >=1.17 {
		return box.getMinZ();
		//?} else {
		/* return box.minZ; */
		//?}
	}

	public static int boxMaxX(net.minecraft.util.math.BlockBox box) {
		//? if >=1.17 {
		return box.getMaxX();
		//?} else {
		/* return box.maxX; */
		//?}
	}

	public static int boxMaxY(net.minecraft.util.math.BlockBox box) {
		//? if >=1.17 {
		return box.getMaxY();
		//?} else {
		/* return box.maxY; */
		//?}
	}

	public static int boxMaxZ(net.minecraft.util.math.BlockBox box) {
		//? if >=1.17 {
		return box.getMaxZ();
		//?} else {
		/* return box.maxZ; */
		//?}
	}
}
