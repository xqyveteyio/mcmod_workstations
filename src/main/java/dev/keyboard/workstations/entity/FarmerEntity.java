package dev.keyboard.workstations.entity;

import dev.keyboard.workstations.block.FarmBlockEntity;
import dev.keyboard.workstations.block.StationWorker;
import dev.keyboard.workstations.ModConfig;
import dev.keyboard.workstations.entity.ai.FarmerBrain;
import dev.keyboard.workstations.entity.ai.GateOperator;
import dev.keyboard.workstations.entity.ai.WorkerMob;
import dev.keyboard.workstations.entity.ai.WorkerMovement;
import dev.keyboard.workstations.entity.ai.WorkerNavigation;
import dev.keyboard.workstations.work.FarmSettings;
import dev.keyboard.workstations.work.WorkArea;
import dev.keyboard.workstations.work.WorkerSkin;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The worker a farm station summons. It tills, sows and harvests the plots its station has on its
 * books, carries the produce in a small pack, and empties that into the station, so a full station
 * stops the farm instead of scattering crops on the ground.
 *
 * <p>Built the same way as {@link RancherEntity} and for the same reason: a real villager runs on
 * the brain and task system and would fight this AI for control of its own pathfinding. Only the
 * appearance is borrowed, by pointing the renderer at the vanilla villager model.
 */
public class FarmerEntity extends PathAwareEntity implements StationWorker, WorkerMob {
	public static final int CARRY_SLOTS = 8;

	/**
	 * Which of {@link WorkerSkin#FARMER} to draw. Tracked rather than read off the station the way
	 * every other setting is, because the station a farmer belongs to is server side knowledge: the
	 * renderer has no way to ask which block hired the farmer standing in front of it.
	 */
	private static final TrackedData<Integer> SKIN =
			DataTracker.registerData(FarmerEntity.class, TrackedDataHandlerRegistry.INTEGER);

	/**
	 * Ticks of digging a farmer still owes before it is above ground, or zero. Tracked rather than
	 * announced, because the client has to know how deep to draw it from the very first frame: a
	 * message sent after the farmer would leave it standing in the open until that message landed.
	 */
	private static final TrackedData<Integer> BURIED =
			DataTracker.registerData(FarmerEntity.class, TrackedDataHandlerRegistry.INTEGER);

	/**
	 * The villager another mod has lent this farmer its looks from, or nothing at all, which is the
	 * usual case. Settled once on the server and carried from there so that every player sees the
	 * same farmer and it is still the same one after a restart.
	 */
	private static final TrackedData<NbtCompound> DISGUISE =
			DataTracker.registerData(FarmerEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);

	private static final String STATION_KEY = "Station";
	private static final String CARRIED_KEY = "Carried";
	private static final String DISGUISE_KEY = "Disguise";
	/** Grace period before a farmer whose station is gone gives up, in ticks. */
	private static final int HOMELESS_LIMIT = 200;
	/** How often the state label may be rewritten, in ticks. Four times a second reads fine. */
	private static final int LABEL_INTERVAL = 5;

	private final SimpleInventory carried = new SimpleInventory(CARRY_SLOTS);
	private final FarmerBrain brain = new FarmerBrain();
	private final GateOperator gates = new GateOperator();
	private final WorkerEntrance entrance =
			new WorkerEntrance(() -> dataTracker.get(BURIED), ticks -> dataTracker.set(BURIED, ticks));
	@Nullable
	private BlockPos stationPos;
	/** Last label pushed to the name tag, so an unchanged state is not resent every tick. */
	@Nullable
	private String stateLabel;
	private int labelCooldown;
	private int homelessTicks;
	private int workCooldown;
	/** Stand in orders for a farmer that has outlived its station. */
	@Nullable
	private FarmSettings orphanedSettings;

	public FarmerEntity(EntityType<? extends FarmerEntity> type, World world) {
		super(type, world);
		setPersistent();
	}

	@Override
	protected void initDataTracker() {
		super.initDataTracker();
		dataTracker.startTracking(SKIN, 0);
		dataTracker.startTracking(BURIED, 0);
		dataTracker.startTracking(DISGUISE, new NbtCompound());
	}

