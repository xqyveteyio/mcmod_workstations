package dev.keyboard.breederscarecrow.pen;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;

/**
 * A flat set of floor tiles that animals can walk between without leaving the pen.
 * Every tile shares the same Y level, which is the level the scarecrow itself stands on.
 */
public final class PenRegion {
	private static final String FLOOR_Y_KEY = "FloorY";
	private static final String ENCLOSED_KEY = "Enclosed";
	private static final String CELLS_KEY = "Cells";

	private final int floorY;
	private final boolean enclosed;
	private final LongSet cells;
	private final int minX;
	private final int minZ;
	private final int maxX;
	private final int maxZ;

	public PenRegion(int floorY, boolean enclosed, LongSet cells) {
		this.floorY = floorY;
		this.enclosed = enclosed;
		this.cells = cells;

		int minX = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;

		for (LongIterator iterator = cells.iterator(); iterator.hasNext(); ) {
			long packed = iterator.nextLong();
			int x = BlockPos.unpackLongX(packed);
			int z = BlockPos.unpackLongZ(packed);
			minX = Math.min(minX, x);
			minZ = Math.min(minZ, z);
			maxX = Math.max(maxX, x);
			maxZ = Math.max(maxZ, z);
		}

		this.minX = minX;
		this.minZ = minZ;
		this.maxX = maxX;
		this.maxZ = maxZ;
	}

	public static PenRegion empty(int floorY) {
		return new PenRegion(floorY, false, LongSets.EMPTY_SET);
	}

	public int getFloorY() {
		return floorY;
	}

	/** {@code false} when the pen leaks, is larger than the configured limit, or was never scanned. */
	public boolean isEnclosed() {
		return enclosed;
	}

	public boolean isEmpty() {
		return cells.isEmpty();
	}

	public int size() {
		return cells.size();
	}

	public LongSet getCells() {
		return cells;
	}

	public boolean containsColumn(int x, int z) {
		return cells.contains(BlockPos.asLong(x, floorY, z));
	}

	public boolean containsEntity(Entity entity) {
		if (entity.getY() < floorY - 1.0 || entity.getY() > floorY + 3.0) {
			return false;
		}

		return containsColumn(MathHelper.floor(entity.getX()), MathHelper.floor(entity.getZ()));
	}

	/** Bounding box used to collect entities; slightly taller than the floor so jumping animals still count. */
	public Box getSearchBox() {
		if (isEmpty()) {
			return null;
		}

		return new Box(minX, floorY - 1, minZ, maxX + 1.0, floorY + 3.0, maxZ + 1.0);
	}

	public NbtCompound writeNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.putInt(FLOOR_Y_KEY, floorY);
		nbt.putBoolean(ENCLOSED_KEY, enclosed);
		nbt.putLongArray(CELLS_KEY, cells.toLongArray());
		return nbt;
	}

	public static PenRegion fromNbt(NbtCompound nbt) {
		return new PenRegion(nbt.getInt(FLOOR_Y_KEY), nbt.getBoolean(ENCLOSED_KEY),
				new LongOpenHashSet(nbt.getLongArray(CELLS_KEY)));
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}

		if (!(other instanceof PenRegion region)) {
			return false;
		}

		return floorY == region.floorY && enclosed == region.enclosed && cells.equals(region.cells);
	}

	@Override
	public int hashCode() {
		return 31 * (31 * floorY + Boolean.hashCode(enclosed)) + cells.hashCode();
	}
}
