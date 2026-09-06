package dev.keyboard.breederscarecrow.client;

import dev.keyboard.breederscarecrow.block.ScarecrowBlockEntity;
import dev.keyboard.breederscarecrow.pen.PenRegion;
import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws the detected pen so the shape can be checked in game: a translucent floor plus
 * a one block high outline everywhere the pen touches a wall. Green means the pen is closed,
 * red means animals could walk out.
 */
public class PenHighlightRenderer implements BlockEntityRenderer<ScarecrowBlockEntity> {
	private static final float FLOOR_OFFSET = 0.02F;
	private static final float WALL_HEIGHT = 1.0F;
	private static final float FILL_ALPHA = 0.16F;
	private static final float LINE_ALPHA = 0.85F;

	public PenHighlightRenderer(BlockEntityRendererFactory.Context context) {
	}

	@Override
	public boolean rendersOutsideBoundingBox(ScarecrowBlockEntity blockEntity) {
		return true;
	}

	@Override
	public int getRenderDistance() {
		return 192;
	}

	@Override
	public void render(ScarecrowBlockEntity blockEntity, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, int overlay) {
		if (!HighlightState.shouldRender()) {
			return;
		}

		PenRegion region = blockEntity.getPenRegion();

		if (region.isEmpty()) {
			return;
		}

		BlockPos origin = blockEntity.getPos();
		float red = region.isEnclosed() ? 0.25F : 0.95F;
		float green = region.isEnclosed() ? 0.95F : 0.25F;
		float blue = 0.35F;
		float baseY = region.getFloorY() - origin.getY();

		renderFloor(region, matrices, vertexConsumers, origin, baseY, red, green, blue);
		renderWalls(region, matrices, vertexConsumers, origin, baseY, red, green, blue);
	}

	private void renderFloor(PenRegion region, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
			BlockPos origin, float baseY, float red, float green, float blue) {
		VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getDebugQuads());
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		float y = baseY + FLOOR_OFFSET;

		for (LongIterator iterator = region.getCells().iterator(); iterator.hasNext(); ) {
			long packed = iterator.nextLong();
			float x = BlockPos.unpackLongX(packed) - origin.getX();
			float z = BlockPos.unpackLongZ(packed) - origin.getZ();

			buffer.vertex(matrix, x, y, z).color(red, green, blue, FILL_ALPHA).next();
			buffer.vertex(matrix, x, y, z + 1.0F).color(red, green, blue, FILL_ALPHA).next();
			buffer.vertex(matrix, x + 1.0F, y, z + 1.0F).color(red, green, blue, FILL_ALPHA).next();
			buffer.vertex(matrix, x + 1.0F, y, z).color(red, green, blue, FILL_ALPHA).next();
		}
	}

	private void renderWalls(PenRegion region, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
			BlockPos origin, float baseY, float red, float green, float blue) {
		VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getLines());
		MatrixStack.Entry entry = matrices.peek();
		float top = baseY + WALL_HEIGHT;

		for (LongIterator iterator = region.getCells().iterator(); iterator.hasNext(); ) {
			long packed = iterator.nextLong();
			int cellX = BlockPos.unpackLongX(packed);
			int cellZ = BlockPos.unpackLongZ(packed);
			float x = cellX - origin.getX();
			float z = cellZ - origin.getZ();

			if (!region.containsColumn(cellX, cellZ - 1)) {
				edge(buffer, entry, x, z, x + 1.0F, z, baseY, top, red, green, blue);
			}

			if (!region.containsColumn(cellX, cellZ + 1)) {
				edge(buffer, entry, x, z + 1.0F, x + 1.0F, z + 1.0F, baseY, top, red, green, blue);
			}

			if (!region.containsColumn(cellX - 1, cellZ)) {
				edge(buffer, entry, x, z, x, z + 1.0F, baseY, top, red, green, blue);
			}

			if (!region.containsColumn(cellX + 1, cellZ)) {
				edge(buffer, entry, x + 1.0F, z, x + 1.0F, z + 1.0F, baseY, top, red, green, blue);
			}
		}

		// The scarecrow itself, so the pen anchor is easy to spot from a distance.
		box(buffer, entry, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 0.35F, 0.6F, 1.0F);
	}

	/** One vertical quad outline standing on the given horizontal edge. */
	private void edge(VertexConsumer buffer, MatrixStack.Entry entry, float x1, float z1, float x2, float z2,
			float bottom, float top, float red, float green, float blue) {
		line(buffer, entry, x1, bottom, z1, x2, bottom, z2, red, green, blue);
		line(buffer, entry, x1, top, z1, x2, top, z2, red, green, blue);
		line(buffer, entry, x1, bottom, z1, x1, top, z1, red, green, blue);
		line(buffer, entry, x2, bottom, z2, x2, top, z2, red, green, blue);
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

		Matrix4f position = entry.getPositionMatrix();
		Matrix3f normal = entry.getNormalMatrix();
		buffer.vertex(position, x1, y1, z1).color(red, green, blue, LINE_ALPHA).normal(normal, dx, dy, dz).next();
		buffer.vertex(position, x2, y2, z2).color(red, green, blue, LINE_ALPHA).normal(normal, dx, dy, dz).next();
	}
}
