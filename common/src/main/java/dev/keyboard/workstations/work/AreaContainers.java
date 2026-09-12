package dev.keyboard.workstations.work;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The barrels and boxes a station works with, found anywhere inside its work area.
 *
 * <p>Reaching only what it touches would be simpler, but it makes the area a station is told to
 * look after mean two different things at once, and it puts the store in the one spot beside the
 * station that is also the natural place to stand. Anywhere in the area is the rule that needs no
 * explaining.
 *
 * <p>Which rules out looking at every block in the area: an area can be 129 blocks across and 65
 * tall, better than a million positions, and this is asked several times a tick. What is walked
 * instead is the block entity table each covered chunk already keeps, so the cost follows how many
 * containers are about rather than how large the area is. A wide area over empty fields costs
 * almost nothing.
 *
 * <p>Even that is not run on demand. Positions are kept for {@link #REFRESH_TICKS} and the block
 * entities looked up fresh from them every time, so a broken container is noticed at once while a
 * newly placed one takes up to a second to be taken on. Caching the block entities themselves
 * would mean writing milk into a barrel that had already been mined.
 */
public final class AreaContainers<T extends BlockEntity> {
	/** How long a scan's answer is reused for, in ticks. */
	private static final int REFRESH_TICKS = 20;

	private final Class<T> type;
	private final List<BlockPos> found = new ArrayList<>();
	private long nextScan;

	public AreaContainers(Class<T> type) {
		this.type = type;
	}

	/** Everything of this kind in the area, nearest the station first. */
	public List<T> in(@Nullable Level world, WorkArea area) {
		if (world == null) {
			return List.of();
		}

		if (world.getGameTime() >= nextScan) {
			rescan(world, area);
			nextScan = world.getGameTime() + REFRESH_TICKS;
		}

		List<T> containers = new ArrayList<>(found.size());

		for (BlockPos pos : found) {
			BlockEntity candidate = world.getBlockEntity(pos);

			if (type.isInstance(candidate) && !candidate.isRemoved()) {
				containers.add(type.cast(candidate));
			}
		}

		return containers;
	}

	/**
	 * Nearest first, and ties broken by position so the order never shuffles.
	 *
	 * <p>The order decides which barrel fills up and which box seed comes out of, so an answer that
	 * changed between two calls would have a station moving its stock about for no reason. Nearest
	 * first also means the container a player put right against the station is still the one used,
	 * which keeps the old habit working.
	 */
	private void rescan(Level world, WorkArea area) {
		found.clear();

		AABB box = area.getBox();
		BlockPos center = area.getCenter();
		int minChunkX = Mth.floor(box.minX) >> 4;
		int maxChunkX = Mth.floor(box.maxX) >> 4;
		int minChunkZ = Mth.floor(box.minZ) >> 4;
		int maxChunkZ = Mth.floor(box.maxZ) >> 4;

		for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
			for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
				// Asked for only once it is known to be there. Fetching a chunk that is not loaded
				// generates it, and a station is not a reason to build world.
				if (!world.hasChunk(chunkX, chunkZ)) {
					continue;
				}

				for (Map.Entry<BlockPos, BlockEntity> entry
						: world.getChunk(chunkX, chunkZ).getBlockEntities().entrySet()) {
					if (type.isInstance(entry.getValue())
							&& box.contains(Vec3.atCenterOf(entry.getKey()))) {
						found.add(entry.getKey().immutable());
					}
				}
			}
		}

		found.sort(Comparator.comparingDouble((BlockPos pos) -> pos.distSqr(center))
				.thenComparingLong(BlockPos::asLong));
	}
}
