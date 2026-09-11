package dev.keyboard.workstations.block;

import dev.keyboard.workstations.entity.WorkerEntrance;
import dev.keyboard.workstations.work.AreaContainers;
import dev.keyboard.workstations.work.WorkArea;
import dev.keyboard.workstations.work.WorkerSettings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Everything a station does regardless of what it is a station for: hold a chest full of supplies
 * and produce, keep exactly one worker alive, and hand out the area that worker is allowed to work.
 *
 * <p>Deciding what the work actually is belongs to the worker's brain, and the orders it works to
 * belong to the subclass, which owns its own {@link WorkerSettings}.
 *
 * @param <W> the worker this kind of station employs
 * @param <S> the orders this kind of station keeps
 */
public abstract class WorkStationBlockEntity<W extends MobEntity & StationWorker, S extends WorkerSettings<S>>
		extends LootableContainerBlockEntity {
	public static final int INVENTORY_SIZE = 27;

	protected static final String SETTINGS_KEY = "Settings";
	private static final String WORKER_KEY = "Worker";
	private static final String RESPAWN_KEY = "Respawn";

	private DefaultedList<ItemStack> inventory = DefaultedList.ofSize(INVENTORY_SIZE, ItemStack.EMPTY);
	/** Seed boxes standing anywhere in the work area, looked up afresh now and then. */
	private final AreaContainers<SeedBoxBlockEntity> seedBoxes =
			new AreaContainers<>(SeedBoxBlockEntity.class);
	@Nullable
	private UUID workerUuid;
	private int respawnTimer;

	protected WorkStationBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	/**
	 * The seed boxes anywhere in this station's work area, nearest first.
	 *
	 * <p>Kept here rather than by the stations that sow, because a box is where seed and saplings
	 * belong whichever worker picked them up. A ranch that harvests a field of carrots on its way
	 * past has the same reason to put them somewhere other than the shelves its produce lands on,
	 * and a box between two stations is found by both without either writing a second scan.
	 *
	 * <p>Separate from {@link #seedStores()} because the boxes are where seed is meant to end up
	 * and the station is only what catches the overflow, a distinction that matters when deciding
	 * how much of something belongs in a box in the first place.
	 */
	public List<Inventory> seedBoxes() {
		return List.copyOf(seedBoxes.in(world, getWorkArea()));
	}

	/**
	 * Everywhere this station's seed, saplings and feed might be, the place to reach for first
	 * listed first.
	 *
	 * <p>Seed boxes come before the station's own shelves: seed is taken out of a box while one
	 * holds any, which is what keeps the station's own space clear for the produce coming the other
	 * way. The station is last rather than absent so seed left on its shelves by hand is still
	 * used, and with no box in the area the station is the only store there is and everything works
	 * as it did before boxes existed.
	 */
	public List<Inventory> seedStores() {
		List<Inventory> stores = new ArrayList<>(seedBoxes());
		stores.add(this);
		return stores;
	}

	/**
	 * This station's orders, started off from the config file's values and edited in game from the
	 * settings screen. Sent to the client in full: it needs the area size to draw the highlight,
	 * and the screen reads the rest straight off the block rather than asking the server for it.
	 */
	public abstract S getSettings();

	public abstract WorkArea getWorkArea();

	protected abstract Class<W> workerClass();

	protected abstract EntityType<W> workerType();

	/** Ticks to wait before replacing a worker that died or went missing. */
	protected abstract int respawnTicks();

	/** Called every tick a worker is on duty, for whatever the subclass wants to keep in step. */
	protected void tickWithWorker(ServerWorld world, W worker) {
	}

	/** Called every tick, worker or no worker, before the worker is looked for. */
	protected void tickAlways(ServerWorld world) {
	}

	public static <W extends MobEntity & StationWorker, S extends WorkerSettings<S>> void serverTick(
			World world, BlockPos pos, BlockState state, WorkStationBlockEntity<W, S> station) {
		if (!(world instanceof ServerWorld serverWorld)) {
			return;
		}

		station.tickAlways(serverWorld);
		W worker = station.getWorker(serverWorld);

		if (worker != null) {
			// Refreshed every tick so a worker restored from disk finds its way home again.
			worker.setStation(pos);
			station.respawnTimer = station.respawnTicks();
			station.tickWithWorker(serverWorld, worker);
			return;
		}

		if (--station.respawnTimer <= 0) {
			station.respawnTimer = station.respawnTicks();
			station.summonWorker(serverWorld);
		}
	}

	/**
	 * Takes on a set of settings from the screen. Listeners are told even though only the area size
	 * is drawn, because that size is what the highlight is built from and a resize that never
	 * reached the client would leave the box lying about where the worker works.
	 */
	public void applySettings(S incoming) {
		getSettings().copyFrom(incoming);
		markDirty();

		if (world != null) {
			world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
		}
	}

	/**
	 * The worker belonging to this station, or {@code null} if it died or is not loaded.
	 *
	 * <p>The saved id is kept even when the worker cannot be found. The worker's chunk can unload
	 * without the station's, and the entity region is read separately from this block, so the
	 * worker may simply not be in the world yet. Forgetting the id in either case would leave the
	 * station unable to recognise its own worker when that worker comes back. A worker that is
	 * truly gone is replaced once the hire delay runs out, which writes a new id through
	 * {@link #adopt}.
	 */
	@Nullable
	public W getWorker(ServerWorld world) {
		if (workerUuid != null) {
			Entity entity = world.getEntity(workerUuid);

			if (workerClass().isInstance(entity) && entity.isAlive()) {
				return workerClass().cast(entity);
			}

			// Still claiming a worker that is not in the world right now. Looking around for a
			// substitute would take on whoever happens to be standing nearby, and the claimed
			// worker would then be the one sent away.
			return null;
		}

		// No one is on the books, so take on a worker already standing in the area before
		// summoning a new one.
		List<W> strays = world.getEntitiesByClass(workerClass(), getWorkArea().getBox().expand(4.0),
				worker -> worker.isAlive() && pos.equals(worker.getStationPos()));

		if (!strays.isEmpty()) {
			adopt(strays.get(0));
			return strays.get(0);
		}

		return null;
	}

	/**
	 * Whether this station has taken on a worker, and it is not {@code worker}. A station that has
	 * taken no one on yet is still choosing, so nobody should stand down for that.
	 */
	public boolean hasAdoptedOtherThan(UUID worker) {
		return workerUuid != null && !workerUuid.equals(worker);
	}

	public void summonWorker(ServerWorld world) {
		BlockPos spawnPos = findSpawnPos(world);

		if (spawnPos == null) {
			return;
		}

		W worker = workerType().create(world);

		if (worker == null) {
			return;
		}

		// A worker that dropped in has to be put in the air it is going to drop through, so where
		// it starts depends on how it is arriving.
		WorkerEntrance.Style style = WorkerEntrance.styleFor(world, spawnPos);
		double startY = spawnPos.getY() + (style == WorkerEntrance.Style.FALL ? WorkerEntrance.FALL_HEIGHT : 0);

		worker.refreshPositionAndAngles(spawnPos.getX() + 0.5, startY, spawnPos.getZ() + 0.5,
				world.random.nextFloat() * 360.0F, 0.0F);
		worker.setStation(pos);
		worker.arriveBy(style);

		if (world.spawnEntity(worker)) {
			adopt(worker);
		}
	}

	/** What {@link #recallWorker} managed to do, so the caller can report it. */
	public enum Recall {
		SUMMONED,
		MOVED,
		NO_ROOM
	}

	/**
	 * Brings the worker back to the station, hiring a new one if it has gone. This is the way out of
	 * a worker that wandered off after a stray animal or walled itself in: rather than digging it
	 * out, you call it home and it picks its work up from there.
	 */
	public Recall recallWorker(ServerWorld world) {
		W worker = getWorker(world);

		if (worker == null) {
			summonWorker(world);
			return getWorker(world) == null ? Recall.NO_ROOM : Recall.SUMMONED;
		}

		BlockPos spawnPos = findSpawnPos(world);

		if (spawnPos == null) {
			return Recall.NO_ROOM;
		}

		// The path it was walking leads from where it used to be, so it has to be thrown away.
		worker.getNavigation().stop();
		worker.teleport(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
		return Recall.MOVED;
	}

	/** Sends the worker away with the station, so a broken block does not leave one behind. */
	public void dismissWorker(ServerWorld world) {
		W worker = getWorker(world);

		if (worker != null) {
			WorkerEntrance.leave(worker);
		}

		workerUuid = null;
	}

	/**
	 * Takes {@code worker} on as the one this station employs. The hire delay starts over, because
	 * a station that has just filled the post has no reason to keep counting down to a replacement.
	 */
	private void adopt(W worker) {
		workerUuid = worker.getUuid();
		respawnTimer = respawnTicks();
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

		nbt.putInt(RESPAWN_KEY, respawnTimer);
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
			getSettings().readNbt(nbt.getCompound(SETTINGS_KEY));
		}

		// The worker lives in the entity region, not with this block, and the two are loaded on
		// their own clocks. A station that has just come back must wait the full hire delay
		// before deciding its worker is gone, or it will hire another while the first is still
		// being read in. A countdown already written is resumed so a station saved mid-wait does
		// not start over; a station that has none is given a full delay, because the field would
		// otherwise be zero and the wait would be over on the first tick. respawnTicks() is asked
		// after the settings have been read, which is where that delay is kept.
		if (nbt.contains(RESPAWN_KEY, NbtElement.INT_TYPE)) {
			respawnTimer = nbt.getInt(RESPAWN_KEY);
		} else {
			respawnTimer = respawnTicks();
		}
	}

	protected NbtCompound settingsNbt() {
		NbtCompound nbt = new NbtCompound();
		getSettings().writeNbt(nbt);
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
