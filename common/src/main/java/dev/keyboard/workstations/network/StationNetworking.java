package dev.keyboard.workstations.network;

import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntity;
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
 * <p>The open-screen packet is registered as a type on a dedicated server here, so that process
 * can send it. The client registers the same type when it attaches the receiver; doing both in
 * one Fabric client process would throw that the id is already registered.
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
	public record OpenScreenPayload(BlockPos pos, List<Identifier> palette) implements CustomPacketPayload {
		public static final Type<OpenScreenPayload> ID = new Type<>(WorkstationsMod.id("open_screen"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenScreenPayload> CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, OpenScreenPayload::pos,
				Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()), OpenScreenPayload::palette,
				OpenScreenPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return ID;
		}
	}

	/** Client to server: store what the screen was left showing. */
	public record SaveSettingsPayload(BlockPos pos, CompoundTag nbt) implements CustomPacketPayload {
		public static final Type<SaveSettingsPayload> ID = new Type<>(WorkstationsMod.id("save_settings"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SaveSettingsPayload> CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, SaveSettingsPayload::pos,
				ByteBufCodecs.TRUSTED_COMPOUND_TAG, SaveSettingsPayload::nbt,
				SaveSettingsPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return ID;
		}
	}

	/** Client to server: call the worker home, hiring a replacement if there is none. */
	public record RecallWorkerPayload(BlockPos pos) implements CustomPacketPayload {
		public static final Type<RecallWorkerPayload> ID = new Type<>(WorkstationsMod.id("recall_worker"));
		public static final StreamCodec<RegistryFriendlyByteBuf, RecallWorkerPayload> CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, RecallWorkerPayload::pos, RecallWorkerPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return ID;
		}
	}

	/** Client to server: take the field survey again, for a farm built after the block was placed. */
	public record RescanPlotsPayload(BlockPos pos) implements CustomPacketPayload {
		public static final Type<RescanPlotsPayload> ID = new Type<>(WorkstationsMod.id("rescan_plots"));
		public static final StreamCodec<RegistryFriendlyByteBuf, RescanPlotsPayload> CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, RescanPlotsPayload::pos, RescanPlotsPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return ID;
		}
	}

	public static void registerServerReceivers() {
		if (Platform.getEnvironment() == Env.SERVER) {
			NetworkManager.registerS2CPayloadType(OpenScreenPayload.ID, OpenScreenPayload.CODEC);
		}

		NetworkManager.registerReceiver(NetworkManager.c2s(), SaveSettingsPayload.ID, SaveSettingsPayload.CODEC,
				(payload, context) -> context.queue(() -> {
					if (!(context.getPlayer() instanceof ServerPlayer player)) {
						return;
					}

					WorkStationBlockEntity<?, ?> station = reachableStation(player, payload.pos());
					CompoundTag nbt = payload.nbt();

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
				}));

		NetworkManager.registerReceiver(NetworkManager.c2s(), RecallWorkerPayload.ID, RecallWorkerPayload.CODEC,
				(payload, context) -> context.queue(() -> {
					if (!(context.getPlayer() instanceof ServerPlayer player)) {
						return;
					}

					WorkStationBlockEntity<?, ?> station = reachableStation(player, payload.pos());

					if (station == null || !(player.level() instanceof ServerLevel world)) {
						return;
					}

					// Over the hotbar rather than in the screen, so the answer survives closing it.
					player.sendOverlayMessage(Component.translatable(switch (station.recallWorker(world)) {
						case SUMMONED -> "message.villager_workstations.worker_summoned";
						case MOVED -> "message.villager_workstations.worker_recalled";
						case NO_ROOM -> "message.villager_workstations.worker_no_room";
					}));
				}));

		NetworkManager.registerReceiver(NetworkManager.c2s(), RescanPlotsPayload.ID, RescanPlotsPayload.CODEC,
				(payload, context) -> context.queue(() -> {
					if (!(context.getPlayer() instanceof ServerPlayer player)
							|| !(reachableStation(player, payload.pos()) instanceof FarmBlockEntity farm)
							|| !(player.level() instanceof ServerLevel world)) {
						return;
					}

					player.sendOverlayMessage(Component.translatable("message.villager_workstations.plots_registered",
							farm.registerPlots(world)));
				}));
	}

	/**
	 * Asks the client to put the settings screen up for this station.
	 *
	 * <p>A farm's seed list rides along, because a station does not send its contents to the client
	 * and the ratio rows are built from exactly those contents. Ranches send an empty list.
	 */
	public static void openScreen(ServerPlayer player, BlockPos pos) {
		List<Identifier> palette = paletteAt(player.level().getBlockEntity(pos)).stream()
				.map(BuiltInRegistries.ITEM::getKey)
				.toList();
		NetworkManager.sendToPlayer(player, new OpenScreenPayload(pos, palette));
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
	private static WorkStationBlockEntity<?, ?> reachableStation(ServerPlayer player, BlockPos pos) {
		if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > EDIT_RANGE_SQUARED) {
			return null;
		}

		return player.level().getBlockEntity(pos) instanceof WorkStationBlockEntity<?, ?> station
				? station
				: null;
	}
}
