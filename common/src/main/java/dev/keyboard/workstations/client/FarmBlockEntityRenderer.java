package dev.keyboard.workstations.client;

import dev.keyboard.workstations.block.FarmBlockEntity;
import dev.keyboard.workstations.work.Crops;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.joml.Matrix4f;

/**
 * Everything the farm station draws: the work area highlight, and a tile over every plot on its
 * register.
 *
 * <p>The area box on its own only says how far the farm reaches. Which ground inside it the farmer
 * actually took on is a separate question, and the usual reason a field appears to be ignored, so
 * the plots are drawn too. Their colour says what the farmer thinks each one needs, which turns
 * "why has it not planted that row" into something you can see from the gate.
 */
public class FarmBlockEntityRenderer implements BlockEntityRenderer<FarmBlockEntity> {
	/**
	 * How far the tiles float above the plot's top face.
	 *
	 * <p>Deliberately more than the area highlight's own floor slab uses. The usual way to build a
	 * farm is to stand the station on the field, which puts the farmland one block down and its top
	 * face at exactly the height that slab is drawn at. Matching offsets would leave the two
	 * translucent quads fighting over the same depth, which flickers as you walk.
	 */
	private static final float SURFACE_OFFSET = 0.05F;
	/** Shrinks each tile so neighbouring plots read as separate squares rather than one sheet. */
	private static final float INSET = 0.06F;
	private static final float ALPHA = 0.35F;

	/** Needs hoeing: registered, but trampled back to bare ground. */
	private static final float[] TILLABLE = {0.90F, 0.45F, 0.15F};
	/** Farmland standing empty, waiting for a seed. */
	private static final float[] BARE = {0.95F, 0.90F, 0.55F};
	/** Sown and still growing, which is the state the farmer has nothing to do about. */
	private static final float[] GROWING = {0.30F, 0.80F, 0.35F};
	/** Ripe and waiting to be harvested. */
	private static final float[] RIPE = {1.00F, 0.80F, 0.10F};

	private final WorkAreaHighlightRenderer<FarmBlockEntity> highlight;

	public FarmBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
		this.highlight = new WorkAreaHighlightRenderer<>(context);
	}

	@Override
	public boolean rendersOutsideBoundingBox(FarmBlockEntity station) {
		return true;
	}

	@Override
	public int getRenderDistance() {
		return 192;
	}

	@Override
	public void render(FarmBlockEntity station, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, int overlay) {
		highlight.render(station, tickDelta, matrices, vertexConsumers, light, overlay);

		World world = station.getWorld();

		// Same gesture as the area box: holding either station block, or the toggle key.
		if (world == null || !HighlightState.shouldRender()) {
			return;
		}

		VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getDebugQuads());
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		BlockPos origin = station.getPos();

		for (BlockPos plot : station.getPlots()) {
			// The register is only as fresh as the last packet, so a plot dug up a moment ago may
			// still be listed. Drawing a tile on thin air would be worse than drawing nothing.
			BlockState state = world.getBlockState(plot);

			if (Crops.isPlot(state)) {
				tile(buffer, matrix, origin, plot, colourFor(world, plot, state));
			}
		}
	}

	/** What the farmer would do with this plot next, as a colour. */
	private static float[] colourFor(World world, BlockPos plot, BlockState state) {
		if (Crops.isTillable(state)) {
			return TILLABLE;
		}

		if (Crops.isRipe(world, plot)) {
			return RIPE;
		}

		return Crops.growingOn(world, plot) == null ? BARE : GROWING;
	}

	/** One flat square laid on the plot's top face, in the station block's own coordinates. */
	private static void tile(VertexConsumer buffer, Matrix4f matrix, BlockPos origin, BlockPos plot,
			float[] colour) {
		float minX = plot.getX() - origin.getX() + INSET;
		float maxX = plot.getX() - origin.getX() + 1.0F - INSET;
		float minZ = plot.getZ() - origin.getZ() + INSET;
		float maxZ = plot.getZ() - origin.getZ() + 1.0F - INSET;
		// The top of the plot, which is one block above the plot's own coordinate.
		float y = plot.getY() - origin.getY() + 1.0F + SURFACE_OFFSET;

		buffer.vertex(matrix, minX, y, minZ).color(colour[0], colour[1], colour[2], ALPHA).next();
		buffer.vertex(matrix, minX, y, maxZ).color(colour[0], colour[1], colour[2], ALPHA).next();
		buffer.vertex(matrix, maxX, y, maxZ).color(colour[0], colour[1], colour[2], ALPHA).next();
		buffer.vertex(matrix, maxX, y, minZ).color(colour[0], colour[1], colour[2], ALPHA).next();
	}
}
