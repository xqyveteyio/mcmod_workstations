package dev.keyboard.workstations.client;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Custom {@code builtin/entity} item drawing, registered per loader. Architectury has no common
 * hook for this in 1.20.1, so Fabric talks to Fabric API and Forge hangs a renderer off the item.
 */
public final class BuiltinItemRenderers {
	private static final Map<Item, DynamicItemRenderer> RENDERERS = new IdentityHashMap<>();

	@FunctionalInterface
	public interface DynamicItemRenderer {
		void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices,
				VertexConsumerProvider vertexConsumers, int light, int overlay);
	}

	private BuiltinItemRenderers() {
	}

	public static void register(Item item, DynamicItemRenderer renderer) {
		RENDERERS.put(item, renderer);
		registerPlatform(item, renderer);
	}

	@Nullable
	public static DynamicItemRenderer get(Item item) {
		return RENDERERS.get(item);
	}

	@ExpectPlatform
	public static void registerPlatform(Item item, DynamicItemRenderer renderer) {
		throw new AssertionError();
	}
}
