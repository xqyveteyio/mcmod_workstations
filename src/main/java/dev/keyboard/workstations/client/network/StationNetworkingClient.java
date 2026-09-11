package dev.keyboard.workstations.client.network;

import dev.keyboard.workstations.block.FarmBlockEntity;
import dev.keyboard.workstations.block.LumberBlockEntity;
import dev.keyboard.workstations.block.RanchBlockEntity;
import dev.keyboard.workstations.client.screen.FarmSettingsScreen;
import dev.keyboard.workstations.client.screen.LumberSettingsScreen;
import dev.keyboard.workstations.client.screen.StationSettingsScreen;
import dev.keyboard.workstations.network.StationNetworking.OpenScreenPayload;
import dev.keyboard.workstations.network.StationNetworking.RecallWorkerPayload;
import dev.keyboard.workstations.network.StationNetworking.RescanPlotsPayload;
import dev.keyboard.workstations.network.StationNetworking.SaveSettingsPayload;
import dev.keyboard.workstations.work.FarmSettings;
import dev.keyboard.workstations.work.LumberSettings;
import dev.keyboard.workstations.work.StationSettings;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import java.util.ArrayList;
import java.util.List;

/** The client half of the settings screen's traffic. */
public final class StationNetworkingClient {
	private StationNetworkingClient() {
	}

	public static void registerClientReceivers() {
		ClientPlayNetworking.registerGlobalReceiver(OpenScreenPayload.ID, (payload, context) ->
				openScreen(context.client(), payload.pos(), payload.palette()));
	}

	/**
	 * Which screen to put up is decided by the block, not by the packet: the client has the block
	 * entity already, and reading the settings off it means the screen opens on the real values
	 * without a round trip.
	 */
	private static void openScreen(Minecraft client, BlockPos pos, List<Identifier> palette) {
		Level world = client.level;

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
			if (BuiltInRegistries.ITEM.containsKey(id)) {
				seeds.add(BuiltInRegistries.ITEM.getValue(id));
			}
		}

		return seeds;
	}

	public static void saveRanchSettings(BlockPos pos, StationSettings settings) {
		CompoundTag nbt = new CompoundTag();
		settings.writeNbt(nbt);
		ClientPlayNetworking.send(new SaveSettingsPayload(pos, nbt));
	}

	public static void saveFarmSettings(BlockPos pos, FarmSettings settings) {
		CompoundTag nbt = new CompoundTag();
		settings.writeNbt(nbt);
		ClientPlayNetworking.send(new SaveSettingsPayload(pos, nbt));
	}

	public static void saveLumberSettings(BlockPos pos, LumberSettings settings) {
		CompoundTag nbt = new CompoundTag();
		settings.writeNbt(nbt);
		ClientPlayNetworking.send(new SaveSettingsPayload(pos, nbt));
	}

	public static void recallWorker(BlockPos pos) {
		ClientPlayNetworking.send(new RecallWorkerPayload(pos));
	}

	public static void rescanPlots(BlockPos pos) {
		ClientPlayNetworking.send(new RescanPlotsPayload(pos));
	}
}
