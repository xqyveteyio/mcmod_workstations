package dev.keyboard.workstations.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.object.chest.ChestModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * A chest, drawn in a workstation box's colours out of vanilla's own chest model.
 */
class SeedBoxModel {
	private final Identifier texture;
	private final ChestModel model;

	SeedBoxModel(ModelPart chest, Identifier texture) {
		this.texture = texture;
		this.model = new ChestModel(chest);
	}

	void submit(BlockState state, float openness, PoseStack matrices, SubmitNodeCollector collector,
			int light, int overlay, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
		matrices.pushPose();

		Direction facing = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
				? state.getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
		matrices.translate(0.5F, 0.5F, 0.5F);
		matrices.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
		matrices.translate(-0.5F, -0.5F, -0.5F);

		float eased = 1.0F - openness;
		eased = 1.0F - eased * eased * eased;
		model.setupAnim(eased);
		collector.submitModel(model, eased, matrices, texture, light, overlay, 0, breakProgress);
		matrices.popPose();
	}
}
