package dev.keyboard.breederscarecrow.client.network;

import dev.keyboard.breederscarecrow.block.ScarecrowBlockEntity;
import dev.keyboard.breederscarecrow.client.screen.StationSettingsScreen;
import dev.keyboard.breederscarecrow.network.StationNetworking;
import dev.keyboard.breederscarecrow.work.StationSettings;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/** The client half of the settings screen's traffic. */
public final class StationNetworkingClient {
	private StationNetworkingClient() {
	}

	public static void registerClientReceivers() {
		ClientPlayNetworking.registerGlobalReceiver(StationNetworking.OPEN_SCREEN,
				(client, handler, buf, sender) -> {
					BlockPos pos = buf.readBlockPos();
					client.execute(() -> client.setScreen(new StationSettingsScreen(pos, settingsAt(client, pos))));
				});
	}

	public static void saveSettings(BlockPos pos, StationSettings settings) {
		NbtCompound nbt = new NbtCompound();
		settings.writeNbt(nbt);

		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBlockPos(pos);
		buf.writeNbt(nbt);
		ClientPlayNetworking.send(StationNetworking.SAVE_SETTINGS, buf);
	}

	public static void recallWorker(BlockPos pos) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBlockPos(pos);
		ClientPlayNetworking.send(StationNetworking.RECALL_WORKER, buf);
	}

	/**
	 * The station's current settings as the client knows them. Stations send these along with the
	 * rest of their block entity data, so the screen opens on the real values without a round trip.
	 */
	private static StationSettings settingsAt(MinecraftClient client, BlockPos pos) {
		if (client.world != null && client.world.getBlockEntity(pos) instanceof ScarecrowBlockEntity station) {
			return station.getSettings().copy();
		}

		return new StationSettings();
	}
}
