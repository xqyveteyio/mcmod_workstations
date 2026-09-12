package dev.keyboard.workstations.client;

import dev.keyboard.workstations.block.SeedBoxBlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;

public class SeedBoxBlockEntityRenderer extends BlockEntityRenderer<SeedBoxBlockEntity> {
private final SeedBoxModel model = new SeedBoxModel();

public SeedBoxBlockEntityRenderer(BlockEntityRenderDispatcher dispatcher) {
		super(dispatcher);
	}

@Override
public void render(SeedBoxBlockEntity box, float tickDelta, MatrixStack matrices,
VertexConsumerProvider vertexConsumers, int light, int overlay) {
model.render(box.getCachedState(), box.getAnimationProgress(tickDelta), matrices, vertexConsumers, light, overlay);
}
}
