package dev.keyboard.workstations.entity;

import dev.keyboard.workstations.Mc;

import dev.keyboard.workstations.block.RanchBlockEntity;
import dev.keyboard.workstations.block.StationWorker;
import dev.keyboard.workstations.entity.ai.GateOperator;
import dev.keyboard.workstations.ModConfig;
import dev.keyboard.workstations.entity.ai.RancherBrain;
import dev.keyboard.workstations.entity.ai.WorkerMovement;
import dev.keyboard.workstations.entity.ai.WorkerMob;
import dev.keyboard.workstations.entity.ai.WorkerNavigation;
import dev.keyboard.workstations.work.StationSettings;
import dev.keyboard.workstations.work.WorkArea;
import dev.keyboard.workstations.work.WorkerSkin;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.pathing.EntityNavigation;
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
//? if >=1.20 {
import net.minecraft.registry.tag.DamageTypeTags;
//?}
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The worker a station summons. It carries what it collects in a small pack and empties that into
 * the station, so a full station stops the ranch instead of scattering drops on the ground.
 *
 * <p>This is a mod entity rather than a real villager on purpose: a villager runs on the brain and
 * task system and would fight the AI here for control of its own pathfinding. Only the appearance
 * is borrowed, by pointing the renderer at the vanilla villager model.
 */
public class RancherEntity extends PathAwareEntity implements StationWorker, WorkerMob {
	public static final int CARRY_SLOTS = 8;

	/**
	 * Which of {@link WorkerSkin#RANCHER} to draw. Tracked rather than read off the station the way
	 * every other setting is, because the station a rancher belongs to is server side knowledge:
	 * the renderer has no way to ask which block hired the rancher standing in front of it.
	 */
	private static final TrackedData<Integer> SKIN =
			DataTracker.registerData(RancherEntity.class, TrackedDataHandlerRegistry.INTEGER);

	/**
	 * Ticks of digging a rancher still owes before it is above ground, or zero. Tracked rather than
	 * announced, because the client has to know how deep to draw it from the very first frame: a
	 * message sent after the rancher would leave it standing in the open until that message landed.
	 */
	private static final TrackedData<Integer> BURIED =
			DataTracker.registerData(RancherEntity.class, TrackedDataHandlerRegistry.INTEGER);

	/**
	 * The villager another mod has lent this rancher its looks from, or nothing at all, which is
	 * the usual case. Settled once on the server and carried from there so that every player sees
	 * the same rancher and it is still the same one after a restart.
	 */
	private static final TrackedData<NbtCompound> DISGUISE =
			DataTracker.registerData(RancherEntity.class, Mc.nbtTracker());

	private static final String STATION_KEY = "Station";
	private static final String CARRIED_KEY = "Carried";
	private static final String DISGUISE_KEY = "Disguise";
	/** Grace period before a rancher whose station is gone gives up, in ticks. */
	private static final int HOMELESS_LIMIT = 200;
	/** How often the state label may be rewritten, in ticks. Four times a second reads fine. */
	private static final int LABEL_INTERVAL = 5;

	private final SimpleInventory carried = new SimpleInventory(CARRY_SLOTS);
	private final RancherBrain brain = new RancherBrain();
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
	private int feedCooldown;
	private int cullCooldown;
	/** Stand in orders for a rancher that has outlived its station. */
	@Nullable
	private StationSettings orphanedSettings;

	public RancherEntity(EntityType<? extends RancherEntity> type, World world) {
		super(type, world);
		setPersistent();
	}

	@Override
	//? if >=1.20.5 {
	/* protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(SKIN, 0);
		builder.add(BURIED, 0);
		builder.add(DISGUISE, new NbtCompound());
	}
	*/
	//?} else {
	protected void initDataTracker() {
		super.initDataTracker();
		dataTracker.startTracking(SKIN, 0);
		dataTracker.startTracking(BURIED, 0);
		dataTracker.startTracking(DISGUISE, new NbtCompound());
	}
	//?}

