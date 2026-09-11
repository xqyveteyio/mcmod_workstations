package dev.keyboard.workstations.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.keyboard.workstations.entity.FarmerEntity;
import dev.keyboard.workstations.work.WorkerSkin;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the farmer on the vanilla villager model, wearing the mod's own skin.
 */
public class FarmerEntityRenderer extends MobRenderer<FarmerEntity, WorkerRenderState, VillagerModel> {
	private static final float MODEL_SCALE = 0.9375F;

	public FarmerEntityRenderer(EntityRendererProvider.Context context) {
		super(context, new VillagerModel(context.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
		this.addLayer(new WorkerOverlayFeatureRenderer(this, state -> state.skin.hatTexture()));
	}

	@Override
	public Identifier getTextureLocation(WorkerRenderState state) {
		return state.skin.texture();
	}

	@Override
	public WorkerRenderState createRenderState() {
		return new WorkerRenderState();
	}

	@Override
	public void extractRenderState(FarmerEntity entity, WorkerRenderState state, float tickDelta) {
		super.extractRenderState(entity, state, tickDelta);
		state.skin = WorkerSkin.get(WorkerSkin.FARMER, entity.getSkin());
		state.sink = entity.getEntrance().sink(tickDelta);
		state.buried = entity.getEntrance().isBuried();
	}

	@Override
	protected void scale(WorkerRenderState state, PoseStack matrices) {
		matrices.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
	}

	@Override
	public Vec3 getRenderOffset(WorkerRenderState state) {
		return state.sink <= 0.0 ? super.getRenderOffset(state) : new Vec3(0.0, -state.sink, 0.0);
	}

	@Override
	protected boolean shouldShowName(FarmerEntity entity, double distance) {
		return !entity.getEntrance().isBuried() && super.shouldShowName(entity, distance);
	}
}
