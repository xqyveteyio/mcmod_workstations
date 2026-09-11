package dev.keyboard.workstations.block;

import dev.keyboard.workstations.Mc;

import dev.keyboard.workstations.WorkstationsMod;
import net.minecraft.block.BlockState;
//? if >=1.17 {
import net.minecraft.block.entity.ChestLidAnimator;
import net.minecraft.block.entity.LidOpenable;
import net.minecraft.block.entity.ViewerCountManager;
//?} else {
/* import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.util.Tickable;
import net.minecraft.util.math.MathHelper; */
//?}
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
//? if >=1.20.5 {
/* import net.minecraft.registry.RegistryWrapper; */
//?}
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
public class SeedBoxBlockEntity extends LootableContainerBlockEntity implements AnimatedLid
		//? if >=1.17 {
		, LidOpenable
		//?} else {
		/* , Tickable */
		//?}
		{
	/** Slots. Two chests' worth, which is about what a field's seed and its returns come to. */
	public static final int INVENTORY_SIZE = 54;

	/** The block event that carries a changed viewer count out to everyone watching. */
	private static final int VIEWER_COUNT_EVENT = 1;

	private DefaultedList<ItemStack> inventory = DefaultedList.ofSize(INVENTORY_SIZE, ItemStack.EMPTY);
	//? if >=1.17 {
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
					&& open.getInventory() == SeedBoxBlockEntity.this;
		}
	};
	//?} else {
	/* private float animationAngle;
	private float lastAnimationAngle;
	private int viewerCount;
	private int ticksOpen; */
	//?}

	//? if >=1.17 {
	public SeedBoxBlockEntity(BlockPos pos, BlockState state) {
		super(WorkstationsMod.SEED_BOX_BLOCK_ENTITY, pos, state);
	}
	//?} else {
	/* public SeedBoxBlockEntity() {
		super(WorkstationsMod.SEED_BOX_BLOCK_ENTITY);
	} */
	//?}

	/** Runs on the client alone, because the lid's angle is the one thing only the client draws. */
	//? if >=1.17 {
	public static void clientTick(World world, BlockPos pos, BlockState state, SeedBoxBlockEntity box) {
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

	//? if >=26.1 {
	/* @Override
	public void startOpen(net.minecraft.world.entity.ContainerUser user) {
		if (user instanceof PlayerEntity player && Mc.world(this) != null && !isRemoved() && !player.isSpectator()) {
			viewers.incrementOpeners(player, Mc.world(this), Mc.pos(this), Mc.cached(this), player.getEyeY());
		}
	}

	@Override
	public void stopOpen(net.minecraft.world.entity.ContainerUser user) {
		if (user instanceof PlayerEntity player && Mc.world(this) != null && !isRemoved() && !player.isSpectator()) {
			viewers.decrementOpeners(player, Mc.world(this), Mc.pos(this), Mc.cached(this));
		}
	}

	public void recountViewers() {
		if (Mc.world(this) != null && !isRemoved()) {
			viewers.recheckOpeners(Mc.world(this), Mc.pos(this), Mc.cached(this));
		}
	}
	*/
	//?} else {
	@Override
	public void onOpen(PlayerEntity player) {
		if (world != null && !isRemoved() && !player.isSpectator()) {
			viewers.openContainer(player, world, pos, getCachedState());
		}
	}

	@Override
	public void onClose(PlayerEntity player) {
		if (world != null && !isRemoved() && !player.isSpectator()) {
			viewers.closeContainer(player, world, pos, getCachedState());
		}
	}

	public void recountViewers() {
		if (world != null && !isRemoved()) {
			viewers.updateViewerCount(world, pos, getCachedState());
		}
	}
	//?}
	//?} else {
	/* @Override
	public void tick() {
		BlockPos here = Mc.pos(this);
		ticksOpen++;
		viewerCount = ChestBlockEntity.tickViewerCount(world, this, ticksOpen,
				here.getX(), here.getY(), here.getZ(), viewerCount);
		lastAnimationAngle = animationAngle;

		if (viewerCount > 0 && animationAngle == 0.0F) {
			creak(world, here, SoundEvents.BLOCK_CHEST_OPEN);
		}

		if (viewerCount > 0 || animationAngle > 0.0F) {
			float previous = animationAngle;

			if (viewerCount > 0) {
				animationAngle += 0.1F;
			} else {
				animationAngle -= 0.1F;
			}

			if (animationAngle > 1.0F) {
				animationAngle = 1.0F;
			}

			if (animationAngle < 0.0F) {
				animationAngle = 0.0F;
			}

			if (previous < 0.5F && animationAngle >= 0.5F) {
				creak(world, here, SoundEvents.BLOCK_CHEST_CLOSE);
			}
		}
	}

	@Override
	public float getAnimationProgress(float tickDelta) {
		return MathHelper.lerp(tickDelta, lastAnimationAngle, animationAngle);
	}

	@Override
	public boolean onSyncedBlockEvent(int type, int data) {
		if (type != VIEWER_COUNT_EVENT) {
			return super.onSyncedBlockEvent(type, data);
		}

		viewerCount = data;
		return true;
	}

	@Override
	public void onOpen(PlayerEntity player) {
		if (world != null && !removed && !player.isSpectator()) {
			if (viewerCount < 0) {
				viewerCount = 0;
			}

			viewerCount++;
			world.addSyncedBlockEvent(pos, getCachedState().getBlock(), VIEWER_COUNT_EVENT, viewerCount);
			world.updateNeighborsAlways(pos, getCachedState().getBlock());
		}
	}

	@Override
	public void onClose(PlayerEntity player) {
		if (world != null && !removed && !player.isSpectator()) {
			viewerCount--;
			world.addSyncedBlockEvent(pos, getCachedState().getBlock(), VIEWER_COUNT_EVENT, viewerCount);
			world.updateNeighborsAlways(pos, getCachedState().getBlock());
		}
	}

	public void recountViewers() {
		if (world != null) {
			viewerCount = ChestBlockEntity.countViewers(world, this, pos.getX(), pos.getY(), pos.getZ());
			world.addSyncedBlockEvent(pos, getCachedState().getBlock(), VIEWER_COUNT_EVENT, viewerCount);
			world.updateNeighborsAlways(pos, getCachedState().getBlock());
		}
	} */
	//?}

	private void creak(World world, BlockPos pos, SoundEvent sound) {
		world.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, sound,
				SoundCategory.BLOCKS, 0.5F, world.random.nextFloat() * 0.1F + 0.9F);
	}

	@Override
	//? if >=26.1 {
	/* public int getContainerSize() { */
	//?} else {
	public int size() {
	//?}
		return INVENTORY_SIZE;
	}

	@Override
	protected Text getContainerName() {
		return Mc.translatable("container.keyboard_workstations.seed_box");
	}

	//? if >=1.21 {
	/* @Override
	protected DefaultedList<ItemStack> getHeldStacks() {
		return inventory;
	}

	@Override
	protected void setHeldStacks(DefaultedList<ItemStack> list) {
		inventory = list;
	}
	*/
	//?} else {
	@Override
	protected DefaultedList<ItemStack> getInvStackList() {
		return inventory;
	}

	@Override
	protected void setInvStackList(DefaultedList<ItemStack> list) {
		inventory = list;
	}
	//?}

	@Override
	protected ScreenHandler createScreenHandler(int syncId, PlayerInventory playerInventory) {
		return GenericContainerScreenHandler.createGeneric9x6(syncId, playerInventory, this);
	}

	//? if >=26.1 {
	/* @Override
	protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput output) {
		super.saveAdditional(output);

		if (!trySaveLootTable(output)) {
			Inventories.saveAllItems(output, inventory);
		}
	}

	@Override
	protected void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
		super.loadAdditional(input);
		inventory = DefaultedList.ofSize(INVENTORY_SIZE, ItemStack.EMPTY);

		if (!tryLoadLootTable(input)) {
			Inventories.loadAllItems(input, inventory);
		}
	}
	*/
	//?} else {
	@Override
	//? if >=1.20.5 {
	/* protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.writeNbt(nbt, registryLookup);

		if (!writeLootTable(nbt)) {
			Inventories.writeNbt(nbt, inventory, registryLookup);
		}
	}
	*/
	//?} elif >=1.17 {
	protected void writeNbt(NbtCompound nbt) {
		super.writeNbt(nbt);

		if (!serializeLootTable(nbt)) {
			Inventories.writeNbt(nbt, inventory);
		}
	}
	//?} else {
	/* public NbtCompound writeNbt(NbtCompound nbt) {
		super.writeNbt(nbt);

		if (!serializeLootTable(nbt)) {
			Inventories.writeNbt(nbt, inventory);
		}

		return nbt;
	} */
	//?}

	@Override
	//? if >=1.20.5 {
	/* protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.readNbt(nbt, registryLookup);
		inventory = DefaultedList.ofSize(INVENTORY_SIZE, ItemStack.EMPTY);

		if (!readLootTable(nbt)) {
			Inventories.readNbt(nbt, inventory, registryLookup);
		}
	}
	*/
	//?} elif >=1.17 {
	public void readNbt(NbtCompound nbt) {
		super.readNbt(nbt);
		inventory = DefaultedList.ofSize(INVENTORY_SIZE, ItemStack.EMPTY);

		if (!deserializeLootTable(nbt)) {
			Inventories.readNbt(nbt, inventory);
		}
	}
	//?} else {
	/* public void fromTag(BlockState state, NbtCompound nbt) {
		super.fromTag(state, nbt);
		inventory = DefaultedList.ofSize(INVENTORY_SIZE, ItemStack.EMPTY);

		if (!deserializeLootTable(nbt)) {
			Inventories.readNbt(nbt, inventory);
		}
	} */
	//?}
	//?}
}
