package dev.keyboard.workstations.client;

import net.minecraft.block.BlockState;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;

/**
 * A chest, drawn in this box's colours out of vanilla's own chest model.
 *
 * <p>The three parts are borrowed rather than built. Vanilla already loads a single chest's base,
 * lid and latch under {@link EntityModelLayers#CHEST}, laid out against a 64 by 64 sheet, so the
 * box's texture only has to follow that same layout to sit on them correctly. Nothing here models
 * anything.
 *
 * <p>Vanilla's chest renderer cannot simply be reused, close as this is to it: which texture it
 * draws with is decided inside it, from whether the block entity is an ender or a trapped chest, so
 * another kind of chest has no way to answer. This exists to supply the texture, and is shared by
 * the placed block and the item in hand, and by every box that looks like a chest.
 */
class StationChestModel {
	private final Identifier texture;
	private final ModelPart base;
	private final ModelPart lid;
	private final ModelPart latch;

	StationChestModel(ModelPart chest, Identifier texture) {
		this.texture = texture;
		base = chest.getChild("bottom");
		lid = chest.getChild("lid");
		latch = chest.getChild("lock");
	}

	/**
	 * @param openness how far the lid has swung, 0 shut and 1 wide open. The item in hand is always
	 *     given 0, having no block entity to have been opened.
	 */
	void render(BlockState state, float openness, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, int overlay) {
		matrices.push();

		// Turned about its own middle, so the latch ends up on the face the chest was put down
		// looking out of rather than swinging the whole body off the block.
		Direction facing = state.contains(Properties.HORIZONTAL_FACING)
				? state.get(Properties.HORIZONTAL_FACING)
				: Direction.NORTH;
		matrices.translate(0.5F, 0.5F, 0.5F);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-facing.asRotation()));
		matrices.translate(-0.5F, -0.5F, -0.5F);

		// Vanilla's easing, which sets the lid moving quickly and lets it settle shut.
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
