package dev.keyboard.workstations.client;

import dev.keyboard.workstations.block.ScarecrowBlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.world.World;

/**
 * Everything the station draws: the work area highlight, and the miniature pen on the tabletop. The
 * two are one renderer because a block entity type may only have one.
 */
public class StationBlockEntityRenderer implements BlockEntityRenderer<ScarecrowBlockEntity> {
	private final WorkAreaHighlightRenderer<ScarecrowBlockEntity> highlight;

	public StationBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
		this.highlight = new WorkAreaHighlightRenderer<>(context);
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
			TabletopDisplay.render(world, tickDelta, matrices, vertexConsumers, light);
		}
	}
}
