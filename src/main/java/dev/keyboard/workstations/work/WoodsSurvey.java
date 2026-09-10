package dev.keyboard.workstations.work;

import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One look at the wood a lumber station is working: the trees still standing, the saplings
 * already in the ground, and the spots that could take another one.
 *
 * <p>Trees are found the way {@link Pickings} finds a melon: a section palette says whether a
 * chunk even holds a log, and only then are the blocks read. Each log starts a flood fill, and
 * logs already claimed by an earlier fill are skipped, so a wood of twenty trees costs twenty
 * walks rather than one per log.
 *
 * <p>Auto-planting does not walk every column. It steps by {@link Woods#PLANT_SPACING}, which is
 * also the gap the plantings themselves keep, so the survey and the rule agree and a 129-across
 * wood is a few hundred columns rather than a million blocks.
 */
public final class WoodsSurvey {
	/**
	 * Most trees one look will take on. Nearest the station first, so a forest larger than the
	 * cap keeps the part around the block rather than an arbitrary corner of itself.
	 */
	public static final int MAX_TREES = 128;
	/** Same idea for auto-plant spots: a bound, not a target. */
	public static final int MAX_PLANTABLE = 256;

	private final List<Woods.Tree> trees = new ArrayList<>();
	private final List<BlockPos> saplings = new ArrayList<>();
	private final List<BlockPos> plantable = new ArrayList<>();

	private WoodsSurvey() {
	}

	public static WoodsSurvey of(ServerWorld world, WorkArea area, Collection<BlockPos> stumps,
			boolean autoPlant) {
		WoodsSurvey survey = new WoodsSurvey();
		Set<BlockPos> claimed = new HashSet<>();

		List<BlockPos> logs = Pickings.find(world, area, Woods::isLog);
		logs.sort(Comparator.comparingDouble(log -> log.getSquaredDistance(area.getCenter())));

		for (BlockPos log : logs) {
			if (survey.trees.size() >= MAX_TREES || claimed.contains(log)) {
				continue;
			}

			Woods.Tree tree = Woods.gather(world, log);
			claimed.addAll(tree.logs());
			survey.trees.add(tree);
		}

		survey.saplings.addAll(Pickings.find(world, area, Woods::isSapling));

		Set<BlockPos> occupied = new HashSet<>();

		for (Woods.Tree tree : survey.trees) {
			occupied.add(tree.stump());
		}

		for (BlockPos sapling : survey.saplings) {
			occupied.add(sapling.down());
		}

		for (BlockPos stump : stumps) {
			BlockPos soil = soilOf(world, stump);

			if (soil != null && survey.acceptPlanting(soil, occupied, false)) {
				survey.plantable.add(soil);
				occupied.add(soil);
			}
		}

		if (autoPlant) {
			survey.scanOpenGround(world, area, occupied);
		}

		return survey;
	}

	/**
	 * Soil a recorded stump still wants a sapling on, or null when the spot has been built over,
	 * already planted, or grown back into a log.
	 *
	 * <p>The stump itself may still be the log position from before the tree came down, or the
	 * soil under it if the log is gone. Both are tried so a stump written either way still
	 * replants.
	 */
	@Nullable
	private static BlockPos soilOf(ServerWorld world, BlockPos stump) {
		if (!world.isChunkLoaded(stump.getX() >> 4, stump.getZ() >> 4)) {
			return null;
		}

		BlockState at = world.getBlockState(stump);

		if (Woods.isLog(at) || Woods.isSapling(at)) {
			return null;
		}

		if (Woods.canPlantAt(world, stump)) {
			return stump;
		}

		return Woods.canPlantAt(world, stump.down()) ? stump.down() : null;
	}

	private void scanOpenGround(ServerWorld world, WorkArea area, Set<BlockPos> occupied) {
		BlockPos center = area.getCenter();
		int radius = area.getRadius();
		int height = area.getHeight();
		int step = Woods.PLANT_SPACING;
		BlockPos.Mutable cursor = new BlockPos.Mutable();

		for (int dx = -radius; dx <= radius && plantable.size() < MAX_PLANTABLE; dx += step) {
			for (int dz = -radius; dz <= radius && plantable.size() < MAX_PLANTABLE; dz += step) {
				int x = center.getX() + dx;
				int z = center.getZ() + dz;

				if (!world.isChunkLoaded(x >> 4, z >> 4)) {
					continue;
				}

				for (int dy = height; dy >= -height; dy--) {
					cursor.set(x, center.getY() + dy, z);

					if (!Woods.canPlantAt(world, cursor)) {
						continue;
					}

					BlockPos soil = cursor.toImmutable();

					if (acceptPlanting(soil, occupied, true) && Woods.hasRoomToGrow(world, soil.up())) {
						plantable.add(soil);
						occupied.add(soil);
						break;
					}
				}
			}
		}
	}

	private boolean acceptPlanting(BlockPos soil, Set<BlockPos> occupied, boolean spaced) {
		if (plantable.size() >= MAX_PLANTABLE) {
			return false;
		}

		if (!spaced) {
			return !occupied.contains(soil);
		}

		for (BlockPos other : occupied) {
			if (Woods.tooClose(soil, other)) {
				return false;
			}
		}

		for (BlockPos other : plantable) {
			if (Woods.tooClose(soil, other)) {
				return false;
			}
		}

		return true;
	}

	public List<Woods.Tree> trees() {
		return trees;
	}

	/** Saplings already in the ground, which is what bone meal is aimed at. */
	public List<BlockPos> saplings() {
		return saplings;
	}

	/**
	 * Soil that should take a sapling this round: recorded stumps first, then the open ground
	 * auto-planting added, each already checked for room and spacing.
	 */
	public List<BlockPos> plantable() {
		return plantable;
	}
}
