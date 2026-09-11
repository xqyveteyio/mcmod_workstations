package dev.keyboard.workstations.work;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * One look around the work area for blocks that never went through a register: melons and
 * pumpkins grown off a stem, mushrooms, and the logs and saplings a lumber station works.
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
	public static List<BlockPos> find(ServerLevel world, WorkArea area, boolean gourds, boolean mushrooms) {
		if (!gourds && !mushrooms) {
			return new ArrayList<>();
		}

		// Deliberately looser than what actually gets picked, because a palette knows which blocks
		// a section holds and nothing about where they are. A pumpkin somebody built a wall out of
		// passes here and is weeded out below, once there is a position to look around.
		return find(world, area,
				state -> (mushrooms && Crops.isMushroom(state)) || (gourds && Crops.isGourd(state)),
				pos -> Crops.isPickable(world, pos, gourds, mushrooms));
	}

	/**
	 * Every block in the area whose state matches {@code candidate}.
	 *
	 * <p>The same section-palette walk the farm uses for melons, reused for logs and saplings: a
	 * wood is the same kind of sparse problem, and writing a second sweep would only have to
	 * rediscover why asking a million blocks is unaffordable.
	 */
	public static List<BlockPos> find(ServerLevel world, WorkArea area, Predicate<BlockState> candidate) {
		return find(world, area, candidate, pos -> candidate.test(world.getBlockState(pos)));
	}

	/**
	 * Every block in the area the palette flags and {@code accept} still wants, once there is a
	 * position to look at.
	 */
	public static List<BlockPos> find(ServerLevel world, WorkArea area, Predicate<BlockState> candidate,
			Predicate<BlockPos> accept) {
		List<BlockPos> found = new ArrayList<>();
		BlockPos center = area.getCenter();
		int minX = center.getX() - area.getXRadius();
		int maxX = center.getX() + area.getXRadius();
		int minZ = center.getZ() - area.getZRadius();
		int maxZ = center.getZ() + area.getZRadius();
		int minY = Math.max(world.getMinY(), center.getY() - area.getBelow());
		int maxY = Math.min(world.getMaxY(), center.getY() + area.getAbove());

		for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
			for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
				// Reading an unloaded chunk would load it, and a station is no reason to hold
				// the far side of its own area in memory.
				if (!world.hasChunk(chunkX, chunkZ)) {
					continue;
				}

				BoundingBox slice = new BoundingBox(
						Math.max(minX, chunkX << 4), minY, Math.max(minZ, chunkZ << 4),
						Math.min(maxX, (chunkX << 4) + 15), maxY, Math.min(maxZ, (chunkZ << 4) + 15));
				sweep(world, world.getChunk(chunkX, chunkZ), candidate, accept, slice, found);
			}
		}

		return found;
	}

	/** The part of one chunk that lies inside the area, taken a section at a time. */
	private static void sweep(ServerLevel world, ChunkAccess chunk, Predicate<BlockState> candidate,
			Predicate<BlockPos> accept, BoundingBox slice, List<BlockPos> found) {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int topSection = SectionPos.blockToSectionCoord(slice.maxY());

		for (int sectionY = SectionPos.blockToSectionCoord(slice.minY()); sectionY <= topSection; sectionY++) {
			int index = chunk.getSectionIndexFromSectionY(sectionY);

			if (index < 0 || index >= chunk.getSections().length) {
				continue;
			}

			LevelChunkSection section = chunk.getSection(index);

			// The whole point of sweeping this way: one palette lookup rules out four thousand
			// blocks, and over a farm nearly every section is ruled out.
			if (section.hasOnlyAir() || !section.maybeHas(candidate)) {
				continue;
			}

			int fromY = Math.max(slice.minY(), SectionPos.sectionToBlockCoord(sectionY));
			int toY = Math.min(slice.maxY(), SectionPos.sectionToBlockCoord(sectionY) + 15);

			for (int y = fromY; y <= toY; y++) {
				for (int x = slice.minX(); x <= slice.maxX(); x++) {
					for (int z = slice.minZ(); z <= slice.maxZ(); z++) {
						cursor.set(x, y, z);

						if (accept.test(cursor)) {
							found.add(cursor.immutable());
						}
					}
				}
			}
		}
	}
}
