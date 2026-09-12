package dev.keyboard.workstations.block;

import dev.keyboard.workstations.WorkstationsMod;
import dev.keyboard.workstations.entity.LumberjackEntity;
import dev.keyboard.workstations.work.LumberSettings;
import dev.keyboard.workstations.work.SeedStock;
import dev.keyboard.workstations.work.WorkArea;
import dev.keyboard.workstations.work.WoodsSurvey;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

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
		super(WorkstationsMod.LUMBER_BLOCK_ENTITY.get(), pos, state);
	}

	@Override
	public LumberSettings getSettings() {
		return settings;
	}

	@Override
	public WorkArea getWorkArea() {
		return WorkArea.of(worldPosition, getBlockState().getValue(LumberBlock.FACING),
				settings.workAlong, settings.workAcross, settings.workAbove, settings.workBelow);
	}

	@Override
	protected Class<LumberjackEntity> workerClass() {
		return LumberjackEntity.class;
	}

	@Override
	protected EntityType<LumberjackEntity> workerType() {
		return WorkstationsMod.LUMBERJACK.get();
	}

	@Override
	protected int respawnTicks() {
		return settings.workerRespawnTicks;
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("container.villager_workstations.lumber");
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
	public WoodsSurvey surveyWoods(ServerLevel world) {
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

		stumps.add(stump.immutable());
		syncStumps();
	}

	/** The hole has been filled, or the ground is no longer plantable, so it drops off the books. */
	public void clearStump(BlockPos soil) {
		if (stumps.remove(soil) || stumps.remove(soil.above())) {
			syncStumps();
		}
	}

	/**
	 * Saves the stumps and sends them to everyone watching, because the highlight draws them and
	 * a wood that changed without telling the client would be shown marking the wrong ground.
	 */
	private void syncStumps() {
		setChanged();

		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	public Map<Item, Integer> getPlantedTally() {
		return stock.tally();
	}

	public void notePlanted(Item sapling) {
		stock.note(sapling);
		setChanged();
	}

	public void clearPlantedTally() {
		if (stock.clear()) {
			setChanged();
		}
	}

	/** The stumps ride along to the client, which needs them to mark the holes in the highlight. */
	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registryLookup) {
		CompoundTag nbt = super.getUpdateTag(registryLookup);
		nbt.store(STUMPS_KEY, BlockPos.CODEC.listOf(), List.copyOf(stumps));
		return nbt;
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.store(STUMPS_KEY, BlockPos.CODEC.listOf(), List.copyOf(stumps));
		stock.save(output);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		stumps.clear();
		input.read(STUMPS_KEY, BlockPos.CODEC.listOf()).ifPresent(stumps::addAll);
		stock.load(input);
	}
}
