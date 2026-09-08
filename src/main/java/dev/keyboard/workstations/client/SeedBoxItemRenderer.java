package dev.keyboard.workstations.client;

import dev.keyboard.workstations.WorkstationsMod;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;

public class SeedBoxItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
private final SeedBoxModel model = new SeedBoxModel();

@Override
public void render(ItemStack stack, ModelTransformation.Mode mode, MatrixStack matrices,
VertexConsumerProvider vertexConsumers, int light, int overlay) {
model.render(WorkstationsMod.SEED_BOX_BLOCK.getDefaultState(), 0.0F,
matrices, vertexConsumers, light, overlay);
}
}
