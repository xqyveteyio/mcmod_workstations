package dev.keyboard.workstations.block;

import dev.keyboard.workstations.WorkstationsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ChestLidController;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Somewhere for a farm station's seed to live that is not the same shelves its produce lands on.
 *
 * <p>Two chests' worth of room, and nothing like a chest underneath it. A double chest is a single
 * container answering to two positions, which a station sweeping its area for boxes would find
 * twice and have to reason about. A plain block with an inventory has none of that: two set side by
 * side stay two boxes, each counted once.
 *
 * <p>The lid is a chest's lid all the same, opened and shut the way vanilla does it, which is why
 * the viewer counting below is worth its length. Nothing but the count crosses to the client: the
 * lid's actual angle is worked out there from how long it has been open.
 */
public class SeedBoxBlockEntity extends RandomizableContainerBlockEntity implements LidBlockEntity {
	/** Slots. Two chests' worth, which is about what a field's seed and its returns come to. */
	public static final int INVENTORY_SIZE = 54;

	/** The block event that carries a changed viewer count out to everyone watching. */
	private static final int VIEWER_COUNT_EVENT = 1;

	private NonNullList<ItemStack> inventory = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
	private final ChestLidController lid = new ChestLidController();

	private final ContainerOpenersCounter viewers = new ContainerOpenersCounter() {
		@Override
		protected void onOpen(Level world, BlockPos pos, BlockState state) {
			creak(world, pos, SoundEvents.CHEST_OPEN);
		}

		@Override
		protected void onClose(Level world, BlockPos pos, BlockState state) {
			creak(world, pos, SoundEvents.CHEST_CLOSE);
		}

		@Override
		protected void openerCountChanged(Level world, BlockPos pos, BlockState state, int from, int to) {
			world.blockEvent(pos, state.getBlock(), VIEWER_COUNT_EVENT, to);
		}

		@Override
		public boolean isOwnContainer(Player player) {
			return player.containerMenu instanceof ChestMenu open
					&& open.getContainer() == SeedBoxBlockEntity.this;
		}
	};

	public SeedBoxBlockEntity(BlockPos pos, BlockState state) {
		super(WorkstationsMod.SEED_BOX_BLOCK_ENTITY, pos, state);
	}

	/** Runs on the client alone, because the lid's angle is the one thing only the client draws. */
	public static void clientTick(Level world, BlockPos pos, BlockState state, SeedBoxBlockEntity box) {
		box.lid.tickLid();
	}

	@Override
	public float getOpenNess(float tickDelta) {
		return lid.getOpenness(tickDelta);
	}

	@Override
	public boolean triggerEvent(int type, int data) {
		if (type != VIEWER_COUNT_EVENT) {
			return super.triggerEvent(type, data);
		}

		lid.shouldBeOpen(data > 0);
		return true;
	}

	@Override
	public void startOpen(ContainerUser user) {
		if (level != null && !remove && user.getLivingEntity() instanceof Player player
				&& !player.isSpectator()) {
			viewers.incrementOpeners(player, level, worldPosition, getBlockState(),
					user.getContainerInteractionRange());
		}
	}

	@Override
	public void stopOpen(ContainerUser user) {
		if (level != null && !remove && user.getLivingEntity() instanceof Player player
				&& !player.isSpectator()) {
			viewers.decrementOpeners(player, level, worldPosition, getBlockState());
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
		if (level != null && !remove) {
			viewers.recheckOpeners(level, worldPosition, getBlockState());
		}
	}

	private void creak(Level world, BlockPos pos, SoundEvent sound) {
		world.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, sound,
				SoundSource.BLOCKS, 0.5F, world.getRandom().nextFloat() * 0.1F + 0.9F);
	}

	@Override
	public int getContainerSize() {
		return INVENTORY_SIZE;
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("container.keyboard_workstations.seed_box");
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return inventory;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> list) {
		inventory = list;
	}

	@Override
	protected AbstractContainerMenu createMenu(int syncId, Inventory playerInventory) {
		return ChestMenu.sixRows(syncId, playerInventory, this);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);

		if (!trySaveLootTable(output)) {
			ContainerHelper.saveAllItems(output, inventory);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		inventory = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);

		if (!tryLoadLootTable(input)) {
			ContainerHelper.loadAllItems(input, inventory);
		}
	}
}
