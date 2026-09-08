package dev.keyboard.workstations.client;

import dev.keyboard.workstations.entity.RancherEntity;
import dev.keyboard.workstations.work.WorkerSkin;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.VillagerResemblingModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public class RancherEntityRenderer extends MobEntityRenderer<RancherEntity, VillagerResemblingModel<RancherEntity>> {
private static final float MODEL_SCALE = 0.9375F;

public RancherEntityRenderer(EntityRenderDispatcher dispatcher, net.fabricmc.fabric.api.client.rendereregistry.v1.EntityRendererRegistry.Context context) {
super(dispatcher, new VillagerResemblingModel<RancherEntity>(0.0F), 0.5F);
this.addFeature(new WorkerOverlayFeatureRenderer<>(this, entity -> skin(entity).hatTexture()));
}

@Override
public Identifier getTexture(RancherEntity entity) {
return skin(entity).texture();
}

private static WorkerSkin skin(RancherEntity entity) {
return WorkerSkin.get(WorkerSkin.RANCHER, entity.getSkin());
}

@Override
public void render(RancherEntity entity, float yaw, float tickDelta, MatrixStack matrices,
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
protected void scale(RancherEntity entity, MatrixStack matrices, float amount) {
matrices.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
}

@Override
protected boolean hasLabel(RancherEntity entity) {
return !entity.getEntrance().isBuried() && super.hasLabel(entity);
}
}
