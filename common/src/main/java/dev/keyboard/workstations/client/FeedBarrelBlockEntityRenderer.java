package dev.keyboard.workstations.client;

import dev.keyboard.workstations.WorkstationsMod;
import dev.keyboard.workstations.block.FeedBarrelBlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.util.math.MatrixStack;

/**
 * Draws the placed feed box, lid and all.
 *
 * <p>The block itself renders as nothing, so this draws its whole body. That is what earns the box
 * a block entity renderer at all: a lid that swings needs redrawing every frame, which a block
 * model baked once into the chunk cannot do.
 */
public class FeedBarrelBlockEntityRenderer implements BlockEntityRenderer<FeedBarrelBlockEntity> {
	private final SeedBoxModel model;

	public FeedBarrelBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
		model = new SeedBoxModel(context.getLayerModelPart(EntityModelLayers.CHEST),
				WorkstationsMod.id("textures/entity/feed_barrel.png"));
	}

	@Override
	public void render(FeedBarrelBlockEntity box, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, int overlay) {
		model.render(box.getCachedState(), box.getAnimationProgress(tickDelta),
				matrices, vertexConsumers, light, overlay);
	}
}
