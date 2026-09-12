package dev.keyboard.workstations.client;

import dev.keyboard.workstations.entity.FarmerEntity;
import dev.keyboard.workstations.work.WorkerSkin;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.VillagerResemblingModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public class FarmerEntityRenderer extends MobEntityRenderer<FarmerEntity, VillagerResemblingModel<FarmerEntity>> {
private static final float MODEL_SCALE = 0.9375F;

public FarmerEntityRenderer(EntityRenderDispatcher dispatcher) {
super(dispatcher, new VillagerResemblingModel<FarmerEntity>(0.0F), 0.5F);
this.addFeature(new WorkerOverlayFeatureRenderer<>(this, entity -> skin(entity).hatTexture()));
}

@Override
public Identifier getTexture(FarmerEntity entity) {
return skin(entity).texture();
}

private static WorkerSkin skin(FarmerEntity entity) {
return WorkerSkin.get(WorkerSkin.FARMER, entity.getSkin());
}

@Override
public void render(FarmerEntity entity, float yaw, float tickDelta, MatrixStack matrices,
VertexConsumerProvider vertexConsumers, int light) {
double sink = entity.getEntrance().sink(tickDelta);
if (sink > 0.0) {
matrices.push();
matrices.translate(0.0, -sink, 0.0);
super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
matrices.pop();
} else {
super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
}
}

@Override
protected void scale(FarmerEntity entity, MatrixStack matrices, float amount) {
matrices.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
}

@Override
protected boolean hasLabel(FarmerEntity entity) {
return !entity.getEntrance().isBuried() && super.hasLabel(entity);
}
}
