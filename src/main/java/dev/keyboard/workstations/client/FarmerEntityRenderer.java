package dev.keyboard.workstations.client;

import dev.keyboard.workstations.entity.FarmerEntity;
import dev.keyboard.workstations.work.WorkerSkin;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.MobEntityRenderer;
//? if >=1.17 {
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
//?} else {
/* import net.minecraft.client.render.entity.EntityRenderDispatcher; */
//?}
import net.minecraft.client.render.entity.model.VillagerResemblingModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * Draws the farmer on the vanilla villager model, wearing the mod's own skin. Built exactly like
 * {@link RancherEntityRenderer}, with its own pair of textures so the two workers can be told apart
 * at a glance.
 *
 * <p>Which pair of files it wears comes from the farmer itself, since its station picks its look
 * out of {@link WorkerSkin#FARMER}.
 */
public class FarmerEntityRenderer extends MobEntityRenderer<FarmerEntity,
		//? if >=26.1 {
		/* WorkerRenderState, VillagerResemblingModel> { */
		//?} else {
		VillagerResemblingModel<FarmerEntity>> {
		//?}
	/** The same shrink vanilla applies, without which the villager model looks oversized. */
	private static final float MODEL_SCALE = 0.9375F;

	//? if >=26.1 {
	/* public FarmerEntityRenderer(EntityRendererFactory.Context context) {
		super(context, new VillagerResemblingModel(context.getPart(EntityModelLayers.VILLAGER)), 0.5F);
		this.addFeature(new WorkerOverlayFeatureRenderer(this, state -> state.hatTexture));
	}

	@Override
	public WorkerRenderState createRenderState() {
		return new WorkerRenderState();
	}

	@Override
	public void extractRenderState(FarmerEntity entity, WorkerRenderState state, float tickDelta) {
		super.extractRenderState(entity, state, tickDelta);
		WorkerSkin look = skin(entity);
		state.texture = look.texture();
		state.hatTexture = look.hatTexture();
		state.sink = entity.getEntrance().sink(tickDelta);
	}

	@Override
	public Identifier getTextureLocation(WorkerRenderState state) {
		return state.texture;
	}

	@Override
	protected void scale(WorkerRenderState state, MatrixStack matrices) {
		matrices.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
	}

	@Override
	public Vec3d getRenderOffset(WorkerRenderState state) {
		return state.sink <= 0.0 ? super.getRenderOffset(state) : new Vec3d(0.0, -state.sink, 0.0);
	}

	@Override
	protected boolean shouldShowName(FarmerEntity entity, double distance) {
		return !entity.getEntrance().isBuried() && super.shouldShowName(entity, distance);
	}
	*/
	//?} elif >=1.17 {
	public FarmerEntityRenderer(EntityRendererFactory.Context context) {
		super(context, new VillagerResemblingModel<>(context.getPart(EntityModelLayers.VILLAGER)), 0.5F);
		this.addFeature(new WorkerOverlayFeatureRenderer<>(this, entity -> skin(entity).hatTexture()));
	}
	//?} else {
	/* public FarmerEntityRenderer(EntityRenderDispatcher dispatcher) {
		super(dispatcher, new VillagerResemblingModel<>(0.0F), 0.5F);
		this.addFeature(new WorkerOverlayFeatureRenderer<>(this, entity -> skin(entity).hatTexture()));
	} */
	//?}

	private static WorkerSkin skin(FarmerEntity entity) {
		return WorkerSkin.get(WorkerSkin.FARMER, entity.getSkin());
	}

	//? if <26.1 {
	@Override
	public Identifier getTexture(FarmerEntity entity) {
		return skin(entity).texture();
	}

	/** Hands the body to Minecraft Comes Alive, exactly as {@link RancherEntityRenderer} does. */
	@Override
	public void render(FarmerEntity entity, float yaw, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light) {
		if (WorkerLook.renderAsMcaVillager(entity, entity.getDisguise(), yaw, tickDelta, matrices,
				vertexConsumers, light)) {
			if (hasLabel(entity)) {
				renderLabelIfPresent(entity, entity.getDisplayName(), matrices, vertexConsumers, light
						//? if >=1.21 {
						/* , tickDelta */
						//?}
				);
			}

			return;
		}

		super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
	}

	@Override
	protected void scale(FarmerEntity entity, MatrixStack matrices, float amount) {
		matrices.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
	}

	/** Buries a farmer that is digging its way in, exactly as {@link RancherEntityRenderer} does. */
	@Override
	public Vec3d getPositionOffset(FarmerEntity entity, float tickDelta) {
		double sink = entity.getEntrance().sink(tickDelta);
		return sink <= 0.0 ? super.getPositionOffset(entity, tickDelta) : new Vec3d(0.0, -sink, 0.0);
	}

	/** A name tag on a farmer still underground is a label lying face up on the floor. */
	@Override
	protected boolean hasLabel(FarmerEntity entity) {
		return !entity.getEntrance().isBuried() && super.hasLabel(entity);
	}
	//?}
}
