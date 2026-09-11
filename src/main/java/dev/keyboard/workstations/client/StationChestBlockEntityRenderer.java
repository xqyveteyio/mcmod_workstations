package dev.keyboard.workstations.client;

import net.minecraft.block.entity.BlockEntity;
import dev.keyboard.workstations.block.AnimatedLid;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
//? if >=1.17 {
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
//?} else {
/* import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher; */
//?}
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * Draws a placed box that looks like a chest, lid and all.
 *
 * <p>The block itself renders as nothing, so this draws its whole body. That is what earns the box
 * a block entity renderer at all: a lid that swings needs redrawing every frame, which a block
 * model baked once into the chunk cannot do.
 */
public class StationChestBlockEntityRenderer<T extends BlockEntity & AnimatedLid>
		//? if >=1.17 {
		implements BlockEntityRenderer<T> {
		//?} else {
		/* extends BlockEntityRenderer<T> { */
		//?}
	private final StationChestModel model;

	//? if >=1.17 {
	public StationChestBlockEntityRenderer(BlockEntityRendererFactory.Context context, Identifier texture) {
		model = new StationChestModel(context.getLayerModelPart(EntityModelLayers.CHEST), texture);
	}
	//?} else {
	/* public StationChestBlockEntityRenderer(BlockEntityRenderDispatcher dispatcher, Identifier texture) {
		super(dispatcher);
		model = new StationChestModel(texture);
	} */
	//?}

	@Override
	public void render(T box, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, int overlay) {
		model.render(box.getCachedState(), box.getAnimationProgress(tickDelta),
				matrices, vertexConsumers, light, overlay);
	}
}
