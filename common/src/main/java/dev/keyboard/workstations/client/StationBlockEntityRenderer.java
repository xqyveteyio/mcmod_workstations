package dev.keyboard.workstations.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.keyboard.workstations.block.RanchBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;

/**
 * Everything the station draws: the work area highlight, and the miniature pen on the tabletop. The
 * two are one renderer because a block entity type may only have one.
 */
public class StationBlockEntityRenderer
		implements BlockEntityRenderer<RanchBlockEntity, StationBlockEntityRenderer.State> {
	public StationBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
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
	public void extractRenderState(RanchBlockEntity station, State state, float tickDelta, Vec3 camera,
			ModelFeatureRenderer.CrumblingOverlay breakProgress) {
		BlockEntityRenderState.extractBase(station, state, breakProgress);
		WorkAreaHighlightRenderer.extract(station, state);

		state.animals.clear();

		if (station.getLevel() != null) {
			TabletopDisplay.extract(station.getLevel(), tickDelta, state.animals);
		}
	}

	@Override
	public void submit(State state, PoseStack matrices, SubmitNodeCollector collector, CameraRenderState camera) {
		WorkAreaHighlightRenderer.submit(state, matrices, collector);
		TabletopDisplay.submit(state.animals, matrices, collector, camera, state.lightCoords);
	}

	public static class State extends WorkAreaHighlightRenderer.HighlightRenderState {
		final List<TabletopDisplay.Drawn> animals = new ArrayList<>();
	}
}
