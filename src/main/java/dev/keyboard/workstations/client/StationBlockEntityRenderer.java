package dev.keyboard.workstations.client;

import dev.keyboard.workstations.block.RanchBlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
//? if >=1.17 {
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
//?} else {
/* import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher; */
//?}
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.world.World;

/**
 * Everything the station draws: the work area highlight, and the miniature pen on the tabletop. The
 * two are one renderer because a block entity type may only have one.
 */
public class StationBlockEntityRenderer
		//? if >=26.1 {
		/* implements BlockEntityRenderer<RanchBlockEntity, net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState> { */
		//?} elif >=1.17 {
		implements BlockEntityRenderer<RanchBlockEntity> {
		//?} else {
		/* extends BlockEntityRenderer<RanchBlockEntity> { */
		//?}
	private final WorkAreaHighlightRenderer<RanchBlockEntity> highlight;

	//? if >=1.17 {
	public StationBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
		this.highlight = new WorkAreaHighlightRenderer<>(context);
	}
	//?} else {
	/* public StationBlockEntityRenderer(BlockEntityRenderDispatcher dispatcher) {
		super(dispatcher);
		this.highlight = new WorkAreaHighlightRenderer<>(dispatcher);
	} */
	//?}

	//? if <26.1 {
	@Override
	public boolean rendersOutsideBoundingBox(RanchBlockEntity station) {
		return true;
	}
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

	private RanchBlockEntity lastStation;
	private float lastTick;

	@Override
	public void extractRenderState(RanchBlockEntity station,
			net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState state, float tickDelta,
			net.minecraft.world.phys.Vec3 camera,
			net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay overlay) {
		net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState.extractBase(station, state, overlay);
		lastStation = station;
		lastTick = tickDelta;
	}

	@Override
	public void submit(net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState state,
			MatrixStack matrices, net.minecraft.client.renderer.SubmitNodeCollector collector,
			net.minecraft.client.renderer.state.level.CameraRenderState camera) {
	}
	*/
	//?} elif >=1.17 {
	@Override
	public int getRenderDistance() {
		return 192;
	}
	//?} else {
	/* public int getRenderDistance() {
		return 192;
	} */
	//?}

	//? if <26.1 {
	@Override
	public void render(RanchBlockEntity station, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, int overlay) {
		highlight.render(station, tickDelta, matrices, vertexConsumers, light, overlay);

		World world = station.getWorld();

		if (world != null) {
			TabletopDisplay.render(world, tickDelta, matrices, vertexConsumers, light);
		}
	}
	//?}
}
