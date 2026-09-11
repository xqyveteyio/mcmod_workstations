package dev.keyboard.workstations.block;

import dev.keyboard.workstations.Mc;

import dev.keyboard.workstations.WorkstationsMod;
import dev.keyboard.workstations.entity.FarmerEntity;
import dev.keyboard.workstations.work.Crops;
import dev.keyboard.workstations.work.FarmSettings;
import dev.keyboard.workstations.work.PlotSurvey;
import dev.keyboard.workstations.work.SeedStock;
import dev.keyboard.workstations.work.WorkArea;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
//? if >=1.20.5 {
/* import net.minecraft.registry.RegistryWrapper; */
//?}
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The farm station: storage for seeds going in and produce coming out, the owner of one farmer, and
 * the register of which squares of farmland that farmer looks after.
 *
 * <p>The register is taken once, when the block is placed, and is the whole of what the farmer will
 * ever touch. That is deliberate: a farmer told to manage "whatever ground is nearby" would hoe up
 * paths and lawns and plant in flowerbeds. Having a fixed list also means a survey costs one block
 * lookup per plot rather than a sweep of the entire area, so a wide farm is no more expensive to
 * run than a narrow one.
 *
 * <p>Plots that stop being farmland or soil are struck off as they are noticed, which is what
 * "a plot that got destroyed is simply skipped" comes to in practice. Rebuilding the field means
 * asking for a fresh survey from the settings screen.
 */
public class FarmBlockEntity extends WorkStationBlockEntity<FarmerEntity, FarmSettings> {
	/**
	 * Most plots one station will look after. A cap rather than a limit anybody should hit: it
	 * keeps the register, the save file and the per scan survey bounded however wide the area is
	 * set, and the plots kept are the ones nearest the station.
	 */
	public static final int MAX_PLOTS = 2048;

	private static final String PLOTS_KEY = "Plots";
	private static final String SURVEYED_KEY = "Surveyed";

	private final FarmSettings settings = new FarmSettings();
	/** Seed boxes in the area and the tally of what has been sown, shared with the lumber station. */
	private final SeedStock stock = new SeedStock();
	/** Insertion ordered so the farmer works a field in a stable, roughly nearest first order. */
	private final Set<BlockPos> plots = new LinkedHashSet<>();
	/** Whether the one off survey has been taken, so a reloaded station does not retake it. */
	private boolean surveyed;

	//? if >=1.17 {
	public FarmBlockEntity(BlockPos pos, BlockState state) {
		super(WorkstationsMod.FARM_BLOCK_ENTITY, pos, state);
	}
	//?} else {
	/* public FarmBlockEntity() {
		super(WorkstationsMod.FARM_BLOCK_ENTITY);
	} */
	//?}

	@Override
	public FarmSettings getSettings() {
		return settings;
	}

	@Override
	public WorkArea getWorkArea() {
		return WorkArea.of(pos, getCachedState().get(FarmBlock.FACING),
				settings.workAlong, settings.workAcross, settings.workAbove, settings.workBelow);
	}

	@Override
	protected Class<FarmerEntity> workerClass() {
		return FarmerEntity.class;
	}

	@Override
	protected EntityType<FarmerEntity> workerType() {
		return WorkstationsMod.FARMER;
	}

	@Override
	protected int respawnTicks() {
		return settings.workerRespawnTicks;
	}

	@Override
	protected Text getContainerName() {
		return Mc.translatable("container.keyboard_workstations.farm");
	}

	/**
	 * Changing the ratios wipes the tally, so an edit takes effect from now on.
	 *
	 * <p>Without this, a field that is already planted has a tally large enough to swamp the new
	 * weights: the mix would spend a long time correcting the whole field's history rather than
	 * dividing out the plots still to come, and the change would look like it had done nothing.
	 */
	@Override
	public void applySettings(FarmSettings incoming) {
		boolean mixChanged = !settings.seedMix.matches(incoming.seedMix);
		super.applySettings(incoming);

		if (mixChanged) {
			clearPlantedTally();
		}
	}

	/** The survey is taken on the first tick rather than on placement, when there is no world yet. */
	@Override
	protected void tickAlways(ServerWorld world) {
		if (!surveyed) {
			registerPlots(world);
		}
	}

