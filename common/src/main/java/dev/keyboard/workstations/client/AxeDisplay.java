package dev.keyboard.workstations.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The axe turning over a lumber station's bench. Shared so the placed block and the item in your
 * hand show the same thing.
 *
 * <p>It is vanilla's own iron axe, drawn as an item rather than built out of cuboids in the block
 * model. An axe made of boxes at this size is a few pixels of grey lump; the real one carries the
 * shape players already read as an axe, and it follows resource packs.
 *
 * <p>Drawn the way a dropped item is, hanging in the air and turning. That is a motion players
 * already know means "here is an item", which is the whole job of the thing sat on the bench.
 */
final class AxeDisplay {
	private static final ItemStack AXE = new ItemStack(Items.IRON_AXE);

	/** Top of the bench in the block model, which the axe hangs above. */
	private static final float TABLETOP = 12.0F / 16.0F;

	/**
	 * Ticks for one full turn. Vanilla spins a dropped item in about 125; this is a loiter.
	 *
	 * <p>World time is folded into this range so it stays a small number: a turn ends where it
	 * began, so the fold is invisible, and a float holding hundreds of thousands of ticks is not.
	 */
	private static final long SPIN_TICKS = 200L;

	/**
	 * Size on top of the dropped item transform, which is itself half scale. This leaves an axe
	 * a little over a third of a block long: big enough to read across a room, and close enough
	 * to the bench that it does not look like it belongs to the block above.
	 */
	private static final float SCALE = 0.9F;

	/**
	 * Height the item transform is applied at. That transform lifts the axe another two pixels of
	 * its own, so the gap the axe actually clears the bench by is smaller than this looks.
	 */
	private static final float HOVER = TABLETOP + 0.09F;

	private AxeDisplay() {
	}

	/** Draws the axe in the block's own coordinates. */
	static void render(@Nullable World world, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, int overlay) {
		// No world is the inventory and the title screen, where nothing is ticking. The axe still
		// has to be drawn, so it simply hangs still.
		float time = world == null ? 0.0F : (float) (world.getTime() % SPIN_TICKS) + tickDelta;

		matrices.push();
		matrices.translate(0.5F, HOVER, 0.5F);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time / SPIN_TICKS * 360.0F));
		matrices.scale(SCALE, SCALE, SCALE);
		// GROUND is the mode vanilla draws a dropped item in, so the axe is sized and stood up
		// exactly as one lying in the world would be.
		MinecraftClient.getInstance().getItemRenderer().renderItem(AXE, ModelTransformationMode.GROUND,
				light, overlay, matrices, vertexConsumers, world, 0);
		matrices.pop();
	}
}
