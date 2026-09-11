package dev.keyboard.workstations.client;

import dev.keyboard.workstations.entity.RancherEntity;
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
 * Draws the rancher on the vanilla villager model, wearing the mod's own skin. The model layer is
 * the one vanilla already bakes for its own villagers, so nothing extra is registered; swapping in
 * a custom model later means changing only this class.
 *
 * <p>The hat rides on a separate overlay texture, following vanilla, which keeps a villager's base
 * skin apart from its biome and profession clothing. Both files use the same villager UV layout, so
 * the hat lives in the model's hat and hat rim boxes, which the base skin must leave blank.
 *
 * <p>Which pair of files that is comes from the rancher itself, since its station picks its look
 * out of {@link WorkerSkin#RANCHER}.
 */
public class RancherEntityRenderer extends MobEntityRenderer<RancherEntity, VillagerResemblingModel<RancherEntity>> {
	/** The same shrink vanilla applies, without which the villager model looks oversized. */
	private static final float MODEL_SCALE = 0.9375F;

	//? if >=1.17 {
	public RancherEntityRenderer(EntityRendererFactory.Context context) {
		super(context, new VillagerResemblingModel<>(context.getPart(EntityModelLayers.VILLAGER)), 0.5F);
		this.addFeature(new WorkerOverlayFeatureRenderer<>(this, entity -> skin(entity).hatTexture()));
	}
	//?} else {
	/* public RancherEntityRenderer(EntityRenderDispatcher dispatcher) {
		super(dispatcher, new VillagerResemblingModel<>(0.0F), 0.5F);
		this.addFeature(new WorkerOverlayFeatureRenderer<>(this, entity -> skin(entity).hatTexture()));
	} */
	//?}

	@Override
	public Identifier getTexture(RancherEntity entity) {
		return skin(entity).texture();
	}

	private static WorkerSkin skin(RancherEntity entity) {
		return WorkerSkin.get(WorkerSkin.RANCHER, entity.getSkin());
	}

	/**
	 * Hands the body over to Minecraft Comes Alive when that mod is installed, so that a rancher
	 * standing among its villagers does not look like the one squidward left in the room. The name
	 * plate stays this renderer's own, since the stand in MCA draws knows nothing about ranching.
	 */
	@Override
	public void render(RancherEntity entity, float yaw, float tickDelta, MatrixStack matrices,
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
	protected void scale(RancherEntity entity, MatrixStack matrices, float amount) {
		matrices.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
	}

	/**
	 * Buries a rancher that is digging its way in, letting the ground itself hide everything that
	 * has not surfaced yet. This is vanilla's own offset hook rather than a shift of the matrix
	 * inside the render, so only the drawing moves: the rancher stands where it always stood, and
	 * the shadow stays on the floor to mark the spot it is coming up through.
	 */
	@Override
	public Vec3d getPositionOffset(RancherEntity entity, float tickDelta) {
		double sink = entity.getEntrance().sink(tickDelta);
		return sink <= 0.0 ? super.getPositionOffset(entity, tickDelta) : new Vec3d(0.0, -sink, 0.0);
	}

	/** A name tag on a rancher still underground is a label lying face up on the floor. */
	@Override
	protected boolean hasLabel(RancherEntity entity) {
		return !entity.getEntrance().isBuried() && super.hasLabel(entity);
	}
}
