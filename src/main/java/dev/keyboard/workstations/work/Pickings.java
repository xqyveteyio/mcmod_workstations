package dev.keyboard.workstations.work;

import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * One look around the work area for the produce that never went through a plot: melons and
 * pumpkins grown off a stem, and mushrooms.
 *
 * <p>There is no register behind these the way there is behind plots. A melon appears on whichever
 * side of its stem happened to have room and a mushroom spreads wherever it likes, so the only way
 * to know where they are is to go and look. That is awkward, because the work area can be set 129
 * blocks across and 65 tall, and reading a million blocks every time the farmer wants a job would
 * cost more than everything else the station does put together.
 *
 * <p>So the sweep asks chunk sections rather than blocks. A section keeps a palette of the states
 * it actually holds, and asking that palette whether there is a mushroom anywhere in it is a
 * handful of comparisons instead of four thousand block reads. Almost every section over a farm
 * answers no and is skipped whole, which is what makes looking every round affordable.
 */
public final class Pickings {
	private Pickings() {
	}

	/**
	 * Everything in the area worth breaking, given what the station has been set to take.
	 *
	 * @return positions of the blocks themselves, not of the ground underneath them
	 */
	public static List<BlockPos> find(ServerWorld world, WorkArea area, boolean gourds, boolean mushrooms) {
		List<BlockPos> found = new ArrayList<>();

		if (!gourds && !mushrooms) {
			return found;
		}

		BlockPos center = area.getCenter();
		int radius = area.getRadius();
		int minX = center.getX() - radius;
		int maxX = center.getX() + radius;
		int minZ = center.getZ() - radius;
		int maxZ = center.getZ() + radius;
		int minY = Math.max(world.getBottomY(), center.getY() - area.getHeight());
		int maxY = Math.min(world.getTopY() - 1, center.getY() + area.getHeight());

		// Deliberately looser than what actually gets picked, because a palette knows which blocks
		// a section holds and nothing about where they are. A pumpkin somebody built a wall out of
		// passes here and is weeded out below, once there is a position to look around.
		Predicate<BlockState> candidate = state ->
				(mushrooms && Crops.isMushroom(state)) || (gourds && Crops.isGourd(state));

		for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
			for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
				// Reading an unloaded chunk would load it, and a farm station is no reason to hold
				// the far side of its own area in memory.
				if (!world.isChunkLoaded(chunkX, chunkZ)) {
					continue;
				}

				BlockBox slice = new BlockBox(
						Math.max(minX, chunkX << 4), minY, Math.max(minZ, chunkZ << 4),
						Math.min(maxX, (chunkX << 4) + 15), maxY, Math.min(maxZ, (chunkZ << 4) + 15));
				sweep(world, world.getChunk(chunkX, chunkZ), candidate, slice, gourds, mushrooms, found);
			}
		}

		return found;
	}

	/** The part of one chunk that lies inside the area, taken a section at a time. */
	private static void sweep(ServerWorld world, Chunk chunk, Predicate<BlockState> candidate,
			BlockBox slice, boolean gourds, boolean mushrooms, List<BlockPos> found) {
		BlockPos.Mutable cursor = new BlockPos.Mutable();
		int topSection = ChunkSectionPos.getSectionCoord(slice.getMaxY());

		for (int sectionY = ChunkSectionPos.getSectionCoord(slice.getMinY()); sectionY <= topSection; sectionY++) {
			int index = chunk.sectionCoordToIndex(sectionY);

			if (index < 0 || index >= chunk.getSectionArray().length) {
				continue;
			}

			ChunkSection section = chunk.getSection(index);

			// The whole point of sweeping this way: one palette lookup rules out four thousand
			// blocks, and over a farm nearly every section is ruled out.
			if (section.isEmpty() || !section.hasAny(candidate)) {
				continue;
			}

			int fromY = Math.max(slice.getMinY(), ChunkSectionPos.getBlockCoord(sectionY));
			int toY = Math.min(slice.getMaxY(), ChunkSectionPos.getBlockCoord(sectionY) + 15);

			for (int y = fromY; y <= toY; y++) {
				for (int x = slice.getMinX(); x <= slice.getMaxX(); x++) {
					for (int z = slice.getMinZ(); z <= slice.getMaxZ(); z++) {
						cursor.set(x, y, z);

						if (Crops.isPickable(world, cursor, gourds, mushrooms)) {
							found.add(cursor.toImmutable());
						}
					}
				}
			}
		}
	}
}