	public static DefaultAttributeContainer.Builder createFarmerAttributes() {
		return MobEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.5)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0);
	}

	/**
	 * A worker is equipment, not livestock, so by default nothing may kill it. The station would
	 * only summon a replacement anyway, and the pack it was carrying would end up on the floor.
	 *
	 * <p>Damage types that bypass invulnerability still land, which keeps {@code /kill} working.
	 */
	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		if (super.isInvulnerableTo(source)) {
			return true;
		}

		return ModConfig.get().invulnerable && !source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY);
	}

	/**
	 * Nothing shoves a farmer. It shoves.
	 *
	 * <p>Vanilla's jostling is one push per overlapping entity per tick and is answered in kind, so
	 * a farmer crossing a field full of livestock takes as many pushes as there are animals while
	 * returning one each. No amount of extra shoving strength wins that, because the imbalance
	 * grows with the size of the crowd.
	 *
	 * <p>Answering no here takes the farmer out of every other entity's push list, and leaves it in
	 * theirs: vanilla still separates the two on the farmer's own tick, only now the whole of the
	 * movement lands on the other one.
	 */
	@Override
	public boolean isPushable() {
		return false;
	}

	/** Gates are only worth opening if paths are allowed to run through them in the first place. */
	@Override
	protected EntityNavigation createNavigation(World world) {
		return new WorkerNavigation(this, world);
	}

	@Override
	public boolean mayOpenGates() {
		return getSettings().openFenceGates;
	}

	/**
	 * Only the reflexes live here. Deciding what to do is {@link FarmerBrain}'s job, driven from
	 * {@link #mobTick} so it is polled every tick and never has to win back movement control from
	 * an idle wander goal before it is allowed to notice there is work to do.
	 */
	@Override
	protected void initGoals() {
		goalSelector.add(0, new SwimGoal(this));
		goalSelector.add(3, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
		goalSelector.add(4, new LookAroundGoal(this));
	}

	@Override
	protected void mobTick() {
		super.mobTick();

		// Setting tracked data it already holds costs nothing, so this needs no change detection.
		// Kept up before the entrance is checked, so the farmer is dressed on the way down.
		dataTracker.set(SKIN, getSettings().workerSkin);

		// Borrowed looks are rolled once and then kept for good. Done here rather than at the
		// moment of summoning so that farmers hired before the mod that lends them was installed
		// are dressed too, and up here with the skin so it happens on the way in.
		if (WorkerDisguise.canRoll() && dataTracker.get(DISGUISE).isEmpty()) {
			dataTracker.set(DISGUISE, WorkerDisguise.roll(this));
		}

		// Still dropping out of the sky or clawing its way up through the ground. The field will
		// keep until it has both feet on the floor.
		if (entrance.isArriving()) {
			return;
		}

		if (workCooldown > 0) {
			workCooldown--;
		}

		// A farmer outliving its station would keep working a field nobody owns any more.
		if (getStation() == null) {
			if (++homelessTicks > HOMELESS_LIMIT) {
				WorkerEntrance.leave(this);
			}
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
	 * The entrance is ticked ahead of the body rather than after it, so that a farmer dropped in
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
	 * farmer hoes, sows and harvests its way across the field without ever moving.
	 */
	@Override
	public void tickMovement() {
		tickHandSwing();
		super.tickMovement();
	}

	@Override
	public void arriveBy(WorkerEntrance.Style style) {
		entrance.begin(style);
	}

	@Override
	public void handleStatus(byte status) {
		if (!entrance.handleStatus(this, status)) {
			super.handleStatus(status);
		}
	}

	/** How the farmer is arriving or leaving, which is the renderer's business as well as its own. */
	public WorkerEntrance getEntrance() {
		return entrance;
	}

	/** Which of {@link WorkerSkin#FARMER} this farmer wears, readable on either side. */
	public int getSkin() {
		return dataTracker.get(SKIN);
	}

	/**
	 * The villager this farmer has borrowed its looks from, empty when it is wearing its own. Only
	 * the renderer has any use for this, and only when the mod it came from is installed.
	 */
	public NbtCompound getDisguise() {
		return dataTracker.get(DISGUISE);
	}

	/**
	 * Never registers a landing, which is what stops the farmer ruining its own field.
	 *
	 * <p>Vanilla turns farmland back into dirt when something lands on it from any height over half
	 * a block, destroying whatever was growing there. A farmer walking a field on more than one
	 * level steps down constantly, and pathfinding has it hop fences and gate sills besides, so it
	 * would spend its day trampling the crops it had just sown and then dutifully hoeing the plots
	 * back. Since the trample is decided from {@code fallDistance}, clearing that first means the
	 * landing is never reported to the block at all.
	 */
	@Override
	protected void fall(double heightDifference, boolean onGround, BlockState state, BlockPos landedPosition) {
		fallDistance = 0.0F;
		super.fall(heightDifference, onGround, state, landedPosition);
	}

	/** A farmer that dies in a gateway must not leave the field standing open behind it. */
	@Override
	public void remove(RemovalReason reason) {
		if (!getWorld().isClient() && reason.shouldDestroy()) {
			gates.shut(this);
		}

		super.remove(reason);
	}

	/**
	 * Publishes the brain's state as the entity's name, which puts it over the farmer's head with
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
			setCustomName(Text.literal(label));
			setCustomNameVisible(true);
		}
	}

	/** Whether the farmer is queuing at a fence gate rather than getting nowhere on its own. */
	public boolean isWorkingGate() {
		return gates.isBusy();
	}

	@Override
	public void setStation(BlockPos pos) {
		stationPos = pos.toImmutable();
		homelessTicks = 0;
	}

	@Nullable
	@Override
	public BlockPos getStationPos() {
		return stationPos;
	}

	/** {@code null} when the station was broken, replaced, or its chunk is not loaded right now. */
	@Nullable
	public FarmBlockEntity getStation() {
		if (stationPos == null || !getWorld().isChunkLoaded(stationPos.getX() >> 4, stationPos.getZ() >> 4)) {
			return null;
		}

		return getWorld().getBlockEntity(stationPos) instanceof FarmBlockEntity station ? station : null;
	}

	@Nullable
	public WorkArea getWorkArea() {
		FarmBlockEntity station = getStation();
		return station == null ? null : station.getWorkArea();
	}

	/**
	 * The orders this farmer works to, which belong to its own station rather than to the mod as a
	 * whole, so neighbouring farms can be set up differently.
	 *
	 * <p>A farmer whose station is gone is on its way to being discarded, and falls back on the
	 * config file's values so the few ticks it has left need no null checking.
	 */
	public FarmSettings getSettings() {
		FarmBlockEntity station = getStation();

		if (station != null) {
			return station.getSettings();
		}

		if (orphanedSettings == null) {
			orphanedSettings = new FarmSettings();
		}

		return orphanedSettings;
	}

	public SimpleInventory getCarried() {
		return carried;
	}

	public boolean canWorkNow() {
		return workCooldown <= 0;
	}

	public int getWorkCooldown() {
		return workCooldown;
	}

	public void startWorkCooldown() {
		workCooldown = getSettings().farmIntervalTicks;
	}

	/** The station is responsible for summoning replacements, so natural despawning must not apply. */
	@Override
	public boolean canImmediatelyDespawn(double distanceSquared) {
		return false;
	}

	@Override
	protected void dropInventory() {
		super.dropInventory();
		ItemScatterer.spawn(getWorld(), this, carried);
	}

	@Nullable
	@Override
	protected SoundEvent getAmbientSound() {
		return null;
	}

	/**
	 * A farmer wearing somebody else's face should not answer in the vanilla villager's voice, so
	 * a disguise brings its own along with it.
	 */
	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		SoundEvent borrowed = WorkerDisguise.voice(getDisguise(), WorkerDisguise.Voice.HURT);
		return borrowed == null ? SoundEvents.ENTITY_VILLAGER_HURT : borrowed;
	}

	@Override
	protected SoundEvent getDeathSound() {
		SoundEvent borrowed = WorkerDisguise.voice(getDisguise(), WorkerDisguise.Voice.DEATH);
		return borrowed == null ? SoundEvents.ENTITY_VILLAGER_DEATH : borrowed;
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);

		if (stationPos != null) {
			nbt.put(STATION_KEY, NbtHelper.fromBlockPos(stationPos));
		}

		nbt.put(CARRIED_KEY, carried.toNbtList());

		NbtCompound disguise = getDisguise();

		if (!disguise.isEmpty()) {
			nbt.put(DISGUISE_KEY, disguise);
		}
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);

		if (nbt.contains(STATION_KEY, NbtElement.COMPOUND_TYPE)) {
			stationPos = NbtHelper.toBlockPos(nbt.getCompound(STATION_KEY));
		}

		carried.readNbtList(nbt.getList(CARRIED_KEY, NbtElement.COMPOUND_TYPE));

		if (nbt.contains(DISGUISE_KEY, NbtElement.COMPOUND_TYPE)) {
			dataTracker.set(DISGUISE, nbt.getCompound(DISGUISE_KEY));
		}
	}
}
