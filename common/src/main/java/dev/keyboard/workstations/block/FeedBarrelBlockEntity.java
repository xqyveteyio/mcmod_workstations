package dev.keyboard.workstations.block;

import dev.keyboard.workstations.WorkstationsMod;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.ChestLidAnimator;
import net.minecraft.block.entity.LidOpenable;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.block.entity.ViewerCountManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Somewhere for a ranch's feed to live that is not the same shelves its produce lands on.
 *
 * <p>Two chests' worth of room, and nothing like a chest underneath it. A double chest is a single
 * container answering to two positions, which a station sweeping its area for boxes would find
 * twice and have to reason about. A plain block with an inventory has none of that: two set side by
 * side stay two boxes, each counted once.
 *
 * <p>The lid is a chest's lid all the same, opened and shut the way vanilla does it, which is why
 * the viewer counting below is worth its length. Nothing but the count crosses to the client: the
 * lid's actual angle is worked out there from how long it has been open.
 *
 * <p>The rancher draws from here before the station, so stocking the box is enough; feed left in
 * the station by hand is still used when the box is empty or missing.
 */
public class FeedBarrelBlockEntity extends LootableContainerBlockEntity implements LidOpenable {
	/** Slots. Two chests' worth, the same as the seed box. */
	public static final int INVENTORY_SIZE = 54;

	/** The block event that carries a changed viewer count out to everyone watching. */
	private static final int VIEWER_COUNT_EVENT = 1;

	private DefaultedList<ItemStack> inventory = DefaultedList.ofSize(INVENTORY_SIZE, ItemStack.EMPTY);
	private final ChestLidAnimator lid = new ChestLidAnimator();

	private final ViewerCountManager viewers = new ViewerCountManager() {
		@Override
		protected void onContainerOpen(World world, BlockPos pos, BlockState state) {
			creak(world, pos, SoundEvents.BLOCK_CHEST_OPEN);
		}

		@Override
		protected void onContainerClose(World world, BlockPos pos, BlockState state) {
			creak(world, pos, SoundEvents.BLOCK_CHEST_CLOSE);
		}

		@Override
		protected void onViewerCountUpdate(World world, BlockPos pos, BlockState state, int from, int to) {
			world.addSyncedBlockEvent(pos, state.getBlock(), VIEWER_COUNT_EVENT, to);
		}

		@Override
		protected boolean isPlayerViewing(PlayerEntity player) {
			return player.currentScreenHandler instanceof GenericContainerScreenHandler open
					&& open.getInventory() == FeedBarrelBlockEntity.this;
		}
	};

	public FeedBarrelBlockEntity(BlockPos pos, BlockState state) {
		super(WorkstationsMod.FEED_BARREL_BLOCK_ENTITY.get(), pos, state);
	}

	/** Runs on the client alone, because the lid's angle is the one thing only the client draws. */
	public static void clientTick(World world, BlockPos pos, BlockState state, FeedBarrelBlockEntity box) {
		box.lid.step();
	}

	@Override
	public float getAnimationProgress(float tickDelta) {
		return lid.getProgress(tickDelta);
	}

	@Override
	public boolean onSyncedBlockEvent(int type, int data) {
		if (type != VIEWER_COUNT_EVENT) {
			return super.onSyncedBlockEvent(type, data);
		}

		lid.setOpen(data > 0);
		return true;
	}

	@Override
	public void onOpen(PlayerEntity player) {
		if (world != null && !removed && !player.isSpectator()) {
			viewers.openContainer(player, world, pos, getCachedState());
		}
	}

	@Override
	public void onClose(PlayerEntity player) {
		if (world != null && !removed && !player.isSpectator()) {
			viewers.closeContainer(player, world, pos, getCachedState());
		}
	}

	/**
	 * Recounts who is looking in.
	 *
	 * <p>Worth doing on a scheduled tick because a chest can be left open: a player who logs out
	 * with the screen up, or one whose chunk goes away, never closes it, and the count would then
	 * hold the lid up forever.
	 */
	public void recountViewers() {
		if (world != null && !removed) {
			viewers.updateViewerCount(world, pos, getCachedState());
		}
	}

	private void creak(World world, BlockPos pos, SoundEvent sound) {
		world.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, sound,
				SoundCategory.BLOCKS, 0.5F, world.random.nextFloat() * 0.1F + 0.9F);
	}

	@Override
	public int size() {
		return INVENTORY_SIZE;
	}

	@Override
	protected Text getContainerName() {
		return Text.translatable("container.villager_workstations.feed_barrel");
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
		return GenericContainerScreenHandler.createGeneric9x6(syncId, playerInventory, this);
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
