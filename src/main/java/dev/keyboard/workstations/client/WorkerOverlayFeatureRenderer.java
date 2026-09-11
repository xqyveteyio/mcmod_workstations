package dev.keyboard.workstations.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.Function;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * Draws a worker's model a second time with another texture, the way vanilla layers a villager's
 * biome and profession clothing over the base villager skin.
 */
public class WorkerOverlayFeatureRenderer extends RenderLayer<WorkerRenderState, VillagerModel> {
	private final Function<WorkerRenderState, Identifier> texture;

	public WorkerOverlayFeatureRenderer(RenderLayerParent<WorkerRenderState, VillagerModel> context,
			Function<WorkerRenderState, Identifier> texture) {
		super(context);
		this.texture = texture;
	}

	@Override
	public void submit(PoseStack matrices, SubmitNodeCollector collector, int light, WorkerRenderState state,
			float yRot, float xRot) {
		if (state.isInvisible) {
			return;
		}

		collector.submitModel(getParentModel(), state, matrices, texture.apply(state), light,
				OverlayTexture.NO_OVERLAY, 0, null);
	}
}
