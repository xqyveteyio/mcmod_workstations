package dev.keyboard.breederscarecrow.block;

import dev.keyboard.breederscarecrow.BreederScarecrowMod;
import dev.keyboard.breederscarecrow.entity.RancherEntity;
import dev.keyboard.breederscarecrow.work.StationSettings;
import dev.keyboard.breederscarecrow.work.WorkArea;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The station: storage for feed going in and produce coming out, plus the owner of one rancher.
 * It does no ranching itself, it only keeps a worker alive and hands out the work area.
 */
public class ScarecrowBlockEntity extends LootableContainerBlockEntity {
	public static final int INVENTORY_SIZE = 27;

	private static final String WORKER_KEY = "Worker";
	private static final String SETTINGS_KEY = "Settings";
	/** Where the area size lived before stations had settings of their own. */
	private static final String LEGACY_RADIUS_KEY = "Radius";
	private static final String LEGACY_HEIGHT_KEY = "Height";

	private DefaultedList<ItemStack> inventory = DefaultedList.ofSize(INVENTORY_SIZE, ItemStack.EMPTY);
	@Nullable
	private UUID workerUuid;
	private int respawnTimer;
	/**
	 * This station's orders, started off from the config file's values and edited in game from the
	 * settings screen. Sent to the client in full: it needs the area size to draw the highlight,
	 * and the screen reads the rest straight off the block rather than asking the server for it.
	 */
	private final StationSettings settings = new StationSettings();

	public ScarecrowBlockEntity(BlockPos pos, BlockState state) {
		super(BreederScarecrowMod.SCARECROW_BLOCK_ENTITY, pos, state);
	}

	public static void serverTick(World world, BlockPos pos, BlockState state, ScarecrowBlockEntity station) {
		if (!(world instanceof ServerWorld serverWorld)) {
			return;
		}

		RancherEntity worker = station.getWorker(serverWorld);

		if (worker != null) {
			// Refreshed every tick so a worker restored from disk finds its way home again.
			worker.setStation(pos);
			station.respawnTimer = station.settings.workerRespawnTicks;
			return;
		}

		if (--station.respawnTimer <= 0) {
			station.respawnTimer = station.settings.workerRespawnTicks;
			station.summonWorker(serverWorld);
		}
	}

	public WorkArea getWorkArea() {
		return new WorkArea(pos, settings.workRadius, settings.workHeight);
	}

	public StationSettings getSettings() {
		return settings;
	}

	/**
	 * Takes on a set of settings from the screen. Listeners are told even though only the area size
	 * is drawn, because that size is what the highlight is built from and a resize that never
	 * reached the client would leave the box lying about where the rancher works.
	 */
	public void applySettings(StationSettings incoming) {
		settings.copyFrom(incoming);
		markDirty();

		if (world != null) {
			world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
		}
	}

	/** The rancher belonging to this station, or {@code null} if it died or is not loaded. */
	@Nullable
	public RancherEntity getWorker(ServerWorld world) {
		if (workerUuid != null) {
			Entity entity = world.getEntity(workerUuid);

			if (entity instanceof RancherEntity rancher && rancher.isAlive()) {
				return rancher;
			}
		}

		// The saved id goes stale whenever the worker's chunk unloads without the station's, so
		// before summoning a replacement, look for one already standing in the area.
		List<RancherEntity> strays = world.getEntitiesByClass(RancherEntity.class, getWorkArea().getBox().expand(4.0),
				rancher -> rancher.isAlive() && pos.equals(rancher.getStationPos()));

		if (!strays.isEmpty()) {
			adopt(strays.get(0));
			return strays.get(0);
		}

		if (workerUuid != null) {
			workerUuid = null;
			markDirty();
		}

		return null;
	}

