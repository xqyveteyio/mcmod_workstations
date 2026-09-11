package dev.keyboard.workstations.entity;

import dev.keyboard.workstations.block.LumberBlockEntity;
import dev.keyboard.workstations.block.StationWorker;
import dev.keyboard.workstations.ModConfig;
import dev.keyboard.workstations.entity.ai.LumberjackBrain;
import dev.keyboard.workstations.entity.ai.GateOperator;
import dev.keyboard.workstations.entity.ai.WorkerMob;
import dev.keyboard.workstations.entity.ai.WorkerMovement;
import dev.keyboard.workstations.entity.ai.WorkerNavigation;
import dev.keyboard.workstations.work.LumberSettings;
import dev.keyboard.workstations.work.WorkArea;
import dev.keyboard.workstations.work.WorkerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * The worker a lumber station summons. It fells trees in its station's area, plants saplings back,
 * carries the wood in a small pack, and empties that into the station, so a full station
 * stops the wood instead of scattering logs on the ground.
 *
 * <p>Built the same way as {@link FarmerEntity} and for the same reason: a real villager runs on
 * the brain and task system and would fight this AI for control of its own pathfinding. Only the
 * appearance is borrowed, by pointing the renderer at the vanilla villager model.
 */
public class LumberjackEntity extends PathfinderMob implements StationWorker, WorkerMob {
	public static final int CARRY_SLOTS = 8;

	/**
	 * Which of {@link WorkerSkin#LUMBERJACK} to draw. Tracked rather than read off the station the way
	 * every other setting is, because the station a lumberjack belongs to is server side knowledge: the
	 * renderer has no way to ask which block hired the lumberjack standing in front of it.
	 */
	private static final EntityDataAccessor<Integer> SKIN =
			SynchedEntityData.defineId(LumberjackEntity.class, EntityDataSerializers.INT);

	/**
	 * Ticks of digging a lumberjack still owes before it is above ground, or zero. Tracked rather than
	 * announced, because the client has to know how deep to draw it from the very first frame: a
	 * message sent after the lumberjack would leave it standing in the open until that message landed.
	 */
	private static final EntityDataAccessor<Integer> BURIED =
			SynchedEntityData.defineId(LumberjackEntity.class, EntityDataSerializers.INT);

	private static final String STATION_KEY = "Station";
	private static final String CARRIED_KEY = "Carried";
	/** Grace period before a lumberjack whose station is gone gives up, in ticks. */
	private static final int HOMELESS_LIMIT = 200;
	/** How often the state label may be rewritten, in ticks. Four times a second reads fine. */
	private static final int LABEL_INTERVAL = 5;

	private final SimpleContainer carried = new SimpleContainer(CARRY_SLOTS);
	private final LumberjackBrain brain = new LumberjackBrain();
	private final GateOperator gates = new GateOperator();
	private final WorkerEntrance entrance =
			new WorkerEntrance(() -> entityData.get(BURIED), ticks -> entityData.set(BURIED, ticks));
	@Nullable
	private BlockPos stationPos;
	/** Last label pushed to the name tag, so an unchanged state is not resent every tick. */
	@Nullable
	private String stateLabel;
	private int labelCooldown;
	private int homelessTicks;
	private int workCooldown;
	/** Stand in orders for a lumberjack that has outlived its station. */
	@Nullable
	private LumberSettings orphanedSettings;

	public LumberjackEntity(EntityType<? extends LumberjackEntity> type, Level world) {
		super(type, world);
		setPersistenceRequired();
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(SKIN, 0);
		builder.define(BURIED, 0);
	}

