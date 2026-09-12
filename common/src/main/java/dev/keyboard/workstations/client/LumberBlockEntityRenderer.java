package dev.keyboard.workstations.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.keyboard.workstations.block.LumberBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Everything the lumber station draws: the axe turning over the bench, the work area highlight,
 * and a tile over every stump it has on its books.
 */
public class LumberBlockEntityRenderer
		implements BlockEntityRenderer<LumberBlockEntity, LumberBlockEntityRenderer.State> {
	private static final float SURFACE_OFFSET = 0.05F;
	private static final float INSET = 0.06F;
	private static final float ALPHA = 0.35F;
	private static final float[] STUMP = {0.72F, 0.52F, 0.28F};

	public LumberBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
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
	public void extractRenderState(LumberBlockEntity station, State state, float tickDelta, Vec3 camera,
			ModelFeatureRenderer.CrumblingOverlay breakProgress) {
		BlockEntityRenderState.extractBase(station, state, breakProgress);
		WorkAreaHighlightRenderer.extract(station, state);
		AxeDisplay.extract(station.getLevel(), tickDelta, state);
		state.tiles.clear();

		if (station.getLevel() == null || !HighlightState.shouldRender()) {
			return;
		}

		BlockPos origin = station.getBlockPos();

		for (BlockPos stump : station.getStumps()) {
			state.tiles.add(new Tile(
					stump.getX() - origin.getX() + INSET,
					stump.getX() - origin.getX() + 1.0F - INSET,
					stump.getZ() - origin.getZ() + INSET,
					stump.getZ() - origin.getZ() + 1.0F - INSET,
					stump.getY() - origin.getY() + SURFACE_OFFSET));
		}
	}

	@Override
	public void submit(State state, PoseStack matrices, SubmitNodeCollector collector, CameraRenderState camera) {
		WorkAreaHighlightRenderer.submit(state, matrices, collector);
		AxeDisplay.submit(state, matrices, collector, state.lightCoords);

		if (state.tiles.isEmpty()) {
			return;
		}

		collector.submitCustomGeometry(matrices, RenderTypes.debugQuads(), (pose, buffer) -> {
			Matrix4f matrix = pose.pose();

			for (Tile tile : state.tiles) {
				buffer.addVertex(matrix, tile.minX, tile.y, tile.minZ).setColor(STUMP[0], STUMP[1], STUMP[2], ALPHA);
				buffer.addVertex(matrix, tile.minX, tile.y, tile.maxZ).setColor(STUMP[0], STUMP[1], STUMP[2], ALPHA);
				buffer.addVertex(matrix, tile.maxX, tile.y, tile.maxZ).setColor(STUMP[0], STUMP[1], STUMP[2], ALPHA);
				buffer.addVertex(matrix, tile.maxX, tile.y, tile.minZ).setColor(STUMP[0], STUMP[1], STUMP[2], ALPHA);
			}
		});
	}

	public static class State extends WorkAreaHighlightRenderer.HighlightRenderState {
		float axeTime;
		final ItemStackRenderState axe = new ItemStackRenderState();
		final List<Tile> tiles = new ArrayList<>();
	}

	record Tile(float minX, float maxX, float minZ, float maxZ, float y) {
	}
}
