package dev.keyboard.workstations.client;

import dev.keyboard.workstations.entity.LumberjackEntity;
import dev.keyboard.workstations.work.WorkerSkin;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.VillagerResemblingModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * Draws the lumberjack on the vanilla villager model, wearing the mod's own skin. Built exactly
 * like {@link FarmerEntityRenderer}, with its own pair of textures so the two workers can be told
 * apart at a glance.
 *
 * <p>Which pair of files it wears comes from the lumberjack itself, since its station picks its
 * look out of {@link WorkerSkin#LUMBERJACK}.
 */
public class LumberjackEntityRenderer
		extends MobEntityRenderer<LumberjackEntity, VillagerResemblingModel<LumberjackEntity>> {
	/** The same shrink vanilla applies, without which the villager model looks oversized. */
	private static final float MODEL_SCALE = 0.9375F;

	public LumberjackEntityRenderer(EntityRendererFactory.Context context) {
		super(context, new VillagerResemblingModel<>(context.getPart(EntityModelLayers.VILLAGER)), 0.5F);
		this.addFeature(new WorkerOverlayFeatureRenderer<>(this, entity -> skin(entity).hatTexture()));
	}

	@Override
	public Identifier getTexture(LumberjackEntity entity) {
		return skin(entity).texture();
	}

	private static WorkerSkin skin(LumberjackEntity entity) {
		return WorkerSkin.get(WorkerSkin.LUMBERJACK, entity.getSkin());
	}

	@Override
	protected void scale(LumberjackEntity entity, MatrixStack matrices, float amount) {
		matrices.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
	}

	/** Buries a lumberjack that is digging its way in, exactly as {@link FarmerEntityRenderer} does. */
	@Override
	public Vec3d getPositionOffset(LumberjackEntity entity, float tickDelta) {
		double sink = entity.getEntrance().sink(tickDelta);
		return sink <= 0.0 ? super.getPositionOffset(entity, tickDelta) : new Vec3d(0.0, -sink, 0.0);
	}

	/** A name tag on a lumberjack still underground is a label lying face up on the floor. */
	@Override
	protected boolean hasLabel(LumberjackEntity entity) {
		return !entity.getEntrance().isBuried() && super.hasLabel(entity);
	}
}
