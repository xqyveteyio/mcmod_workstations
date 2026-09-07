package dev.keyboard.breederscarecrow.client;

import dev.keyboard.breederscarecrow.block.ScarecrowBlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Everything the station draws: the work area highlight, and the miniature pen on the tabletop. The
 * two are one renderer because a block entity type may only have one.
 *
 * <p>The pen is a readout of the herd. A kind of livestock appears in its corner once there is one
 * of it in the work area, so a glance at the table says what the ranch is keeping.
 */
public class StationBlockEntityRenderer implements BlockEntityRenderer<ScarecrowBlockEntity> {
	/** Ticks between herd scans. Generous, because this only feeds an ornament. */
	private static final int SCAN_INTERVAL = 60;

	private final WorkAreaHighlightRenderer highlight;
	/** Weakly keyed, so a station that unloads takes its survey with it. */
	private final Map<ScarecrowBlockEntity, Survey> surveys = new WeakHashMap<>();

	public StationBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
		this.highlight = new WorkAreaHighlightRenderer(context);
	}

	@Override
	public boolean rendersOutsideBoundingBox(ScarecrowBlockEntity station) {
		return true;
	}

	@Override
	public int getRenderDistance() {
		return 192;
	}

	@Override
	public void render(ScarecrowBlockEntity station, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, int overlay) {
		highlight.render(station, tickDelta, matrices, vertexConsumers, light, overlay);

		World world = station.getWorld();

		if (world != null) {
			Set<EntityType<?>> present = surveys.computeIfAbsent(station, key -> new Survey()).present(station, world);
			TabletopDisplay.render(world, present, tickDelta, matrices, vertexConsumers, light);
		}
	}

	/** One station's last look at its herd, kept so the pen is not rebuilt every frame. */
	private static final class Survey {
		private long takenAt = Long.MIN_VALUE;
		private Set<EntityType<?>> present = Set.of();

		Set<EntityType<?>> present(ScarecrowBlockEntity station, World world) {
			long time = world.getTime();

			if (time - takenAt >= SCAN_INTERVAL) {
				takenAt = time;
				present = scan(station, world);
			}

			return present;
		}

		private static Set<EntityType<?>> scan(ScarecrowBlockEntity station, World world) {
			Set<EntityType<?>> found = new HashSet<>();

			for (AnimalEntity animal : world.getEntitiesByClass(AnimalEntity.class,
					station.getWorkArea().getBox(), AnimalEntity::isAlive)) {
				if (TabletopDisplay.EVERY_KIND.contains(animal.getType())) {
					found.add(animal.getType());

					// Nothing left to learn once every corner of the pen is spoken for.
					if (found.size() == TabletopDisplay.EVERY_KIND.size()) {
						break;
					}
				}
			}

			return found;
		}
	}
}
