package dev.keyboard.workstations.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import dev.keyboard.workstations.WorkstationsMod;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * The extra geometry that used to ride on {@code BuiltinItemRendererRegistry}: animals on the ranch
 * item, the spinning axe on the lumber item. 26.1 draws those through special item models instead.
 */
final class StationItemModels {
	private StationItemModels() {
	}

	static void register() {
		SpecialModelRenderers.ID_MAPPER.put(WorkstationsMod.id("ranch_tabletop"),
				RanchTabletop.Unbaked.MAP_CODEC);
		SpecialModelRenderers.ID_MAPPER.put(WorkstationsMod.id("lumber_axe"),
				LumberAxe.Unbaked.MAP_CODEC);
	}

	private static float tickDelta() {
		return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
	}

	private static final class RanchTabletop implements NoDataSpecialModelRenderer {
		private static final RanchTabletop INSTANCE = new RanchTabletop();
		private static final CameraRenderState CAMERA = TabletopDisplay.itemCamera();

		@Override
		public void submit(PoseStack matrices, SubmitNodeCollector collector, int light, int overlay,
				boolean foil, int outlineColor) {
			Minecraft client = Minecraft.getInstance();

			if (client.level == null) {
				return;
			}

			List<TabletopDisplay.Drawn> animals = new ArrayList<>();
			TabletopDisplay.extract(client.level, tickDelta(), animals);
			TabletopDisplay.submit(animals, matrices, collector, CAMERA, light);
		}

		@Override
		public void getExtents(Consumer<Vector3fc> output) {
			output.accept(new Vector3f(0.0F, 0.0F, 0.0F));
			output.accept(new Vector3f(1.0F, 1.0F, 1.0F));
		}

		private record Unbaked() implements NoDataSpecialModelRenderer.Unbaked {
			private static final Unbaked INSTANCE = new Unbaked();
			private static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(INSTANCE);

			@Override
			public MapCodec<? extends NoDataSpecialModelRenderer.Unbaked> type() {
				return MAP_CODEC;
			}

			@Override
			public SpecialModelRenderer<Void> bake(SpecialModelRenderer.BakingContext context) {
				return RanchTabletop.INSTANCE;
			}
		}
	}

	private static final class LumberAxe implements NoDataSpecialModelRenderer {
		private static final LumberAxe INSTANCE = new LumberAxe();
		private final ItemStackRenderState axe = new ItemStackRenderState();

		@Override
		public void submit(PoseStack matrices, SubmitNodeCollector collector, int light, int overlay,
				boolean foil, int outlineColor) {
			Minecraft client = Minecraft.getInstance();
			AxeDisplay.extract(client.level, axe);
			float spin = client.level == null ? 0.0F : AxeDisplay.spin(client.level, tickDelta());
			AxeDisplay.submit(axe, spin, matrices, collector, light);
		}

		@Override
		public void getExtents(Consumer<Vector3fc> output) {
			output.accept(new Vector3f(0.0F, 0.0F, 0.0F));
			output.accept(new Vector3f(1.0F, 1.0F, 1.0F));
		}

		private record Unbaked() implements NoDataSpecialModelRenderer.Unbaked {
			private static final Unbaked INSTANCE = new Unbaked();
			private static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(INSTANCE);

			@Override
			public MapCodec<? extends NoDataSpecialModelRenderer.Unbaked> type() {
				return MAP_CODEC;
			}

			@Override
			public SpecialModelRenderer<Void> bake(SpecialModelRenderer.BakingContext context) {
				return LumberAxe.INSTANCE;
			}
		}
	}
}
