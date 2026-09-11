package dev.keyboard.workstations.block;

import dev.keyboard.workstations.WorkstationsMod;
import dev.keyboard.workstations.entity.LumberjackEntity;
import dev.keyboard.workstations.work.LumberSettings;
import dev.keyboard.workstations.work.SeedStock;
import dev.keyboard.workstations.work.WorkArea;
import dev.keyboard.workstations.work.WoodsSurvey;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The lumber station: storage for saplings going in and wood coming out, the owner of one
 * lumberjack, and the register of stumps that lumberjack has left behind to replant.
 *
 * <p>Unlike the farm, there is no fixed register of trees. A tree is whatever is standing when
 * the lumberjack next looks, and a wood that grew overnight should be taken without asking the
 * settings screen to survey it again. What is kept is only the stumps: those are the spots a
 * tree used to be, and with auto-planting off they are the only spots a sapling goes back into.
 */
public class LumberBlockEntity extends WorkStationBlockEntity<LumberjackEntity, LumberSettings> {
	/** Most stumps one station will remember. A bound so a busy wood cannot grow the save file. */
	public static final int MAX_STUMPS = 512;

	private static final String STUMPS_KEY = "Stumps";

	private final LumberSettings settings = new LumberSettings();
	private final SeedStock stock = new SeedStock();
	/** Insertion ordered so replanting walks a wood in a stable, roughly oldest-first order. */
	private final Set<BlockPos> stumps = new LinkedHashSet<>();

	public LumberBlockEntity(BlockPos pos, BlockState state) {
		super(WorkstationsMod.LUMBER_BLOCK_ENTITY, pos, state);
	}

	@Override
	public LumberSettings getSettings() {
		return settings;
	}

	@Override
	public WorkArea getWorkArea() {
		return WorkArea.of(pos, getCachedState().get(LumberBlock.FACING),
				settings.workAlong, settings.workAcross, settings.workAbove, settings.workBelow);
	}

	@Override
	protected Class<LumberjackEntity> workerClass() {
		return LumberjackEntity.class;
	}

	@Override
	protected EntityType<LumberjackEntity> workerType() {
		return WorkstationsMod.LUMBERJACK;
	}

	@Override
	protected int respawnTicks() {
		return settings.workerRespawnTicks;
	}

	@Override
	protected Text getContainerName() {
		return Text.translatable("container.keyboard_workstations.lumber");
	}

	/**
	 * Changing the ratios wipes the tally, so an edit takes effect from now on.
	 *
	 * <p>Without this, a wood that is already planted has a tally large enough to swamp the new
	 * weights: the mix would spend a long time correcting the whole wood's history rather than
	 * dividing out the holes still to come, and the change would look like it had done nothing.
	 */
	@Override
	public void applySettings(LumberSettings incoming) {
		boolean mixChanged = !settings.saplingMix.matches(incoming.saplingMix);
		super.applySettings(incoming);

		if (mixChanged) {
			clearPlantedTally();
		}
	}

	/** One look at the wood, for the lumberjack to pick work from and for placement to count trees. */
	public WoodsSurvey surveyWoods(ServerWorld world) {
		return WoodsSurvey.of(world, getWorkArea(), stumps, settings.autoPlanting);
	}

	public Set<BlockPos> getStumps() {
		return Collections.unmodifiableSet(stumps);
	}

	/**
	 * Remembers where a tree came down, so it can be planted again. Auto-planting off uses only
	 * these; auto-planting on uses them as well as whatever open ground the survey offers.
	 */
	public void noteStump(BlockPos stump) {
		if (stumps.size() >= MAX_STUMPS) {
			stumps.remove(stumps.iterator().next());
		}

		stumps.add(stump.toImmutable());
		syncStumps();
	}

	/** The hole has been filled, or the ground is no longer plantable, so it drops off the books. */
	public void clearStump(BlockPos soil) {
		if (stumps.remove(soil) || stumps.remove(soil.up())) {
			syncStumps();
		}
	}

	/**
	 * Saves the stumps and sends them to everyone watching, because the highlight draws them and
	 * a wood that changed without telling the client would be shown marking the wrong ground.
	 */
	private void syncStumps() {
		markDirty();

		if (world != null) {
			world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
		}
	}

	public Map<Item, Integer> getPlantedTally() {
		return stock.tally();
	}

	public void notePlanted(Item sapling) {
		stock.note(sapling);
		markDirty();
	}

	public void clearPlantedTally() {
		if (stock.clear()) {
			markDirty();
		}
	}

	/** The stumps ride along to the client, which needs them to mark the holes in the highlight. */
	@Override
	public NbtCompound toInitialChunkDataNbt() {
		NbtCompound nbt = super.toInitialChunkDataNbt();
		nbt.putLongArray(STUMPS_KEY, packedStumps());
		return nbt;
	}

	private long[] packedStumps() {
		long[] packed = new long[stumps.size()];
		int index = 0;

		for (BlockPos stump : stumps) {
			packed[index++] = stump.asLong();
		}

		return packed;
	}

	@Override
	protected void writeNbt(NbtCompound nbt) {
		super.writeNbt(nbt);
		nbt.putLongArray(STUMPS_KEY, packedStumps());
		stock.writeNbt(nbt);
	}

	@Override
	public void readNbt(NbtCompound nbt) {
		super.readNbt(nbt);
		stumps.clear();

		for (long packed : nbt.getLongArray(STUMPS_KEY)) {
			stumps.add(BlockPos.fromLong(packed));
		}

		stock.readNbt(nbt);
	}
}
