package dev.keyboard.workstations.block;

import dev.keyboard.workstations.WorkstationsMod;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

/**
 * The milk a barrel is holding, in buckets.
 *
 * <p>Nothing here is sent to the client. What a barrel looks like is carried by the block state,
 * which the game keeps in step on its own, and the only place the exact count is read is on the
 * server: the line shown over a player's hotbar is written there and sent as text.
 */
public class MilkBarrelBlockEntity extends BlockEntity {
	/**
	 * Buckets a barrel holds. Sized so that a ranch left alone for a long stretch still has
	 * somewhere to put what it milks, rather than idling because nobody came to empty it.
	 */
	public static final int CAPACITY = 64;

	private static final String MILK_KEY = "Milk";

	private int stored;

	public MilkBarrelBlockEntity(BlockPos pos, BlockState state) {
		super(WorkstationsMod.MILK_BARREL_BLOCK_ENTITY.get(), pos, state);
	}

	public int getStored() {
		return stored;
	}

	public boolean isFull() {
		return stored >= CAPACITY;
	}

	public boolean isEmpty() {
		return stored <= 0;
	}

	/** Pours one bucket in, answering whether there was room for it. */
	public boolean fill() {
		if (isFull()) {
			return false;
		}

		set(stored + 1);
		return true;
	}

	/** Draws one bucket out, answering whether there was any to draw. */
	public boolean drain() {
		if (isEmpty()) {
			return false;
		}

		set(stored - 1);
		return true;
	}

	private void set(int amount) {
		stored = MathHelper.clamp(amount, 0, CAPACITY);
		markDirty();

		if (world != null) {
			// Both of these read the new amount, so neither can be folded into markDirty.
			MilkBarrelBlock.showLevel(world, pos, getCachedState(), stored);
			world.updateComparators(pos, getCachedState().getBlock());
		}
	}

	@Override
	public void readNbt(NbtCompound nbt) {
		super.readNbt(nbt);
		stored = MathHelper.clamp(nbt.getInt(MILK_KEY), 0, CAPACITY);
	}

	@Override
	protected void writeNbt(NbtCompound nbt) {
		super.writeNbt(nbt);
		nbt.putInt(MILK_KEY, stored);
	}
}
