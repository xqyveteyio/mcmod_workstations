package dev.keyboard.workstations.block;

import dev.keyboard.workstations.WorkstationsMod;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;

/**
 * Somewhere for a ranch's feed to live that is not the same shelves its produce lands on.
 *
 * <p>One chest's worth of room: enough for mixed helpings of wheat, seed and the crafted feed,
 * and small enough that a trough reading as a block does not pretend to be a warehouse. The
 * rancher draws from here before the station, so stocking the trough is enough; feed left in
 * the station by hand is still used when the trough is empty or missing.
 */
public class FeedBarrelBlockEntity extends LootableContainerBlockEntity {
	public static final int INVENTORY_SIZE = 27;

	private DefaultedList<ItemStack> inventory = DefaultedList.ofSize(INVENTORY_SIZE, ItemStack.EMPTY);

	public FeedBarrelBlockEntity(BlockPos pos, BlockState state) {
		super(WorkstationsMod.FEED_BARREL_BLOCK_ENTITY, pos, state);
	}

	@Override
	public int size() {
		return INVENTORY_SIZE;
	}

	@Override
	protected Text getContainerName() {
		return Text.translatable("container.keyboard_workstations.feed_barrel");
	}

	@Override
	protected DefaultedList<ItemStack> getInvStackList() {
		return inventory;
	}

	@Override
	protected void setInvStackList(DefaultedList<ItemStack> list) {
		inventory = list;
	}

	@Override
	protected ScreenHandler createScreenHandler(int syncId, PlayerInventory playerInventory) {
		return GenericContainerScreenHandler.createGeneric9x3(syncId, playerInventory, this);
	}

	@Override
	public void onOpen(PlayerEntity player) {
		if (world != null && !removed && !player.isSpectator()) {
			world.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
					SoundEvents.BLOCK_BARREL_OPEN, SoundCategory.BLOCKS, 0.5F,
					world.random.nextFloat() * 0.1F + 0.9F);
		}

		super.onOpen(player);
	}

	@Override
	public void onClose(PlayerEntity player) {
		if (world != null && !removed && !player.isSpectator()) {
			world.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
					SoundEvents.BLOCK_BARREL_CLOSE, SoundCategory.BLOCKS, 0.5F,
					world.random.nextFloat() * 0.1F + 0.9F);
		}

		super.onClose(player);
	}

	@Override
	public void markDirty() {
		super.markDirty();

		if (world != null && !world.isClient) {
			FeedBarrelBlock.showLevel(world, pos, getCachedState(), shown());
			world.updateComparators(pos, getCachedState().getBlock());
		}
	}

	/** Quarters of capacity, or zero when the trough is empty. */
	private int shown() {
		int items = 0;
		int capacity = 0;

		for (ItemStack stack : inventory) {
			capacity += stack.isEmpty() ? 64 : stack.getMaxCount();
			items += stack.getCount();
		}

		if (items <= 0 || capacity <= 0) {
			return 0;
		}

		return Math.min(4, Math.max(1, (items * 4 + capacity - 1) / capacity));
	}

	@Override
	protected void writeNbt(NbtCompound nbt) {
		super.writeNbt(nbt);

		if (!serializeLootTable(nbt)) {
			Inventories.writeNbt(nbt, inventory);
		}
	}

	@Override
	public void readNbt(NbtCompound nbt) {
		super.readNbt(nbt);
		inventory = DefaultedList.ofSize(INVENTORY_SIZE, ItemStack.EMPTY);

		if (!deserializeLootTable(nbt)) {
			Inventories.readNbt(nbt, inventory);
		}
	}
}
