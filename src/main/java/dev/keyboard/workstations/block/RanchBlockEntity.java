package dev.keyboard.workstations.block;

import dev.keyboard.workstations.WorkstationsMod;
import dev.keyboard.workstations.entity.RancherEntity;
import dev.keyboard.workstations.work.AreaContainers;
import dev.keyboard.workstations.work.StationSettings;
import dev.keyboard.workstations.work.WorkArea;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * The ranch station: storage for feed going in and produce coming out, plus the owner of one
 * rancher. It does no ranching itself, it only keeps a worker alive and hands out the work area.
 */
public class RanchBlockEntity extends WorkStationBlockEntity<RancherEntity, StationSettings> {
	/** Where the area size lived before stations had settings of their own. */
	private static final String LEGACY_RADIUS_KEY = "Radius";
	private static final String LEGACY_HEIGHT_KEY = "Height";

	private final StationSettings settings = new StationSettings();
	/** Milk barrels standing anywhere in the work area, looked up afresh now and then. */
	private final AreaContainers<MilkBarrelBlockEntity> barrels =
			new AreaContainers<>(MilkBarrelBlockEntity.class);

	public RanchBlockEntity(BlockPos pos, BlockState state) {
		super(WorkstationsMod.RANCH_BLOCK_ENTITY, pos, state);
	}

	/**
	 * The barrel to pour into: the nearest one in the work area with room in it, or null when there
	 * is nowhere for milk to go.
	 *
	 * <p>Full barrels are passed over rather than reported, so a ranch with one barrel filled and
	 * another still empty keeps milking instead of stopping at the first thing it finds.
	 */
	@Nullable
	public MilkBarrelBlockEntity milkBarrel() {
		for (MilkBarrelBlockEntity barrel : barrels.in(world, getWorkArea())) {
			if (!barrel.isFull()) {
				return barrel;
			}
		}

		return null;
	}

	@Override
	public StationSettings getSettings() {
		return settings;
	}

	@Override
	public WorkArea getWorkArea() {
		return new WorkArea(pos, settings.workRadius, settings.workHeight);
	}

	@Override
	protected Class<RancherEntity> workerClass() {
		return RancherEntity.class;
	}

	@Override
	protected EntityType<RancherEntity> workerType() {
		return WorkstationsMod.RANCHER;
	}

	@Override
	protected int respawnTicks() {
		return settings.workerRespawnTicks;
	}

	@Override
	protected Text getContainerName() {
		return Text.translatable("container.keyboard_workstations.ranch_station");
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.readNbt(nbt, registryLookup);

		// Stations saved before settings were per block only recorded the area size. Checked only
		// when there are no settings to read, so a station that has both because someone merged the
		// old keys back in is not dragged back to them.
		if (!nbt.contains(SETTINGS_KEY, NbtElement.COMPOUND_TYPE)
				&& nbt.contains(LEGACY_RADIUS_KEY, NbtElement.INT_TYPE)) {
			settings.workRadius = nbt.getInt(LEGACY_RADIUS_KEY);
			settings.workHeight = nbt.getInt(LEGACY_HEIGHT_KEY);
			settings.clamp();
		}
	}
}
