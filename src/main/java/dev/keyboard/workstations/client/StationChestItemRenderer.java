package dev.keyboard.workstations.client;

//? if <26.1 {
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
//?}
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
//? if >=1.17 {
import net.minecraft.client.render.entity.model.EntityModelLayers;
//?}
//? if >=1.19.4 {
import net.minecraft.client.render.model.json.ModelTransformationMode;
//?} else {
/* import net.minecraft.client.render.model.json.ModelTransformation; */
//?}
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * A box in hand, in the inventory and on the ground, drawn as the same shut chest.
 *
 * <p>The item model is {@code builtin/entity}, carrying no geometry of its own, the way vanilla's
 * chest item does.
 */
public class StationChestItemRenderer
		//? if <26.1 {
		implements BuiltinItemRendererRegistry.DynamicItemRenderer {
		//?} else {
		/* { */
		//?}
	private final Block block;
	private final Identifier texture;

	/**
	 * Built on first use rather than in the constructor: the chest model is only there to borrow
	 * once resources have been loaded, which is later than client startup.
	 */
	@Nullable
	private StationChestModel model;

	public StationChestItemRenderer(Block block, Identifier texture) {
		this.block = block;
		this.texture = texture;
	}

	//? if <26.1 {
	@Override
	public void render(ItemStack stack,
			//? if >=1.19.4 {
			ModelTransformationMode mode,
			//?} else {
			/* ModelTransformation.Mode mode, */
			//?}
			MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, int overlay) {
		if (model == null) {
			//? if >=1.17 {
			model = new StationChestModel(MinecraftClient.getInstance()
					.getEntityModelLoader().getModelPart(EntityModelLayers.CHEST), texture);
			//?} else {
			/* model = new StationChestModel(texture); */
			//?}
		}

		model.render(block.getDefaultState(), 0.0F, matrices, vertexConsumers, light, overlay);
	}
	//?}
}
