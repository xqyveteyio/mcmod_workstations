package dev.keyboard.breederscarecrow.pen;

import dev.keyboard.breederscarecrow.ModConfig;
import dev.keyboard.breederscarecrow.block.ScarecrowBlockEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps track of the loaded scarecrows so a block change can re-scan the pens it may have opened
 * or closed right away, instead of waiting for the periodic scan.
 */
public final class PenWatcher {
	private static final Map<World, Set<ScarecrowBlockEntity>> LOADED = new ConcurrentHashMap<>();

	private PenWatcher() {
	}

	public static void register() {
		ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, world) -> {
			if (blockEntity instanceof ScarecrowBlockEntity scarecrow) {
				LOADED.computeIfAbsent(world, key -> ConcurrentHashMap.newKeySet()).add(scarecrow);
			}
		});

		ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((blockEntity, world) -> {
			if (blockEntity instanceof ScarecrowBlockEntity scarecrow) {
				Set<ScarecrowBlockEntity> loaded = LOADED.get(world);

				if (loaded != null) {
					loaded.remove(scarecrow);
				}
			}
		});

		ServerWorldEvents.UNLOAD.register((server, world) -> LOADED.remove(world));

		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> onBlockChanged(world, pos));

		// Fires before the block is actually placed, so the queued scan runs on the following tick.
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			onBlockChanged(world, hit.getBlockPos());
			return ActionResult.PASS;
		});
	}

	public static void onBlockChanged(World world, BlockPos changed) {
		if (world.isClient) {
			return;
		}

		Set<ScarecrowBlockEntity> loaded = LOADED.get(world);

		if (loaded == null) {
			return;
		}

		int reach = ModConfig.get().scanMaxRadius + 1;

		for (ScarecrowBlockEntity scarecrow : loaded) {
			BlockPos pos = scarecrow.getPos();

			if (Math.abs(changed.getX() - pos.getX()) <= reach
					&& Math.abs(changed.getZ() - pos.getZ()) <= reach
					&& Math.abs(changed.getY() - pos.getY()) <= 2) {
				scarecrow.requestRescan();
			}
		}
	}
}