	/**
	 * Looks over the work area and takes on every square of farmland in it.
	 *
	 * <p>Only farmland, never bare dirt. Hoeing whatever soil happens to be lying about would have
	 * the farmer dig up the ground you are stood on; a square you already tilled is an unambiguous
	 * statement that it is part of the field. Once registered a plot stays registered even after
	 * being trampled back to dirt, which is what lets it be hoed again.
	 *
	 * @return how many plots the farm now has on its books
	 */
	public int registerPlots(ServerWorld world) {
		WorkArea area = getWorkArea();
		BlockPos center = area.getCenter();
		int xRadius = area.getXRadius();
		int zRadius = area.getZRadius();
		int above = area.getAbove();
		int below = area.getBelow();
		List<BlockPos> found = new ArrayList<>();
		BlockPos.Mutable cursor = new BlockPos.Mutable();

		for (int dx = -xRadius; dx <= xRadius; dx++) {
			for (int dz = -zRadius; dz <= zRadius; dz++) {
				if (!world.isChunkLoaded((center.getX() + dx) >> 4, (center.getZ() + dz) >> 4)) {
					continue;
				}

				for (int dy = -below; dy <= above; dy++) {
					cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);

					if (Crops.isFarmland(world.getBlockState(cursor))) {
						found.add(cursor.toImmutable());
					}
				}
			}
		}

		// Nearest first, so a field larger than the cap keeps the part around the station rather
		// than an arbitrary corner of itself.
		found.sort(Comparator.comparingDouble(plot -> plot.getSquaredDistance(center)));

		plots.clear();
		plots.addAll(found.subList(0, Math.min(found.size(), MAX_PLOTS)));
		surveyed = true;
		syncPlots();
		return plots.size();
	}

	/**
	 * Saves the register and sends it to everyone watching, because the highlight draws the plots
	 * and a field that changed without telling the client would be shown marking the wrong ground.
	 */
	private void syncPlots() {
		markDirty();

		if (world != null) {
			world.updateListeners(pos, getCachedState(), getCachedState(), Mc.NOTIFY_LISTENERS);
		}
	}

	/**
	 * One look at the field, striking off anything that has stopped being a plot as it goes.
	 *
	 * <p>Pruning here rather than on a timer means it happens exactly as often as it matters: the
	 * farmer only cares whether a plot is still there when it is about to be given work.
	 */
	public PlotSurvey surveyPlots(ServerWorld world) {
		PlotSurvey survey = PlotSurvey.of(world, plots);

		if (!survey.lost().isEmpty()) {
			survey.lost().forEach(plots::remove);
			syncPlots();
		}

		return survey;
	}

	public int getPlotCount() {
		return plots.size();
	}

	public Set<BlockPos> getPlots() {
		return Collections.unmodifiableSet(plots);
	}

	/** How many plots each seed holds, for the mix to divide the next one out by. */
	public Map<Item, Integer> getPlantedTally() {
		return stock.tally();
	}

	public void notePlanted(Item seed) {
		stock.note(seed);
		markDirty();
	}

	/**
	 * Forgets what has been planted where, so changing the ratios takes effect from now rather
	 * than being fought by every plot sown under the old ones.
	 */
	public void clearPlantedTally() {
		if (stock.clear()) {
			markDirty();
		}
	}

	/** The register rides along to the client, which needs it to mark the plots in the highlight. */
	@Override
	//? if >=1.20.5 {
	/* public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
		NbtCompound nbt = super.toInitialChunkDataNbt(registryLookup);
	*/
	//?} else {
	public NbtCompound toInitialChunkDataNbt() {
		NbtCompound nbt = super.toInitialChunkDataNbt();
	//?}
		nbt.putLongArray(PLOTS_KEY, packedPlots());
		return nbt;
	}

	private long[] packedPlots() {
		long[] packed = new long[plots.size()];
		int index = 0;

		for (BlockPos plot : plots) {
			packed[index++] = plot.asLong();
		}

		return packed;
	}

	@Override
	//? if >=1.20.5 {
	/* protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.writeNbt(nbt, registryLookup);
	*/
	//?} elif >=1.17 {
	protected void writeNbt(NbtCompound nbt) {
		super.writeNbt(nbt);
	//?} else {
	/* public NbtCompound writeNbt(NbtCompound nbt) {
		super.writeNbt(nbt);
	*/
	//?}
		nbt.putLongArray(PLOTS_KEY, packedPlots());
		nbt.putBoolean(SURVEYED_KEY, surveyed);
		stock.writeNbt(nbt);
		//? if <1.17 {
		/* return nbt; */
		//?}
	}

	@Override
	//? if >=1.20.5 {
	/* protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.readNbt(nbt, registryLookup);
	*/
	//?} elif >=1.17 {
	public void readNbt(NbtCompound nbt) {
		super.readNbt(nbt);
	//?} else {
	/* public void fromTag(BlockState state, NbtCompound nbt) {
		super.fromTag(state, nbt);
	*/
	//?}
		plots.clear();

		for (long packed : nbt.getLongArray(PLOTS_KEY)) {
			plots.add(BlockPos.fromLong(packed));
		}

		surveyed = nbt.getBoolean(SURVEYED_KEY);
		stock.readNbt(nbt);
	}
}