	public void summonWorker(ServerWorld world) {
		BlockPos spawnPos = findSpawnPos(world);

		if (spawnPos == null) {
			return;
		}

		RancherEntity worker = BreederScarecrowMod.RANCHER.create(world);

		if (worker == null) {
			return;
		}

		worker.refreshPositionAndAngles(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
				world.random.nextFloat() * 360.0F, 0.0F);
		worker.setStation(pos);

		if (world.spawnEntity(worker)) {
			adopt(worker);
		}
	}

	/** Sends the rancher away with the station, so a broken block does not leave a worker behind. */
	public void dismissWorker(ServerWorld world) {
		RancherEntity worker = getWorker(world);

		if (worker != null) {
			worker.discard();
		}

		workerUuid = null;
	}

	private void adopt(RancherEntity worker) {
		workerUuid = worker.getUuid();
		markDirty();
	}

	/** A spot beside the station with room to stand, preferring ground level over the block itself. */
	@Nullable
	private BlockPos findSpawnPos(ServerWorld world) {
		for (Direction direction : Direction.Type.HORIZONTAL) {
			BlockPos candidate = pos.offset(direction);

			if (hasHeadroom(world, candidate)) {
				return candidate;
			}
		}

		for (Direction direction : Direction.Type.HORIZONTAL) {
			BlockPos candidate = pos.offset(direction).up();

			if (hasHeadroom(world, candidate)) {
				return candidate;
			}
		}

		return hasHeadroom(world, pos.up()) ? pos.up() : null;
	}

	private static boolean hasHeadroom(ServerWorld world, BlockPos pos) {
		return world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()
				&& world.getBlockState(pos.up()).getCollisionShape(world, pos.up()).isEmpty();
	}

	@Override
	public int size() {
		return INVENTORY_SIZE;
	}

	@Override
	protected DefaultedList<ItemStack> getInvStackList() {
		return inventory;
	}

	@Override
	protected void setInvStackList(DefaultedList<ItemStack> list) {
		inventory = list;
	}

	@Override
	protected Text getContainerName() {
		return Text.translatable("container.breeder_scarecrow.scarecrow");
	}

	@Override
	protected ScreenHandler createScreenHandler(int syncId, PlayerInventory playerInventory) {
		return GenericContainerScreenHandler.createGeneric9x3(syncId, playerInventory, this);
	}

	@Override
	protected void writeNbt(NbtCompound nbt) {
		super.writeNbt(nbt);

		if (!serializeLootTable(nbt)) {
			Inventories.writeNbt(nbt, inventory);
		}

		if (workerUuid != null) {
			nbt.putUuid(WORKER_KEY, workerUuid);
		}

		nbt.put(SETTINGS_KEY, settingsNbt());
	}

	@Override
	public void readNbt(NbtCompound nbt) {
		super.readNbt(nbt);
		inventory = DefaultedList.ofSize(INVENTORY_SIZE, ItemStack.EMPTY);

		if (!deserializeLootTable(nbt)) {
			Inventories.readNbt(nbt, inventory);
		}

		workerUuid = nbt.containsUuid(WORKER_KEY) ? nbt.getUuid(WORKER_KEY) : null;

		if (nbt.contains(SETTINGS_KEY, NbtElement.COMPOUND_TYPE)) {
			settings.readNbt(nbt.getCompound(SETTINGS_KEY));
		}

		// Stations saved before settings were per block only recorded the area size.
		if (nbt.contains(LEGACY_RADIUS_KEY, NbtElement.INT_TYPE)) {
			settings.workRadius = nbt.getInt(LEGACY_RADIUS_KEY);
			settings.workHeight = nbt.getInt(LEGACY_HEIGHT_KEY);
			settings.clamp();
		}
	}

	private NbtCompound settingsNbt() {
		NbtCompound nbt = new NbtCompound();
		settings.writeNbt(nbt);
		return nbt;
	}

	/** Clients get the settings, which the highlight and the settings screen read, but no contents. */
	@Override
	public NbtCompound toInitialChunkDataNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.put(SETTINGS_KEY, settingsNbt());
		return nbt;
	}

	@Nullable
	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this, BlockEntity::toInitialChunkDataNbt);
	}
}
