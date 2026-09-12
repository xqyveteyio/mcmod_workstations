package dev.keyboard.workstations.network;

import me.shedaniel.architectury.networking.NetworkManager;
import dev.keyboard.workstations.WorkstationsMod;
import dev.keyboard.workstations.block.FarmBlockEntity;
import dev.keyboard.workstations.block.LumberBlockEntity;
import dev.keyboard.workstations.block.RanchBlockEntity;
import dev.keyboard.workstations.block.WorkStationBlockEntity;
import dev.keyboard.workstations.work.Crops;
import dev.keyboard.workstations.work.FarmSettings;
import dev.keyboard.workstations.work.LumberSettings;
import dev.keyboard.workstations.work.StationSettings;
import dev.keyboard.workstations.work.Woods;
import io.netty.buffer.Unpooled;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
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
		NetworkManager.registerReceiver(NetworkManager.Side.C2S, SAVE_SETTINGS, (buf, context) -> {
			BlockPos pos = buf.readBlockPos();
			NbtCompound nbt = buf.readNbt();

			// Off the network thread: block entities are only safe to touch on the server thread.
			context.queue(() -> {
				if (!(context.getPlayer() instanceof ServerPlayerEntity player)) {
					return;
				}

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
				} else if (station instanceof LumberBlockEntity lumber) {
					LumberSettings incoming = new LumberSettings();
					incoming.readNbt(nbt);
					lumber.applySettings(incoming);
				}
			});
		});

		NetworkManager.registerReceiver(NetworkManager.Side.C2S, RECALL_WORKER, (buf, context) -> {
			BlockPos pos = buf.readBlockPos();

			context.queue(() -> {
				if (!(context.getPlayer() instanceof ServerPlayerEntity player)) {
					return;
				}

				WorkStationBlockEntity<?, ?> station = reachableStation(player, pos);

				if (station == null || !(player.getEntityWorld() instanceof ServerWorld world)) {
					return;
				}

				// Over the hotbar rather than in the screen, so the answer survives closing it.
				player.sendMessage(new TranslatableText(switch (station.recallWorker(world)) {
					case SUMMONED -> "message.villager_workstations.worker_summoned";
					case MOVED -> "message.villager_workstations.worker_recalled";
					case NO_ROOM -> "message.villager_workstations.worker_no_room";
				}), true);
			});
		});

		NetworkManager.registerReceiver(NetworkManager.Side.C2S, RESCAN_PLOTS, (buf, context) -> {
			BlockPos pos = buf.readBlockPos();

			context.queue(() -> {
				if (!(context.getPlayer() instanceof ServerPlayerEntity player)
						|| !(reachableStation(player, pos) instanceof FarmBlockEntity farm)
						|| !(player.getEntityWorld() instanceof ServerWorld world)) {
					return;
				}

				player.sendMessage(new TranslatableText("message.villager_workstations.plots_registered",
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
		PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
		buf.writeBlockPos(pos);
		List<Item> palette = paletteAt(player.getEntityWorld().getBlockEntity(pos));
		buf.writeVarInt(palette.size());

		for (Item seed : palette) {
			buf.writeIdentifier(Registry.ITEM.getId(seed));
		}

		NetworkManager.sendToPlayer(player, OPEN_SCREEN, buf);
	}

	/**
	 * The mix rows a planting station's screen is built from. A farm and a lumber station both
	 * send their palette this way because neither sends its contents to the client; ranches send
	 * an empty list.
	 */
	private static List<Item> paletteAt(@Nullable BlockEntity block) {
		if (block instanceof FarmBlockEntity farm) {
			return Crops.palette(farm.seedStores());
		}

		if (block instanceof LumberBlockEntity lumber) {
			return Woods.palette(lumber.seedStores());
		}

		return List.of();
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

		return player.getEntityWorld().getBlockEntity(pos) instanceof WorkStationBlockEntity<?, ?> station
				? station
				: null;
	}
}
