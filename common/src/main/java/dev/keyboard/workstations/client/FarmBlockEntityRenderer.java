package dev.keyboard.workstations.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.keyboard.workstations.block.FarmBlockEntity;
import dev.keyboard.workstations.work.Crops;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Everything the farm station draws: the work area highlight, and a tile over every plot on its
 * register.
 */
public class FarmBlockEntityRenderer
		implements BlockEntityRenderer<FarmBlockEntity, FarmBlockEntityRenderer.State> {
	private static final float SURFACE_OFFSET = 0.05F;
	private static final float INSET = 0.06F;
	private static final float ALPHA = 0.35F;
	private static final float[] TILLABLE = {0.90F, 0.45F, 0.15F};
	private static final float[] BARE = {0.95F, 0.90F, 0.55F};
	private static final float[] GROWING = {0.30F, 0.80F, 0.35F};
	private static final float[] RIPE = {1.00F, 0.80F, 0.10F};

	public FarmBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	@Override
	public int getViewDistance() {
		return 192;
	}

	@Override
	public void extractRenderState(FarmBlockEntity station, State state, float tickDelta, Vec3 camera,
			ModelFeatureRenderer.CrumblingOverlay breakProgress) {
		BlockEntityRenderState.extractBase(station, state, breakProgress);
		WorkAreaHighlightRenderer.extract(station, state);
		state.tiles.clear();

		Level world = station.getLevel();

		if (world == null || !HighlightState.shouldRender()) {
			return;
		}

		BlockPos origin = station.getBlockPos();

		for (BlockPos plot : station.getPlots()) {
			BlockState plotState = world.getBlockState(plot);

			if (Crops.isPlot(plotState)) {
				float[] colour = colourFor(world, plot, plotState);
				state.tiles.add(new Tile(
						plot.getX() - origin.getX() + INSET,
						plot.getX() - origin.getX() + 1.0F - INSET,
						plot.getZ() - origin.getZ() + INSET,
						plot.getZ() - origin.getZ() + 1.0F - INSET,
						plot.getY() - origin.getY() + 1.0F + SURFACE_OFFSET,
						colour[0], colour[1], colour[2]));
			}
		}
	}

	@Override
	public void submit(State state, PoseStack matrices, SubmitNodeCollector collector, CameraRenderState camera) {
		WorkAreaHighlightRenderer.submit(state, matrices, collector);

		if (state.tiles.isEmpty()) {
			return;
		}

		collector.submitCustomGeometry(matrices, RenderTypes.debugQuads(), (pose, buffer) -> {
			Matrix4f matrix = pose.pose();

			for (Tile tile : state.tiles) {
				buffer.addVertex(matrix, tile.minX, tile.y, tile.minZ).setColor(tile.red, tile.green, tile.blue, ALPHA);
				buffer.addVertex(matrix, tile.minX, tile.y, tile.maxZ).setColor(tile.red, tile.green, tile.blue, ALPHA);
				buffer.addVertex(matrix, tile.maxX, tile.y, tile.maxZ).setColor(tile.red, tile.green, tile.blue, ALPHA);
				buffer.addVertex(matrix, tile.maxX, tile.y, tile.minZ).setColor(tile.red, tile.green, tile.blue, ALPHA);
			}
		});
	}

	private static float[] colourFor(Level world, BlockPos plot, BlockState state) {
		if (Crops.isTillable(state)) {
			return TILLABLE;
		}

		if (Crops.isRipe(world, plot)) {
			return RIPE;
		}

		return Crops.growingOn(world, plot) == null ? BARE : GROWING;
	}

	public static class State extends WorkAreaHighlightRenderer.HighlightRenderState {
		final List<Tile> tiles = new ArrayList<>();
	}

	record Tile(float minX, float maxX, float minZ, float maxZ, float y, float red, float green, float blue) {
	}
}
