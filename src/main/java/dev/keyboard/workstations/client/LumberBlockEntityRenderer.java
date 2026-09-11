package dev.keyboard.workstations.client;

import dev.keyboard.workstations.Mc;

import dev.keyboard.workstations.block.LumberBlockEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
//? if >=1.17 {
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
//?} else {
/* import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher; */
//?}
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
//? if >=1.19.3 {
import org.joml.Matrix4f;
//?} else {
/* import net.minecraft.util.math.Matrix4f; */
//?}

/**
 * Everything the lumber station draws: the axe turning over the bench, the work area highlight,
 * and a tile over every stump it has on its books.
 *
 * <p>The area box on its own only says how far the wood reaches. Which holes inside it the
 * lumberjack means to plant again is a separate question, and the usual reason a stump appears
 * to be ignored, so the stumps are drawn too.
 */
public class LumberBlockEntityRenderer
		//? if >=26.1 {
		/* implements BlockEntityRenderer<LumberBlockEntity, net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState> { */
		//?} elif >=1.17 {
		implements BlockEntityRenderer<LumberBlockEntity> {
		//?} else {
		/* extends BlockEntityRenderer<LumberBlockEntity> { */
		//?}
	private static final float SURFACE_OFFSET = 0.05F;
	private static final float INSET = 0.06F;
	private static final float ALPHA = 0.35F;
	/** A stump waiting for a sapling. */
	private static final float[] STUMP = {0.72F, 0.52F, 0.28F};

	private final WorkAreaHighlightRenderer<LumberBlockEntity> highlight;

	//? if >=1.17 {
	public LumberBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
		this.highlight = new WorkAreaHighlightRenderer<>(context);
	}
	//?} else {
	/* public LumberBlockEntityRenderer(BlockEntityRenderDispatcher dispatcher) {
		super(dispatcher);
		this.highlight = new WorkAreaHighlightRenderer<>(dispatcher);
	} */
	//?}

	//? if >=26.1 {
	/* @Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	@Override
	public int getViewDistance() {
		return 192;
	}

	@Override
	public net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState createRenderState() {
		return new net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState();
	}

	@Override
	public void extractRenderState(LumberBlockEntity station,
			net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState state, float tickDelta,
			net.minecraft.world.phys.Vec3 camera,
			net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay overlay) {
		net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState.extractBase(station, state, overlay);
	}

	@Override
	public void submit(net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState state,
			MatrixStack matrices, net.minecraft.client.renderer.SubmitNodeCollector collector,
			net.minecraft.client.renderer.state.level.CameraRenderState camera) {
	}
	*/
	//?} else {
	@Override
	public boolean rendersOutsideBoundingBox(LumberBlockEntity station) {
		return true;
	}

	//? if >=1.17 {
	@Override
	//?}
	public int getRenderDistance() {
		return 192;
	}

	@Override
	public void render(LumberBlockEntity station, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, int overlay) {
		highlight.render(station, tickDelta, matrices, vertexConsumers, light, overlay);
		AxeDisplay.render(station.getWorld(), tickDelta, matrices, vertexConsumers, light, overlay);

		if (station.getWorld() == null || !HighlightState.shouldRender()) {
			return;
		}

		VertexConsumer buffer = vertexConsumers.getBuffer(
				//? if >=1.17 {
				RenderLayer.getDebugQuads()
				//?} else {
				/* RenderLayer.getLines() */
				//?}
		);
		//? if >=1.19.3 {
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		//?} else {
		/* Matrix4f matrix = matrices.peek().getModel(); */
		//?}
		BlockPos origin = Mc.pos(station);

		for (BlockPos stump : station.getStumps()) {
			tile(buffer, matrix, origin, stump);
		}
	}
	//?}

	private static void tile(VertexConsumer buffer, Matrix4f matrix, BlockPos origin, BlockPos stump) {
		float minX = stump.getX() - origin.getX() + INSET;
		float maxX = stump.getX() - origin.getX() + 1.0F - INSET;
		float minZ = stump.getZ() - origin.getZ() + INSET;
		float maxZ = stump.getZ() - origin.getZ() + 1.0F - INSET;
		float y = stump.getY() - origin.getY() + SURFACE_OFFSET;

		buffer.vertex(matrix, minX, y, minZ).color(STUMP[0], STUMP[1], STUMP[2], ALPHA)/*? if <1.21 {*/.next()/*?}*/;
		buffer.vertex(matrix, minX, y, maxZ).color(STUMP[0], STUMP[1], STUMP[2], ALPHA)/*? if <1.21 {*/.next()/*?}*/;
		buffer.vertex(matrix, maxX, y, maxZ).color(STUMP[0], STUMP[1], STUMP[2], ALPHA)/*? if <1.21 {*/.next()/*?}*/;
		buffer.vertex(matrix, maxX, y, minZ).color(STUMP[0], STUMP[1], STUMP[2], ALPHA)/*? if <1.21 {*/.next()/*?}*/;
	}
}
