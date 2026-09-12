package dev.keyboard.workstations.block;

import dev.keyboard.workstations.WorkstationsMod;
import dev.keyboard.workstations.entity.RancherEntity;
import dev.keyboard.workstations.work.AreaContainers;
import dev.keyboard.workstations.work.StationSettings;
import dev.keyboard.workstations.work.WorkArea;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;

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
	/** Feed boxes standing anywhere in the work area, looked up the same way. */
	private final AreaContainers<FeedBarrelBlockEntity> feedBarrels =
			new AreaContainers<>(FeedBarrelBlockEntity.class);

	public RanchBlockEntity(BlockPos pos, BlockState state) {
		super(WorkstationsMod.RANCH_BLOCK_ENTITY.get(), pos, state);
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
		for (MilkBarrelBlockEntity barrel : barrels.in(level, getWorkArea())) {
			if (!barrel.isFull()) {
				return barrel;
			}
		}

		return null;
	}

	/**
	 * The feed boxes anywhere in this station's work area, nearest first.
	 *
	 * <p>Separate from {@link #feedStores()} because the box is where feed is meant to live
	 * and the station is only what catches what was left on its shelves by hand.
	 */
	public List<Container> feedBoxes() {
		return List.copyOf(feedBarrels.in(level, getWorkArea()));
	}

	/**
	 * Everywhere this ranch's feed might be, the place to reach for first listed first.
	 *
	 * <p>Boxes come before the station's own shelves, so stocking a box is enough and the
	 * station stays clear for the produce coming the other way. The station is last rather than
	 * absent so feed left on its shelves by hand is still used, and with no box in the area
	 * the station is the only store there is and everything works as it did before boxes existed.
	 */
	public List<Container> feedStores() {
		List<Container> stores = new ArrayList<>(feedBoxes());
		stores.add(this);
		return stores;
	}

	@Override
	public StationSettings getSettings() {
		return settings;
	}

	@Override
	public WorkArea getWorkArea() {
		return WorkArea.of(worldPosition, getBlockState().getValue(RanchBlock.FACING),
				settings.workAlong, settings.workAcross, settings.workAbove, settings.workBelow);
	}

	@Override
	protected Class<RancherEntity> workerClass() {
		return RancherEntity.class;
	}

	@Override
	protected EntityType<RancherEntity> workerType() {
		return WorkstationsMod.RANCHER.get();
	}

	@Override
	protected int respawnTicks() {
		return settings.workerRespawnTicks;
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("container.villager_workstations.ranch_station");
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);

		// Stations saved before settings were per block only recorded the area size. Checked only
		// when there are no settings to read, so a station that has both because someone merged the
		// old keys back in is not dragged back to them.
		if (input.child(SETTINGS_KEY).isEmpty()) {
			input.getInt(LEGACY_RADIUS_KEY).ifPresent(radius -> {
				settings.workAlong = radius;
				settings.workAcross = radius;
				int height = input.getIntOr(LEGACY_HEIGHT_KEY, radius);
				settings.workAbove = height;
				settings.workBelow = height;
				settings.clamp();
			});
		}
	}
}
