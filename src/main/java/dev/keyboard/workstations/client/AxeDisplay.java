package dev.keyboard.workstations.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The axe turning over a lumber station's bench.
 */
final class AxeDisplay {
	private static final float TABLETOP = 12.0F / 16.0F;
	private static final long SPIN_TICKS = 200L;
	private static final float SCALE = 0.9F;
	private static final float HOVER = TABLETOP + 0.09F;

	private AxeDisplay() {
	}

	static float spin(Level world, float tickDelta) {
		return (float) (world.getGameTime() % SPIN_TICKS) + tickDelta;
	}

	static void extract(@Nullable Level world, ItemStackRenderState axe) {
		Minecraft.getInstance().getItemModelResolver().updateForTopItem(
				axe, axe(), ItemDisplayContext.GROUND, world, null, 0);
	}

	static void extract(@Nullable Level world, float tickDelta, LumberBlockEntityRenderer.State state) {
		state.axeTime = world == null ? 0.0F : spin(world, tickDelta);
		extract(world, state.axe);
	}

	static void submit(ItemStackRenderState axe, float axeTime, PoseStack matrices,
			SubmitNodeCollector collector, int light) {
		matrices.pushPose();
		matrices.translate(0.5F, HOVER, 0.5F);
		matrices.mulPose(Axis.YP.rotationDegrees(axeTime / SPIN_TICKS * 360.0F));
		matrices.scale(SCALE, SCALE, SCALE);
		axe.submit(matrices, collector, light, OverlayTexture.NO_OVERLAY, 0);
		matrices.popPose();
	}

	static void submit(LumberBlockEntityRenderer.State state, PoseStack matrices,
			SubmitNodeCollector collector, int light) {
		submit(state.axe, state.axeTime, matrices, collector, light);
	}

	private static ItemStack axe() {
		return new ItemStack(Items.IRON_AXE);
	}
}
