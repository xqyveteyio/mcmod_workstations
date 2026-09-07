package dev.keyboard.workstations.block;

import dev.keyboard.workstations.WorkstationsMod;
import dev.keyboard.workstations.entity.RancherEntity;
import dev.keyboard.workstations.work.StationSettings;
import dev.keyboard.workstations.work.WorkArea;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * The ranch station: storage for feed going in and produce coming out, plus the owner of one
 * rancher. It does no ranching itself, it only keeps a worker alive and hands out the work area.
 */
public class ScarecrowBlockEntity extends WorkStationBlockEntity<RancherEntity, StationSettings> {
	/** Where the area size lived before stations had settings of their own. */
	private static final String LEGACY_RADIUS_KEY = "Radius";
	private static final String LEGACY_HEIGHT_KEY = "Height";

	private final StationSettings settings = new StationSettings();

	public ScarecrowBlockEntity(BlockPos pos, BlockState state) {
		super(WorkstationsMod.SCARECROW_BLOCK_ENTITY, pos, state);
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
		return Text.translatable("container.workstations.scarecrow");
	}

	@Override
	public void readNbt(NbtCompound nbt) {
		super.readNbt(nbt);

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