	public static DefaultAttributeContainer.Builder createRancherAttributes() {
		return MobEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.5)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 4.0);
	}

	/**
	 * A worker is equipment, not livestock, so by default nothing may kill it: not the mob it is
	 * butchering, not a cactus it walked into, not a creeper wandering past. The station would only
	 * summon a replacement anyway, and the pack it was carrying would end up on the floor.
	 *
	 * <p>Damage types that bypass invulnerability still land, which keeps {@code /kill} working.
	 */
	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		if (super.isInvulnerableTo(source)) {
			return true;
		}

		//? if >=1.20 {
		return ModConfig.get().invulnerable && !source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY);
		//?} else {
		/* return ModConfig.get().invulnerable && !source.isOutOfWorld(); */
		//?}
	}

	/**
	 * Nothing shoves a rancher. It shoves.
	 *
	 * <p>Vanilla's jostling is one push per overlapping animal per tick and is answered in kind, so
	 * a rancher wading into a herd takes as many pushes as there are animals while returning one
	 * each. No amount of extra shoving strength wins that: the imbalance grows with the size of the
	 * pen, which is exactly where a rancher is most needed and where it used to be squeezed back
	 * out of its own work.
	 *
	 * <p>Answering no here takes the rancher out of every other entity's push list, and leaves it
	 * in theirs: vanilla still separates the two on the rancher's own tick, only now the whole of
	 * the movement lands on the animal. A crowd of forty is then no harder to walk through than
	 * one cow.
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
	 * Only the reflexes live here. Deciding what to do is {@link RancherBrain}'s job, driven from
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
		// Kept up before the entrance is checked, so the rancher is dressed on the way down.
		dataTracker.set(SKIN, getSettings().workerSkin);

		// Borrowed looks are rolled once and then kept for good. Done here rather than at the
		// moment of summoning so that ranchers hired before the mod that lends them was installed
		// are dressed too, and up here with the skin so it happens on the way in.
		if (WorkerDisguise.canRoll() && dataTracker.get(DISGUISE).isEmpty()) {
			dataTracker.set(DISGUISE, WorkerDisguise.roll(this));
		}

		// Still dropping out of the sky or clawing its way up through the ground. Whatever the
		// animals are up to can wait until it has both feet on the floor.
		if (entrance.isArriving()) {
			return;
		}

		if (feedCooldown > 0) {
			feedCooldown--;
		}

		if (cullCooldown > 0) {
			cullCooldown--;
		}

		// A rancher outliving its station would keep working an area nobody owns any more.
		RanchBlockEntity station = getStation();

		if (station == null) {
			if (++homelessTicks > HOMELESS_LIMIT) {
				WorkerEntrance.leave(this);
			}
		} else if (station.hasAdoptedOtherThan(getUuid())) {
			// The station has taken someone else on. Staying would leave two ranchers working the
			// same pen, and only the one on the books is meant to be.
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
	 * The entrance is ticked ahead of the body rather than after it. Part of what it does is write
	 * off the fall damage owed by a rancher dropped in out of the sky, and once the body has moved
	 * for the tick the landing has already been worked out and taken out of its health.
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
	 * rancher shears, feeds and butchers its way around the pen without ever moving.
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

	/** How the rancher is arriving or leaving, which is the renderer's business as well as its own. */
	public WorkerEntrance getEntrance() {
		return entrance;
	}

	/** Which of {@link WorkerSkin#RANCHER} this rancher wears, readable on either side. */
	public int getSkin() {
		return dataTracker.get(SKIN);
	}

	/**
	 * The villager this rancher has borrowed its looks from, empty when it is wearing its own. Only
	 * the renderer has any use for this, and only when the mod it came from is installed.
	 */
	public NbtCompound getDisguise() {
		return dataTracker.get(DISGUISE);
	}

	/** A rancher that dies in a gateway must not leave the pen standing open behind it. */
	@Override
	//? if >=1.17 {
	public void remove(RemovalReason reason) {
		if (!Mc.world(this).isClient() && reason.shouldDestroy()) {
			gates.shut(this);
		}

		super.remove(reason);
	}
	//?} else {
	/* public void remove() {
		if (!Mc.world(this).isClient) {
			gates.shut(this);
		}

		super.remove();
	} */
	//?}

	/**
	 * Publishes the brain's state as the entity's name, which puts it over the rancher's head with
	 * no client side code at all.
	 *
	 * <p>Throttled, and skipped when the text has not changed, because a name change marks the
	 * entity's tracked data dirty and resends it to every player watching. The countdown in the
	 * label ticks every tick, so writing it straight through would mean a packet per tick.
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
			setCustomName(Mc.literal(label));
			setCustomNameVisible(true);
		}
	}

	/** Whether the rancher is queuing at a fence gate rather than getting nowhere on its own. */
	public boolean isWorkingGate() {
		return gates.isBusy();
	}

	public int getFeedCooldown() {
		return feedCooldown;
	}

	public int getCullCooldown() {
		return cullCooldown;
	}

	public void setStation(BlockPos pos) {
		stationPos = pos.toImmutable();
		homelessTicks = 0;
	}

	@Nullable
	public BlockPos getStationPos() {
		return stationPos;
	}

	/** {@code null} when the station was broken, replaced, or its chunk is not loaded right now. */
	@Nullable
	public RanchBlockEntity getStation() {
		if (stationPos == null || !Mc.world(this).isChunkLoaded(stationPos.getX() >> 4, stationPos.getZ() >> 4)) {
			return null;
		}

		return Mc.world(this).getBlockEntity(stationPos) instanceof RanchBlockEntity station ? station : null;
	}

	@Nullable
	public WorkArea getWorkArea() {
		RanchBlockEntity station = getStation();
		return station == null ? null : station.getWorkArea();
	}

	/**
	 * The orders this rancher works to, which belong to its own station rather than to the mod as a
	 * whole, so neighbouring ranches can be set up differently.
	 *
	 * <p>A rancher whose station is gone is on its way to being discarded, and falls back on the
	 * config file's values so the few ticks it has left need no null checking.
	 */
	public StationSettings getSettings() {
		RanchBlockEntity station = getStation();

		if (station != null) {
			return station.getSettings();
		}

		if (orphanedSettings == null) {
			orphanedSettings = new StationSettings();
		}

		return orphanedSettings;
	}

	public SimpleInventory getCarried() {
		return carried;
	}

	public boolean canFeedNow() {
		return feedCooldown <= 0;
	}

	public boolean canCullNow() {
		return cullCooldown <= 0;
	}

	public void startFeedCooldown() {
		feedCooldown = getSettings().breedIntervalTicks;
	}

	public void startCullCooldown() {
		cullCooldown = getSettings().cullIntervalTicks;
	}

	/** The station is responsible for summoning replacements, so natural despawning must not apply. */
	@Override
	public boolean canImmediatelyDespawn(double distanceSquared) {
		return false;
	}

	@Override
	protected void dropInventory() {
		super.dropInventory();
		ItemScatterer.spawn(Mc.world(this), this, carried);
	}

	@Nullable
	@Override
	protected SoundEvent getAmbientSound() {
		return null;
	}

	/**
	 * A rancher wearing somebody else's face should not answer in the vanilla villager's voice, so
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

		//? if >=1.20.5 {
		/* nbt.put(CARRIED_KEY, carried.toNbtList(getRegistryManager())); */
		//?} else {
		nbt.put(CARRIED_KEY, carried.toNbtList());
		//?}

		NbtCompound disguise = getDisguise();

		if (!disguise.isEmpty()) {
			nbt.put(DISGUISE_KEY, disguise);
		}
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);

		if (nbt.contains(STATION_KEY, Mc.NBT_COMPOUND)) {
			//? if >=1.20.5 {
			/* stationPos = NbtHelper.toBlockPos(nbt, STATION_KEY).orElseGet(() ->
					new BlockPos(BlockPos.ofFloored(getX(), getY(), getZ()))); */
			//?} else {
			stationPos = NbtHelper.toBlockPos(nbt.getCompound(STATION_KEY));
			//?}
		}

		//? if >=1.20.5 {
		/* carried.readNbtList(nbt.getList(CARRIED_KEY, Mc.NBT_COMPOUND), getRegistryManager()); */
		//?} else {
		carried.readNbtList(nbt.getList(CARRIED_KEY, Mc.NBT_COMPOUND));
		//?}

		if (nbt.contains(DISGUISE_KEY, Mc.NBT_COMPOUND)) {
			dataTracker.set(DISGUISE, nbt.getCompound(DISGUISE_KEY));
		}
	}
}
