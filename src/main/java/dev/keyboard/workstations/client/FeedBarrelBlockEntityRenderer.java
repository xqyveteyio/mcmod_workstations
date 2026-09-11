package dev.keyboard.workstations.client;

import dev.keyboard.workstations.WorkstationsMod;
import dev.keyboard.workstations.block.FeedBarrelBlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;

/** Draws a placed feed box as a chest, lid and all. */
public class FeedBarrelBlockEntityRenderer extends BlockEntityRenderer<FeedBarrelBlockEntity> {
	private final SeedBoxModel model = new SeedBoxModel(WorkstationsMod.id("textures/entity/feed_barrel.png"));

	public FeedBarrelBlockEntityRenderer(BlockEntityRenderDispatcher dispatcher) {
		super(dispatcher);
	}

	@Override
	public void render(FeedBarrelBlockEntity box, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, int overlay) {
		model.render(box.getCachedState(), box.getAnimationProgress(tickDelta),
				matrices, vertexConsumers, light, overlay);
	}
}
