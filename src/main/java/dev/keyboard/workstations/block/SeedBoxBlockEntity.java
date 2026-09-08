package dev.keyboard.workstations.block;

import dev.keyboard.workstations.WorkstationsMod;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
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
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Tickable;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class SeedBoxBlockEntity extends LootableContainerBlockEntity implements Tickable {
public static final int INVENTORY_SIZE = 54;

private static final int VIEWER_COUNT_EVENT = 1;

private DefaultedList<ItemStack> inventory = DefaultedList.ofSize(INVENTORY_SIZE, ItemStack.EMPTY);

protected float animationAngle;
protected float lastAnimationAngle;
protected int viewerCount;
private int ticksOpen;

public SeedBoxBlockEntity() {
super(WorkstationsMod.SEED_BOX_BLOCK_ENTITY);
}

public SeedBoxBlockEntity(BlockPos pos, BlockState state) {
super(WorkstationsMod.SEED_BOX_BLOCK_ENTITY);
}

@Override
public void tick() {
BlockPos pos = getPos();
ticksOpen++;
viewerCount = ChestBlockEntity.tickViewerCount(world, this, ticksOpen,
pos.getX(), pos.getY(), pos.getZ(), viewerCount);
lastAnimationAngle = animationAngle;

if (viewerCount > 0 && animationAngle == 0.0F) {
playSound(SoundEvents.BLOCK_CHEST_OPEN);
}

if (viewerCount > 0 || animationAngle > 0.0F) {
float angle = animationAngle;

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

if (angle < 0.5F && animationAngle >= 0.5F) {
playSound(SoundEvents.BLOCK_CHEST_CLOSE);
}
}
}

public float getAnimationProgress(float tickDelta) {
return MathHelper.lerp(tickDelta, lastAnimationAngle, animationAngle);
}

@Override
public boolean onSyncedBlockEvent(int type, int data) {
if (type == VIEWER_COUNT_EVENT) {
viewerCount = data;
return true;
}
return super.onSyncedBlockEvent(type, data);
}

@Override
public void onOpen(PlayerEntity player) {
if (world != null && !removed && !player.isSpectator()) {
if (viewerCount < 0) {
viewerCount = 0;
}
viewerCount++;
onInvOpenOrClose();
}
}

@Override
public void onClose(PlayerEntity player) {
if (world != null && !removed && !player.isSpectator()) {
viewerCount--;
onInvOpenOrClose();
}
}

protected void onInvOpenOrClose() {
if (world != null) {
world.addSyncedBlockEvent(pos, getCachedState().getBlock(), VIEWER_COUNT_EVENT, viewerCount);
world.updateNeighborsAlways(pos, getCachedState().getBlock());
}
}

public void recountViewers() {
if (world != null) {
viewerCount = ChestBlockEntity.countViewers(world, this, pos.getX(), pos.getY(), pos.getZ());
onInvOpenOrClose();
}
}

private void playSound(SoundEvent sound) {
if (world != null) {
world.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, sound,
SoundCategory.BLOCKS, 0.5F, world.random.nextFloat() * 0.1F + 0.9F);
}
}

@Override
public int size() {
return INVENTORY_SIZE;
}

@Override
protected Text getContainerName() {
return new TranslatableText("container.keyboard_workstations.seed_box");
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
public NbtCompound writeNbt(NbtCompound nbt) {
super.writeNbt(nbt);

if (!serializeLootTable(nbt)) {
Inventories.writeNbt(nbt, inventory);
}
return nbt;
}

@Override
public void fromTag(BlockState state, NbtCompound nbt) {
super.fromTag(state, nbt);
inventory = DefaultedList.ofSize(INVENTORY_SIZE, ItemStack.EMPTY);

if (!deserializeLootTable(nbt)) {
Inventories.readNbt(nbt, inventory);
}
}
}
