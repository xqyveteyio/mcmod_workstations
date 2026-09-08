package dev.keyboard.workstations.network;

import dev.keyboard.workstations.WorkstationsMod;
import dev.keyboard.workstations.block.FarmBlockEntity;
import dev.keyboard.workstations.block.RanchBlockEntity;
import dev.keyboard.workstations.block.WorkStationBlockEntity;
import dev.keyboard.workstations.work.Crops;
import dev.keyboard.workstations.work.FarmSettings;
import dev.keyboard.workstations.work.StationSettings;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Talk between a station and the settings screen, for both kinds of station.
 *
 * <p>The screen is opened from the server rather than straight from the block's use handler, which
 * keeps every client only class out of the block classes. A dedicated server loading a screen class
 * would crash on the spot.
 *
 * <p>Settings themselves travel as NBT, the same shape they are saved in. That way a new setting is
 * one entry in a station's option list and needs nothing here.
 */
public final class StationNetworking {
	/** Server to client: put the settings screen up for a station. */
	public static final Identifier OPEN_SCREEN = WorkstationsMod.id("open_screen");
	/** Client to server: store what the screen was left showing. */
	public static final Identifier SAVE_SETTINGS = WorkstationsMod.id("save_settings");
	/** Client to server: call the worker home, hiring a replacement if there is none. */
	public static final Identifier RECALL_WORKER = WorkstationsMod.id("recall_worker");
	/** Client to server: take the field survey again, for a farm built after the block was placed. */
	public static final Identifier RESCAN_PLOTS = WorkstationsMod.id("rescan_plots");

	/**
	 * How far from a station a player may still be editing it, squared. Generous next to the reach
	 * the block was clicked with, since the screen stays open while you are pushed about.
	 */
	private static final double EDIT_RANGE_SQUARED = 144.0;

	private StationNetworking() {
	}

	public static void registerServerReceivers() {
		ServerPlayNetworking.registerGlobalReceiver(SAVE_SETTINGS, (server, player, handler, buf, sender) -> {
			BlockPos pos = buf.readBlockPos();
			NbtCompound nbt = buf.readNbt();

			// Off the network thread: block entities are only safe to touch on the server thread.
			server.execute(() -> {
				WorkStationBlockEntity<?, ?> station = reachableStation(player, pos);

				if (nbt == null) {
					return;
				}

				// Which kind of settings the packet holds is decided by the block it names rather
				// than by anything in the packet, so a mismatched pair cannot be applied at all.
				if (station instanceof RanchBlockEntity ranch) {
					StationSettings incoming = new StationSettings();
					incoming.readNbt(nbt);
					ranch.applySettings(incoming);
				} else if (station instanceof FarmBlockEntity farm) {
					FarmSettings incoming = new FarmSettings();
					incoming.readNbt(nbt);
					farm.applySettings(incoming);
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(RECALL_WORKER, (server, player, handler, buf, sender) -> {
			BlockPos pos = buf.readBlockPos();

			server.execute(() -> {
				WorkStationBlockEntity<?, ?> station = reachableStation(player, pos);

				if (station == null || !(player.getWorld() instanceof ServerWorld world)) {
					return;
				}

				// Over the hotbar rather than in the screen, so the answer survives closing it.
				player.sendMessage(Text.translatable(switch (station.recallWorker(world)) {
					case SUMMONED -> "message.workstations.worker_summoned";
					case MOVED -> "message.workstations.worker_recalled";
					case NO_ROOM -> "message.workstations.worker_no_room";
				}), true);
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(RESCAN_PLOTS, (server, player, handler, buf, sender) -> {
			BlockPos pos = buf.readBlockPos();

			server.execute(() -> {
				if (!(reachableStation(player, pos) instanceof FarmBlockEntity farm)
						|| !(player.getWorld() instanceof ServerWorld world)) {
					return;
				}

				player.sendMessage(Text.translatable("message.workstations.plots_registered",
						farm.registerPlots(world)), true);
			});
		});
	}

	/**
	 * Asks the client to put the settings screen up for this station.
	 *
	 * <p>A farm's seed list rides along, because a station does not send its contents to the client
	 * and the ratio rows are built from exactly those contents. Ranches send an empty list.
	 */
	public static void openScreen(ServerPlayerEntity player, BlockPos pos) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBlockPos(pos);
		List<Item> palette = player.getWorld().getBlockEntity(pos) instanceof FarmBlockEntity farm
				? Crops.palette(farm.seedStores())
				: List.of();
		buf.writeVarInt(palette.size());

		for (Item seed : palette) {
			buf.writeIdentifier(Registries.ITEM.getId(seed));
		}

		ServerPlayNetworking.send(player, OPEN_SCREEN, buf);
	}

	/**
	 * The station a packet names, so long as the player is stood next to it. Anyone may set a
	 * station up, but only one they could actually be looking at.
	 */
	@Nullable
	private static WorkStationBlockEntity<?, ?> reachableStation(ServerPlayerEntity player, BlockPos pos) {
		if (player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > EDIT_RANGE_SQUARED) {
			return null;
		}

		return player.getWorld().getBlockEntity(pos) instanceof WorkStationBlockEntity<?, ?> station
				? station
				: null;
	}
}