	public static AttributeSupplier.Builder createLumberjackAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, 0.5)
				.add(Attributes.FOLLOW_RANGE, 32.0);
	}

	/**
	 * A worker is equipment, not livestock, so by default nothing may kill it. The station would
	 * only summon a replacement anyway, and the pack it was carrying would end up on the floor.
	 *
	 * <p>Damage types that bypass invulnerability still land, which keeps {@code /kill} working.
	 */
	@Override
	public boolean isInvulnerableTo(ServerLevel world, DamageSource source) {
		if (super.isInvulnerableTo(world, source)) {
			return true;
		}

		return ModConfig.get().invulnerable && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
	}

	/**
	 * Nothing shoves a lumberjack. It shoves.
	 *
	 * <p>Vanilla's jostling is one push per overlapping entity per tick and is answered in kind, so
	 * a lumberjack crossing a field full of livestock takes as many pushes as there are animals while
	 * returning one each. No amount of extra shoving strength wins that, because the imbalance
	 * grows with the size of the crowd.
	 *
	 * <p>Answering no here takes the lumberjack out of every other entity's push list, and leaves it in
	 * theirs: vanilla still separates the two on the lumberjack's own tick, only now the whole of the
	 * movement lands on the other one.
	 */
	@Override
	public boolean isPushable() {
		return false;
	}

	/** Gates are only worth opening if paths are allowed to run through them in the first place. */
	@Override
	protected PathNavigation createNavigation(Level world) {
		return new WorkerNavigation(this, world);
	}

	@Override
	public boolean mayOpenGates() {
		return getSettings().openFenceGates;
	}

	/**
	 * Only the reflexes live here. Deciding what to do is {@link LumberjackBrain}'s job, driven from
	 * {@link #customServerAiStep} so it is polled every tick and never has to win back movement control from
	 * an idle wander goal before it is allowed to notice there is work to do.
	 */
	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
		goalSelector.addGoal(4, new RandomLookAroundGoal(this));
	}

	@Override
	protected void customServerAiStep(ServerLevel world) {
		super.customServerAiStep(world);

		// Setting tracked data it already holds costs nothing, so this needs no change detection.
		// Kept up before the entrance is checked, so the lumberjack is dressed on the way down.
		entityData.set(SKIN, getSettings().workerSkin);

		// Still dropping out of the sky or clawing its way up through the ground. The field will
		// keep until it has both feet on the floor.
		if (entrance.isArriving()) {
			return;
		}

		if (workCooldown > 0) {
			workCooldown--;
		}

		// A lumberjack outliving its station would keep working a field nobody owns any more.
		LumberBlockEntity station = getStation();

		if (station == null) {
			if (++homelessTicks > HOMELESS_LIMIT) {
				WorkerEntrance.leave(this);
			}
		} else if (station.hasAdoptedOtherThan(getUUID())) {
			// The station has taken someone else on. Staying would leave two lumberjacks working the
			// same field, and only the one on the books is meant to be.
			WorkerEntrance.leave(this);
		} else {
			homelessTicks = 0;
		}

		brain.tick(this);
		// After the brain, so a gate on a path it just laid down is seen on this very tick.
		gates.tick(this);

		if (getSettings().shoveBlockers) {
			WorkerMovement.shoveBlockers(this);
		}

		updateStateLabel();
	}

	/**
	 * The entrance is ticked ahead of the body rather than after it, so that a lumberjack dropped in
	 * out of the sky is already clear of its fall by the time the landing is worked out.
	 */
	@Override
	public void tick() {
		entrance.tick(this);
		super.tick();
	}

	/**
	 * Carries a swing of the arm forward once one has been started.
	 *
	 * <p>Swinging is two halves, and vanilla only gives passive mobs the first. {@code swingHand}
	 * raises the arm and tells every client watching, but something has to walk the swing back down
	 * a tick at a time, and the only things vanilla has doing that are players and hostile mobs. A
	 * worker is neither, so without this the arm goes up on the first job and stays there: the
	 * lumberjack hoes, sows and harvests its way across the field without ever moving.
	 */
	@Override
	public void aiStep() {
		updateSwingTime();
		super.aiStep();
	}

	@Override
	public void arriveBy(WorkerEntrance.Style style) {
		entrance.begin(style);
	}

	@Override
	public void handleEntityEvent(byte status) {
		if (!entrance.handleStatus(this, status)) {
			super.handleEntityEvent(status);
		}
	}

	/** How the lumberjack is arriving or leaving, which is the renderer's business as well as its own. */
	public WorkerEntrance getEntrance() {
		return entrance;
	}

	/** Which of {@link WorkerSkin#LUMBERJACK} this lumberjack wears, readable on either side. */
	public int getSkin() {
		return entityData.get(SKIN);
	}

	/**
	 * Never registers a landing, which is what stops the lumberjack ruining its own field.
	 *
	 * <p>Vanilla turns farmland back into dirt when something lands on it from any height over half
	 * a block, destroying whatever was growing there. A lumberjack walking a field on more than one
	 * level steps down constantly, and pathfinding has it hop fences and gate sills besides, so it
	 * would spend its day trampling the crops it had just sown and then dutifully hoeing the plots
	 * back. Since the trample is decided from {@code fallDistance}, clearing that first means the
	 * landing is never reported to the block at all.
	 */
	@Override
	protected void checkFallDamage(double heightDifference, boolean onGround, BlockState state, BlockPos landedPosition) {
		fallDistance = 0.0F;
		super.checkFallDamage(heightDifference, onGround, state, landedPosition);
	}

	/** A lumberjack that dies in a gateway must not leave the field standing open behind it. */
	@Override
	public void remove(RemovalReason reason) {
		if (!level().isClientSide() && reason.shouldDestroy()) {
			gates.shut(this);
		}

		super.remove(reason);
	}

	/**
	 * Publishes the brain's state as the entity's name, which puts it over the lumberjack's head with
	 * no client side code at all.
	 *
	 * <p>Throttled, and skipped when the text has not changed, because a name change marks the
	 * entity's tracked data dirty and resends it to every player watching.
	 */
	private void updateStateLabel() {
		if (!getSettings().showWorkerState) {
			if (stateLabel != null) {
				stateLabel = null;
				setCustomName(null);
				setCustomNameVisible(false);
			}

			return;
		}

		if (--labelCooldown > 0) {
			return;
		}

		labelCooldown = LABEL_INTERVAL;
		String label = brain.describe(this);

		if (!label.equals(stateLabel)) {
			stateLabel = label;
			setCustomName(Component.literal(label));
			setCustomNameVisible(true);
		}
	}

	/** Whether the lumberjack is queuing at a fence gate rather than getting nowhere on its own. */
	public boolean isWorkingGate() {
		return gates.isBusy();
	}

	@Override
	public void setStation(BlockPos pos) {
		stationPos = pos.immutable();
		homelessTicks = 0;
	}

	@Nullable
	@Override
	public BlockPos getStationPos() {
		return stationPos;
	}

	/** {@code null} when the station was broken, replaced, or its chunk is not loaded right now. */
	@Nullable
	public LumberBlockEntity getStation() {
		if (stationPos == null || !level().hasChunk(stationPos.getX() >> 4, stationPos.getZ() >> 4)) {
			return null;
		}

		return level().getBlockEntity(stationPos) instanceof LumberBlockEntity station ? station : null;
	}

	@Nullable
	public WorkArea getWorkArea() {
		LumberBlockEntity station = getStation();
		return station == null ? null : station.getWorkArea();
	}

	/**
	 * The orders this lumberjack works to, which belong to its own station rather than to the mod as a
	 * whole, so neighbouring farms can be set up differently.
	 *
	 * <p>A lumberjack whose station is gone is on its way to being discarded, and falls back on the
	 * config file's values so the few ticks it has left need no null checking.
	 */
	public LumberSettings getSettings() {
		LumberBlockEntity station = getStation();

		if (station != null) {
			return station.getSettings();
		}

		if (orphanedSettings == null) {
			orphanedSettings = new LumberSettings();
		}

		return orphanedSettings;
	}

	public SimpleContainer getCarried() {
		return carried;
	}

	public boolean canWorkNow() {
		return workCooldown <= 0;
	}

	public int getWorkCooldown() {
		return workCooldown;
	}

	public void startWorkCooldown() {
		workCooldown = getSettings().lumberIntervalTicks;
	}

	/** The station is responsible for summoning replacements, so natural despawning must not apply. */
	@Override
	public boolean removeWhenFarAway(double distanceSquared) {
		return false;
	}

	@Override
	protected void dropEquipment(ServerLevel world) {
		super.dropEquipment(world);
		Containers.dropContents(level(), this, carried);
	}

	@Nullable
	@Override
	protected SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.VILLAGER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.VILLAGER_DEATH;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);

		if (stationPos != null) {
			output.store(STATION_KEY, BlockPos.CODEC, stationPos);
		}

		carried.storeAsItemList(output.list(CARRIED_KEY, ItemStack.CODEC));
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		stationPos = input.read(STATION_KEY, BlockPos.CODEC).orElse(null);
		carried.fromItemList(input.listOrEmpty(CARRIED_KEY, ItemStack.CODEC));
	}
}
