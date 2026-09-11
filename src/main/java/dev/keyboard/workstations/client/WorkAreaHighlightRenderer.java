package dev.keyboard.workstations.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.keyboard.workstations.block.WorkStationBlockEntity;
import dev.keyboard.workstations.work.WorkArea;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * Draws a worker's work area for debugging: a wireframe box for the whole volume plus a
 * translucent slab at the station's own level, which is the part you actually want to line up
 * with your fences.
 */
final class WorkAreaHighlightRenderer {
	private static final float FLOOR_OFFSET = 0.02F;
	private static final float FILL_ALPHA = 0.10F;
	private static final float LINE_ALPHA = 0.8F;

	private WorkAreaHighlightRenderer() {
	}

	static void extract(WorkStationBlockEntity<?, ?> station, HighlightRenderState state) {
		if (!HighlightState.shouldRender()) {
			state.visible = false;
			return;
		}

		WorkArea area = station.getWorkArea();
		BlockPos origin = station.getBlockPos();
		AABB box = area.getBox();

		state.visible = true;
		state.minX = (float) (box.minX - origin.getX());
		state.minY = (float) (box.minY - origin.getY());
		state.minZ = (float) (box.minZ - origin.getZ());
		state.maxX = (float) (box.maxX - origin.getX());
		state.maxY = (float) (box.maxY - origin.getY());
		state.maxZ = (float) (box.maxZ - origin.getZ());
	}

	static void submit(HighlightRenderState state, PoseStack matrices, SubmitNodeCollector collector) {
		if (!state.visible) {
			return;
		}

		float minX = state.minX;
		float minY = state.minY;
		float minZ = state.minZ;
		float maxX = state.maxX;
		float maxY = state.maxY;
		float maxZ = state.maxZ;

		collector.submitCustomGeometry(matrices, RenderTypes.debugQuads(), (pose, buffer) -> {
			Matrix4f matrix = pose.pose();
			buffer.addVertex(matrix, minX, FLOOR_OFFSET, minZ).setColor(0.3F, 0.85F, 0.95F, FILL_ALPHA);
			buffer.addVertex(matrix, minX, FLOOR_OFFSET, maxZ).setColor(0.3F, 0.85F, 0.95F, FILL_ALPHA);
			buffer.addVertex(matrix, maxX, FLOOR_OFFSET, maxZ).setColor(0.3F, 0.85F, 0.95F, FILL_ALPHA);
			buffer.addVertex(matrix, maxX, FLOOR_OFFSET, minZ).setColor(0.3F, 0.85F, 0.95F, FILL_ALPHA);
		});

		collector.submitCustomGeometry(matrices, RenderTypes.lines(), (pose, buffer) -> {
			box(buffer, pose, minX, minY, minZ, maxX, maxY, maxZ, 0.3F, 0.85F, 0.95F);
			box(buffer, pose, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.85F, 0.25F);
		});
	}

	private static void box(VertexConsumer buffer, PoseStack.Pose entry, float minX, float minY, float minZ,
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

	private static void line(VertexConsumer buffer, PoseStack.Pose entry, float x1, float y1, float z1,
			float x2, float y2, float z2, float red, float green, float blue) {
		float dx = x2 - x1;
		float dy = y2 - y1;
		float dz = z2 - z1;
		float length = Mth.sqrt(dx * dx + dy * dy + dz * dz);

		if (length == 0.0F) {
			return;
		}

		dx /= length;
		dy /= length;
		dz /= length;

		buffer.addVertex(entry, x1, y1, z1).setColor(red, green, blue, LINE_ALPHA)
				.setNormal(entry, dx, dy, dz).setLineWidth(2.0F);
		buffer.addVertex(entry, x2, y2, z2).setColor(red, green, blue, LINE_ALPHA)
				.setNormal(entry, dx, dy, dz).setLineWidth(2.0F);
	}

	static class HighlightRenderState extends BlockEntityRenderState {
		boolean visible;
		float minX;
		float minY;
		float minZ;
		float maxX;
		float maxY;
		float maxZ;
	}
}
