package dev.keyboard.workstations.client;

import dev.keyboard.workstations.WorkstationsMod;
import net.minecraft.block.BlockState;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3f;

class SeedBoxModel {
private final Identifier texture;
private final ModelPart base;
private final ModelPart lid;
private final ModelPart latch;

SeedBoxModel() {
this(WorkstationsMod.id("textures/entity/seed_box.png"));
}

SeedBoxModel(Identifier texture) {
this.texture = texture;
base = new ModelPart(64, 64, 0, 19);
base.addCuboid(1.0F, 0.0F, 1.0F, 14.0F, 10.0F, 14.0F, 0.0F);

lid = new ModelPart(64, 64, 0, 0);
lid.addCuboid(1.0F, 10.0F, 1.0F, 14.0F, 5.0F, 14.0F, 0.0F);

latch = new ModelPart(64, 64, 0, 0);
latch.addCuboid(7.0F, 9.0F, 15.0F, 2.0F, 4.0F, 1.0F, 0.0F);
}

void render(BlockState state, float openness, MatrixStack matrices,
VertexConsumerProvider vertexConsumers, int light, int overlay) {
matrices.push();

Direction facing = state.contains(Properties.HORIZONTAL_FACING)
		? state.get(Properties.HORIZONTAL_FACING) : Direction.NORTH;
matrices.translate(0.5F, 0.5F, 0.5F);
matrices.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion(-facing.asRotation()));
matrices.translate(-0.5F, -0.5F, -0.5F);

float eased = 1.0F - openness;
eased = 1.0F - eased * eased * eased;

VertexConsumer vertices = vertexConsumers.getBuffer(RenderLayer.getEntityCutout(texture));
lid.pitch = -(eased * ((float) Math.PI / 2.0F));
latch.pitch = lid.pitch;
lid.render(matrices, vertices, light, overlay);
latch.render(matrices, vertices, light, overlay);
base.render(matrices, vertices, light, overlay);
matrices.pop();
}
}
