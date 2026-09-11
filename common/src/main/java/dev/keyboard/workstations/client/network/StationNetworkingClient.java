package dev.keyboard.workstations.client.network;

import dev.keyboard.workstations.block.FarmBlockEntity;
import dev.keyboard.workstations.block.LumberBlockEntity;
import dev.keyboard.workstations.block.RanchBlockEntity;
import dev.keyboard.workstations.client.screen.FarmSettingsScreen;
import dev.keyboard.workstations.client.screen.LumberSettingsScreen;
import dev.keyboard.workstations.client.screen.StationSettingsScreen;
import dev.keyboard.workstations.network.StationNetworking;
import dev.keyboard.workstations.work.FarmSettings;
import dev.keyboard.workstations.work.LumberSettings;
import dev.keyboard.workstations.work.StationSettings;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/** The client half of the settings screen's traffic. */
public final class StationNetworkingClient {
	private StationNetworkingClient() {
	}

	public static void registerClientReceivers() {
		NetworkManager.registerReceiver(NetworkManager.Side.S2C, StationNetworking.OPEN_SCREEN, (buf, context) -> {
			BlockPos pos = buf.readBlockPos();
			int seeds = buf.readVarInt();
			List<Identifier> palette = new ArrayList<>(seeds);

			for (int index = 0; index < seeds; index++) {
				palette.add(buf.readIdentifier());
			}

			context.queue(() -> openScreen(MinecraftClient.getInstance(), pos, palette));
		});
	}

	/**
	 * Which screen to put up is decided by the block, not by the packet: the client has the block
	 * entity already, and reading the settings off it means the screen opens on the real values
	 * without a round trip.
	 */
	private static void openScreen(MinecraftClient client, BlockPos pos, List<Identifier> palette) {
		World world = client.world;

		if (world == null) {
			return;
		}

		if (world.getBlockEntity(pos) instanceof FarmBlockEntity farm) {
			client.setScreen(new FarmSettingsScreen(pos, farm.getSettings().copy(), items(palette)));
		} else if (world.getBlockEntity(pos) instanceof LumberBlockEntity lumber) {
			client.setScreen(new LumberSettingsScreen(pos, lumber.getSettings().copy(), items(palette)));
		} else if (world.getBlockEntity(pos) instanceof RanchBlockEntity ranch) {
			client.setScreen(new StationSettingsScreen(pos, ranch.getSettings().copy()));
		}
	}

	/** Seeds from a mod the client does not have are dropped rather than showing a broken row. */
	private static List<Item> items(List<Identifier> ids) {
		List<Item> seeds = new ArrayList<>(ids.size());

		for (Identifier id : ids) {
			if (Registries.ITEM.containsId(id)) {
				seeds.add(Registries.ITEM.get(id));
			}
		}

		return seeds;
	}

	public static void saveRanchSettings(BlockPos pos, StationSettings settings) {
		NbtCompound nbt = new NbtCompound();
		settings.writeNbt(nbt);
		sendSettings(pos, nbt);
	}

	public static void saveFarmSettings(BlockPos pos, FarmSettings settings) {
		NbtCompound nbt = new NbtCompound();
		settings.writeNbt(nbt);
		sendSettings(pos, nbt);
	}

	public static void saveLumberSettings(BlockPos pos, LumberSettings settings) {
		NbtCompound nbt = new NbtCompound();
		settings.writeNbt(nbt);
		sendSettings(pos, nbt);
	}

	private static void sendSettings(BlockPos pos, NbtCompound nbt) {
		PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
		buf.writeBlockPos(pos);
		buf.writeNbt(nbt);
		NetworkManager.sendToServer(StationNetworking.SAVE_SETTINGS, buf);
	}

	public static void recallWorker(BlockPos pos) {
		NetworkManager.sendToServer(StationNetworking.RECALL_WORKER, justPos(pos));
	}

	public static void rescanPlots(BlockPos pos) {
		NetworkManager.sendToServer(StationNetworking.RESCAN_PLOTS, justPos(pos));
	}

	private static PacketByteBuf justPos(BlockPos pos) {
		PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
		buf.writeBlockPos(pos);
		return buf;
	}
}
