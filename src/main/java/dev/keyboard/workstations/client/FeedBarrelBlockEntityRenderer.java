package dev.keyboard.workstations.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.keyboard.workstations.WorkstationsMod;
import dev.keyboard.workstations.block.FeedBarrelBlockEntity;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the placed feed box, lid and all.
 */
public class FeedBarrelBlockEntityRenderer
		implements BlockEntityRenderer<FeedBarrelBlockEntity, FeedBarrelBlockEntityRenderer.State> {
	private final SeedBoxModel model;

	public FeedBarrelBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
		model = new SeedBoxModel(context.bakeLayer(ModelLayers.CHEST),
				WorkstationsMod.id("textures/entity/feed_barrel.png"));
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(FeedBarrelBlockEntity box, State state, float tickDelta, Vec3 camera,
			ModelFeatureRenderer.CrumblingOverlay breakProgress) {
		BlockEntityRenderState.extractBase(box, state, breakProgress);
		state.blockState = box.getBlockState();
		state.openness = box.getOpenNess(tickDelta);
		state.breakProgress = breakProgress;
	}

	@Override
	public void submit(State state, PoseStack matrices, SubmitNodeCollector collector, CameraRenderState camera) {
		model.submit(state.blockState, state.openness, matrices, collector, state.lightCoords,
				net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, state.breakProgress);
	}

	public static class State extends BlockEntityRenderState {
		BlockState blockState;
		float openness;
	}
}
