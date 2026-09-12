package dev.keyboard.workstations.client;

import dev.keyboard.workstations.WorkstationsMod;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;

public class SeedBoxItemRenderer implements BuiltinItemRenderers.DynamicItemRenderer {
private final SeedBoxModel model = new SeedBoxModel();

@Override
public void render(ItemStack stack, ModelTransformation.Mode mode, MatrixStack matrices,
VertexConsumerProvider vertexConsumers, int light, int overlay) {
model.render(WorkstationsMod.SEED_BOX_BLOCK.get().getDefaultState(), 0.0F,
matrices, vertexConsumers, light, overlay);
}
}
