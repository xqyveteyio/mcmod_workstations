package dev.keyboard.workstations.client;

import dev.keyboard.workstations.Mc;

import dev.keyboard.workstations.block.WorkStationBlockEntity;
import dev.keyboard.workstations.work.WorkArea;
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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
//? if >=1.19.3 {
import org.joml.Matrix3f;
import org.joml.Matrix4f;
//?} else {
/* import net.minecraft.util.math.Matrix3f;
import net.minecraft.util.math.Matrix4f; */
//?}

/**
 * Draws a worker's work area for debugging: a wireframe box for the whole volume plus a
 * translucent slab at the station's own level, which is the part you actually want to line up
 * with your fences.
 *
 * <p>Generic over the kind of station, since every station has an area and none of the drawing
 * cares what the work inside it is.
 */
public class WorkAreaHighlightRenderer<T extends WorkStationBlockEntity<?, ?>>
		//? if >=26.1 {
		/* { */
		//?} elif >=1.17 {
		implements BlockEntityRenderer<T> {
		//?} else {
		/* extends BlockEntityRenderer<T> { */
		//?}
	private static final float FLOOR_OFFSET = 0.02F;
	private static final float FILL_ALPHA = 0.10F;
	private static final float LINE_ALPHA = 0.8F;

	//? if >=1.17 {
	public WorkAreaHighlightRenderer(BlockEntityRendererFactory.Context context) {
	}
	//?} else {
	/* public WorkAreaHighlightRenderer(BlockEntityRenderDispatcher dispatcher) {
		super(dispatcher);
	} */
	//?}

	//? if <26.1 {
	@Override
	public boolean rendersOutsideBoundingBox(T blockEntity) {
		return true;
	}

	//? if >=1.17 {
	@Override
	//?}
	public int getRenderDistance() {
		return 192;
	}

	@Override
	//?}
	public void render(T blockEntity, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, int overlay) {
		if (!HighlightState.shouldRender()) {
			return;
		}

		WorkArea area = blockEntity.getWorkArea();
		BlockPos origin = Mc.pos(blockEntity);
		Box box = area.getBox();

		// The block entity is drawn at its own position, so everything shifts into local space.
		float minX = (float) (box.minX - origin.getX());
		float minY = (float) (box.minY - origin.getY());
		float minZ = (float) (box.minZ - origin.getZ());
		float maxX = (float) (box.maxX - origin.getX());
		float maxY = (float) (box.maxY - origin.getY());
		float maxZ = (float) (box.maxZ - origin.getZ());

		renderFloor(matrices, vertexConsumers, minX, minZ, maxX, maxZ);

		VertexConsumer lines = vertexConsumers.getBuffer(RenderLayer.getLines());
		MatrixStack.Entry entry = matrices.peek();
		box(lines, entry, minX, minY, minZ, maxX, maxY, maxZ, 0.3F, 0.85F, 0.95F);
		// The station itself, so the anchor is easy to spot from across the area.
		box(lines, entry, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.85F, 0.25F);
	}

	private void renderFloor(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
			float minX, float minZ, float maxX, float maxZ) {
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

		buffer.vertex(matrix, minX, FLOOR_OFFSET, minZ).color(0.3F, 0.85F, 0.95F, FILL_ALPHA)/*? if <1.21 {*/.next()/*?}*/;
		buffer.vertex(matrix, minX, FLOOR_OFFSET, maxZ).color(0.3F, 0.85F, 0.95F, FILL_ALPHA)/*? if <1.21 {*/.next()/*?}*/;
		buffer.vertex(matrix, maxX, FLOOR_OFFSET, maxZ).color(0.3F, 0.85F, 0.95F, FILL_ALPHA)/*? if <1.21 {*/.next()/*?}*/;
		buffer.vertex(matrix, maxX, FLOOR_OFFSET, minZ).color(0.3F, 0.85F, 0.95F, FILL_ALPHA)/*? if <1.21 {*/.next()/*?}*/;
	}

	private void box(VertexConsumer buffer, MatrixStack.Entry entry, float minX, float minY, float minZ,
			float maxX, float maxY, float maxZ, float red, float green, float blue) {
		line(buffer, entry, minX, minY, minZ, maxX, minY, minZ, red, green, blue);
		line(buffer, entry, maxX, minY, minZ, maxX, minY, maxZ, red, green, blue);
		line(buffer, entry, maxX, minY, maxZ, minX, minY, maxZ, red, green, blue);
		line(buffer, entry, minX, minY, maxZ, minX, minY, minZ, red, green, blue);

		line(buffer, entry, minX, maxY, minZ, maxX, maxY, minZ, red, green, blue);
		line(buffer, entry, maxX, maxY, minZ, maxX, maxY, maxZ, red, green, blue);
		line(buffer, entry, maxX, maxY, maxZ, minX, maxY, maxZ, red, green, blue);
		line(buffer, entry, minX, maxY, maxZ, minX, maxY, minZ, red, green, blue);

		line(buffer, entry, minX, minY, minZ, minX, maxY, minZ, red, green, blue);
		line(buffer, entry, maxX, minY, minZ, maxX, maxY, minZ, red, green, blue);
		line(buffer, entry, maxX, minY, maxZ, maxX, maxY, maxZ, red, green, blue);
		line(buffer, entry, minX, minY, maxZ, minX, maxY, maxZ, red, green, blue);
	}

	private void line(VertexConsumer buffer, MatrixStack.Entry entry, float x1, float y1, float z1,
			float x2, float y2, float z2, float red, float green, float blue) {
		float dx = x2 - x1;
		float dy = y2 - y1;
		float dz = z2 - z1;
		float length = MathHelper.sqrt(dx * dx + dy * dy + dz * dz);

		if (length == 0.0F) {
			return;
		}

		dx /= length;
		dy /= length;
		dz /= length;

		//? if >=1.21 {
		/* buffer.vertex(entry, x1, y1, z1).color(red, green, blue, LINE_ALPHA).normal(entry, dx, dy, dz);
		buffer.vertex(entry, x2, y2, z2).color(red, green, blue, LINE_ALPHA).normal(entry, dx, dy, dz); */
		//?} else {
		Matrix4f position =
				//? if >=1.19.3 {
				entry.getPositionMatrix();
				Matrix3f normal = entry.getNormalMatrix();
				//?} else {
				/* entry.getModel();
				Matrix3f normal = entry.getNormal(); */
				//?}
		buffer.vertex(position, x1, y1, z1).color(red, green, blue, LINE_ALPHA).normal(normal, dx, dy, dz).next();
		buffer.vertex(position, x2, y2, z2).color(red, green, blue, LINE_ALPHA).normal(normal, dx, dy, dz).next();
		//?}
	}
}
