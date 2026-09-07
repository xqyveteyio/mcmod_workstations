package dev.keyboard.breederscarecrow.network;

import dev.keyboard.breederscarecrow.BreederScarecrowMod;
import dev.keyboard.breederscarecrow.block.ScarecrowBlockEntity;
import dev.keyboard.breederscarecrow.work.StationSettings;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Talk between a station and the settings screen.
 *
 * <p>The screen is opened from the server rather than straight from the block's use handler, which
 * keeps every client only class out of {@link dev.keyboard.breederscarecrow.block.ScarecrowBlock}.
 * A dedicated server loading a screen class would crash on the spot.
 *
 * <p>Settings themselves travel as NBT, the same shape they are saved in. That way a new setting is
 * one entry in {@link StationSettings} and needs nothing here.
 */
public final class StationNetworking {
	/** Server to client: put the settings screen up for a station. */
	public static final Identifier OPEN_SCREEN = BreederScarecrowMod.id("open_screen");
	/** Client to server: store what the screen was left showing. */
	public static final Identifier SAVE_SETTINGS = BreederScarecrowMod.id("save_settings");
	/** Client to server: call the worker home, hiring a replacement if there is none. */
	public static final Identifier RECALL_WORKER = BreederScarecrowMod.id("recall_worker");

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
				ScarecrowBlockEntity station = reachableStation(player, pos);

				if (station != null && nbt != null) {
					StationSettings incoming = new StationSettings();
					incoming.readNbt(nbt);
					station.applySettings(incoming);
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(RECALL_WORKER, (server, player, handler, buf, sender) -> {
			BlockPos pos = buf.readBlockPos();

			server.execute(() -> {
				ScarecrowBlockEntity station = reachableStation(player, pos);

				if (station == null || !(player.getWorld() instanceof ServerWorld world)) {
					return;
				}

				// Over the hotbar rather than in the screen, so the answer survives closing it.
				player.sendMessage(Text.translatable(switch (station.recallWorker(world)) {
					case SUMMONED -> "message.breeder_scarecrow.worker_summoned";
					case MOVED -> "message.breeder_scarecrow.worker_recalled";
					case NO_ROOM -> "message.breeder_scarecrow.worker_no_room";
				}), true);
			});
		});
	}

	/** Asks the client to put the settings screen up for this station. */
	public static void openScreen(ServerPlayerEntity player, BlockPos pos) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBlockPos(pos);
		ServerPlayNetworking.send(player, OPEN_SCREEN, buf);
	}

	/**
	 * The station a packet names, so long as the player is stood next to it. Anyone may set a ranch
	 * up, but only one they could actually be looking at.
	 */
	@Nullable
	private static ScarecrowBlockEntity reachableStation(ServerPlayerEntity player, BlockPos pos) {
		if (player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > EDIT_RANGE_SQUARED) {
			return null;
		}

		return player.getWorld().getBlockEntity(pos) instanceof ScarecrowBlockEntity station ? station : null;
	}
}
