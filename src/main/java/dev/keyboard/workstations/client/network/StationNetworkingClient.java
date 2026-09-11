package dev.keyboard.workstations.client.network;

import dev.keyboard.workstations.Mc;

import dev.keyboard.workstations.block.FarmBlockEntity;
import dev.keyboard.workstations.block.LumberBlockEntity;
import dev.keyboard.workstations.block.RanchBlockEntity;
import dev.keyboard.workstations.client.screen.FarmSettingsScreen;
import dev.keyboard.workstations.client.screen.LumberSettingsScreen;
import dev.keyboard.workstations.client.screen.StationSettingsScreen;
import dev.keyboard.workstations.work.FarmSettings;
import dev.keyboard.workstations.work.LumberSettings;
import dev.keyboard.workstations.work.StationSettings;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

//? if >=1.20.5 {
/* import dev.keyboard.workstations.network.StationNetworking.OpenScreenPayload;
import dev.keyboard.workstations.network.StationNetworking.RecallWorkerPayload;
import dev.keyboard.workstations.network.StationNetworking.RescanPlotsPayload;
import dev.keyboard.workstations.network.StationNetworking.SaveSettingsPayload;
*/
//?} else {
import dev.keyboard.workstations.network.StationNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
//?}

/** The client half of the settings screen's traffic. */
public final class StationNetworkingClient {
	private StationNetworkingClient() {
	}

	public static void registerClientReceivers() {
		//? if >=1.20.5 {
		/* ClientPlayNetworking.registerGlobalReceiver(OpenScreenPayload.ID, (payload, context) ->
				openScreen(context.client(), payload.pos(), payload.palette())); */
		//?} else {
		ClientPlayNetworking.registerGlobalReceiver(StationNetworking.OPEN_SCREEN,
				(client, handler, buf, sender) -> {
					BlockPos pos = buf.readBlockPos();
					int seeds = buf.readVarInt();
					List<Identifier> palette = new ArrayList<>(seeds);

					for (int index = 0; index < seeds; index++) {
						palette.add(buf.readIdentifier());
					}

					client.execute(() -> openScreen(client, pos, palette));
				});
		//?}
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
			//? if >=1.19 {
			client.setScreen(new FarmSettingsScreen(pos, farm.getSettings().copy(), items(palette)));
			//?} else {
			/* client.openScreen(new FarmSettingsScreen(pos, farm.getSettings().copy(), items(palette))); */
			//?}
		} else if (world.getBlockEntity(pos) instanceof LumberBlockEntity lumber) {
			//? if >=1.19 {
			client.setScreen(new LumberSettingsScreen(pos, lumber.getSettings().copy(), items(palette)));
			//?} else {
			/* client.openScreen(new LumberSettingsScreen(pos, lumber.getSettings().copy(), items(palette))); */
			//?}
		} else if (world.getBlockEntity(pos) instanceof RanchBlockEntity ranch) {
			//? if >=1.19 {
			client.setScreen(new StationSettingsScreen(pos, ranch.getSettings().copy()));
			//?} else {
			/* client.openScreen(new StationSettingsScreen(pos, ranch.getSettings().copy())); */
			//?}
		}
	}

	/** Seeds from a mod the client does not have are dropped rather than showing a broken row. */
	private static List<Item> items(List<Identifier> ids) {
		List<Item> seeds = new ArrayList<>(ids.size());

		for (Identifier id : ids) {
			if (Mc.hasItem(id)) {
				seeds.add(Mc.item(id));
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
		//? if >=1.20.5 {
		/* ClientPlayNetworking.send(new SaveSettingsPayload(pos, nbt)); */
		//?} else {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBlockPos(pos);
		buf.writeNbt(nbt);
		ClientPlayNetworking.send(StationNetworking.SAVE_SETTINGS, buf);
		//?}
	}

	public static void recallWorker(BlockPos pos) {
		//? if >=1.20.5 {
		/* ClientPlayNetworking.send(new RecallWorkerPayload(pos)); */
		//?} else {
		ClientPlayNetworking.send(StationNetworking.RECALL_WORKER, justPos(pos));
		//?}
	}

	public static void rescanPlots(BlockPos pos) {
		//? if >=1.20.5 {
		/* ClientPlayNetworking.send(new RescanPlotsPayload(pos)); */
		//?} else {
		ClientPlayNetworking.send(StationNetworking.RESCAN_PLOTS, justPos(pos));
		//?}
	}

	//? if <1.20.5 {
	private static PacketByteBuf justPos(BlockPos pos) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBlockPos(pos);
		return buf;
	}
	//?}
}
