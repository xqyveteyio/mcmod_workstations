package dev.keyboard.workstations.network;

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
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Talk between a station and the settings screen, for every kind of station.
 *
 * <p>The screen is opened from the server rather than straight from the block's use handler, which
 * keeps every client only class out of the block classes. A dedicated server loading a screen class
 * would crash on the spot.
 *
 * <p>Settings themselves travel as NBT, the same shape they are saved in. That way a new setting is
 * one entry in a station's option list and needs nothing here.
 *
 * <p>Payload types for both directions are registered here so a dedicated server knows the packets
 * it is about to send, not only the ones it is prepared to receive.
 */
public final class StationNetworking {
	/**
	 * How far from a station a player may still be editing it, squared. Generous next to the reach
	 * the block was clicked with, since the screen stays open while you are pushed about.
	 */
	private static final double EDIT_RANGE_SQUARED = 144.0;

	private StationNetworking() {
	}

	/** Server to client: put the settings screen up for a station. */
	public record OpenScreenPayload(BlockPos pos, List<Identifier> palette) implements CustomPayload {
		public static final Id<OpenScreenPayload> ID = new Id<>(WorkstationsMod.id("open_screen"));
		public static final PacketCodec<RegistryByteBuf, OpenScreenPayload> CODEC = PacketCodec.tuple(
				BlockPos.PACKET_CODEC, OpenScreenPayload::pos,
				Identifier.PACKET_CODEC.collect(PacketCodecs.toList()), OpenScreenPayload::palette,
				OpenScreenPayload::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Client to server: store what the screen was left showing. */
	public record SaveSettingsPayload(BlockPos pos, NbtCompound nbt) implements CustomPayload {
		public static final Id<SaveSettingsPayload> ID = new Id<>(WorkstationsMod.id("save_settings"));
		public static final PacketCodec<RegistryByteBuf, SaveSettingsPayload> CODEC = PacketCodec.tuple(
				BlockPos.PACKET_CODEC, SaveSettingsPayload::pos,
				PacketCodecs.UNLIMITED_NBT_COMPOUND, SaveSettingsPayload::nbt,
				SaveSettingsPayload::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Client to server: call the worker home, hiring a replacement if there is none. */
	public record RecallWorkerPayload(BlockPos pos) implements CustomPayload {
		public static final Id<RecallWorkerPayload> ID = new Id<>(WorkstationsMod.id("recall_worker"));
		public static final PacketCodec<RegistryByteBuf, RecallWorkerPayload> CODEC = PacketCodec.tuple(
				BlockPos.PACKET_CODEC, RecallWorkerPayload::pos, RecallWorkerPayload::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Client to server: take the field survey again, for a farm built after the block was placed. */
	public record RescanPlotsPayload(BlockPos pos) implements CustomPayload {
		public static final Id<RescanPlotsPayload> ID = new Id<>(WorkstationsMod.id("rescan_plots"));
		public static final PacketCodec<RegistryByteBuf, RescanPlotsPayload> CODEC = PacketCodec.tuple(
				BlockPos.PACKET_CODEC, RescanPlotsPayload::pos, RescanPlotsPayload::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public static void registerServerReceivers() {
		PayloadTypeRegistry.playS2C().register(OpenScreenPayload.ID, OpenScreenPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SaveSettingsPayload.ID, SaveSettingsPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(RecallWorkerPayload.ID, RecallWorkerPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(RescanPlotsPayload.ID, RescanPlotsPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(SaveSettingsPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			WorkStationBlockEntity<?, ?> station = reachableStation(player, payload.pos());
			NbtCompound nbt = payload.nbt();

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

		ServerPlayNetworking.registerGlobalReceiver(RecallWorkerPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			WorkStationBlockEntity<?, ?> station = reachableStation(player, payload.pos());

			if (station == null || !(player.getWorld() instanceof ServerWorld world)) {
				return;
			}

			// Over the hotbar rather than in the screen, so the answer survives closing it.
			player.sendMessage(Text.translatable(switch (station.recallWorker(world)) {
				case SUMMONED -> "message.keyboard_workstations.worker_summoned";
				case MOVED -> "message.keyboard_workstations.worker_recalled";
				case NO_ROOM -> "message.keyboard_workstations.worker_no_room";
			}), true);
		});

		ServerPlayNetworking.registerGlobalReceiver(RescanPlotsPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();

			if (!(reachableStation(player, payload.pos()) instanceof FarmBlockEntity farm)
					|| !(player.getWorld() instanceof ServerWorld world)) {
				return;
			}

			player.sendMessage(Text.translatable("message.keyboard_workstations.plots_registered",
					farm.registerPlots(world)), true);
		});
	}

	/**
	 * Asks the client to put the settings screen up for this station.
	 *
	 * <p>A farm's seed list rides along, because a station does not send its contents to the client
	 * and the ratio rows are built from exactly those contents. Ranches send an empty list.
	 */
	public static void openScreen(ServerPlayerEntity player, BlockPos pos) {
		List<Identifier> palette = paletteAt(player.getWorld().getBlockEntity(pos)).stream()
				.map(Registries.ITEM::getId)
				.toList();
		ServerPlayNetworking.send(player, new OpenScreenPayload(pos, palette));
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

		return player.getWorld().getBlockEntity(pos) instanceof WorkStationBlockEntity<?, ?> station
				? station
				: null;
	}
}
