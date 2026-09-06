package dev.keyboard.breederscarecrow.entity;

import dev.keyboard.breederscarecrow.ModConfig;
import dev.keyboard.breederscarecrow.block.ScarecrowBlockEntity;
import dev.keyboard.breederscarecrow.entity.ai.GateOperator;
import dev.keyboard.breederscarecrow.entity.ai.RancherBrain;
import dev.keyboard.breederscarecrow.entity.ai.RancherNavigation;
import dev.keyboard.breederscarecrow.work.WorkArea;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
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
public class RancherEntity extends PathAwareEntity {
	public static final int CARRY_SLOTS = 8;

	private static final String STATION_KEY = "Station";
	private static final String CARRIED_KEY = "Carried";
	/** Grace period before a rancher whose station is gone gives up, in ticks. */
	private static final int HOMELESS_LIMIT = 200;
	/** How often the state label may be rewritten, in ticks. Four times a second reads fine. */
	private static final int LABEL_INTERVAL = 5;
	/** How far ahead blockers get shouldered aside, in blocks. */
	private static final double SHOVE_RANGE = 1.8;
	/** Velocity added per tick at point blank range, tapering to nothing at {@link #SHOVE_RANGE}. */
	private static final double SHOVE_STRENGTH = 0.07;
	/** Cosine of the cone in front of the rancher that counts as being in the way. */
	private static final double SHOVE_CONE = 0.3;

	private final SimpleInventory carried = new SimpleInventory(CARRY_SLOTS);
	private final RancherBrain brain = new RancherBrain();
	private final GateOperator gates = new GateOperator();
	@Nullable
	private BlockPos stationPos;
	/** Last label pushed to the name tag, so an unchanged state is not resent every tick. */
	@Nullable
	private String stateLabel;
	private int labelCooldown;
	private int homelessTicks;
	private int feedCooldown;
	private int cullCooldown;

	public RancherEntity(EntityType<? extends RancherEntity> type, World world) {
		super(type, world);
		setPersistent();
	}

	public static DefaultAttributeContainer.Builder createRancherAttributes() {
		return MobEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.5)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 4.0);
	}

	/** Gates are only worth opening if paths are allowed to run through them in the first place. */
	@Override
	protected EntityNavigation createNavigation(World world) {
		return new RancherNavigation(this, world);
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

		if (feedCooldown > 0) {
			feedCooldown--;
		}

		if (cullCooldown > 0) {
			cullCooldown--;
		}

		// A rancher outliving its station would keep working an area nobody owns any more.
		if (getStation() == null) {
			if (++homelessTicks > HOMELESS_LIMIT) {
				discard();
			}
		} else {
			homelessTicks = 0;
		}

		brain.tick(this);
		// After the brain, so a gate on a path it just laid down is seen on this very tick.
		gates.tick(this);
		shoveBlockers();
		updateStateLabel();
	}

	/**
	 * Shoulders whatever is in the way aside while walking. Mobs only shove each other once their
	 * hitboxes already overlap, which is too late to stop a cow stood in a doorway from sending the
	 * rancher the long way round, or nowhere at all.
	 *
	 * <p>Only while actually navigating, and only for what is ahead. Jostling the herd while stood
	 * still would keep animals in love from ever reaching each other, so nothing would breed.
	 *
	 * <p>The shove goes sideways rather than straight ahead on purpose: pushing a blocker along the
	 * direction of travel would drive whatever stands in a gateway right through it, which for an
	 * animal means out of the pen. Sideways clears the corridor without ever pushing anything
	 * through the gap the rancher is heading for.
	 */
	private void shoveBlockers() {
		if (!ModConfig.get().shoveBlockers || getNavigation().isIdle()) {
			return;
		}

		Vec3d forward = Vec3d.fromPolar(0.0F, bodyYaw);

		for (Entity other : getWorld().getOtherEntities(this,
				getBoundingBox().expand(SHOVE_RANGE, 0.5, SHOVE_RANGE),
				candidate -> candidate.isPushable() && !(candidate instanceof PlayerEntity))) {
			double dx = other.getX() - getX();
			double dz = other.getZ() - getZ();
			double distance = Math.sqrt(dx * dx + dz * dz);

			if (distance < 1.0E-4 || distance > SHOVE_RANGE) {
				continue;
			}

			if ((forward.x * dx + forward.z * dz) / distance < SHOVE_CONE) {
				continue;
			}

			double sideX = -forward.z;
			double sideZ = forward.x;
			double lean = sideX * dx + sideZ * dz;
			// Pushed towards the side it already leans, so the two never disagree about which way
			// it should go. Dead ahead there is no such side, so its id picks one and sticks to it.
			boolean flip = Math.abs(lean) < 1.0E-3 ? (other.getId() & 1) == 0 : lean < 0.0;

			if (flip) {
				sideX = -sideX;
				sideZ = -sideZ;
			}

			double push = SHOVE_STRENGTH * (1.0 - distance / SHOVE_RANGE);
			other.addVelocity(sideX * push, 0.0, sideZ * push);
			other.velocityModified = true;
		}
	}

	/** A rancher that dies in a gateway must not leave the pen standing open behind it. */
	@Override
	public void remove(RemovalReason reason) {
		if (!getWorld().isClient() && reason.shouldDestroy()) {
			gates.shut(this);
		}

		super.remove(reason);
	}

	/**
	 * Publishes the brain's state as the entity's name, which puts it over the rancher's head with
	 * no client side code at all.
	 *
	 * <p>Throttled, and skipped when the text has not changed, because a name change marks the
	 * entity's tracked data dirty and resends it to every player watching. The countdown in the
	 * label ticks every tick, so writing it straight through would mean a packet per tick.
	 */
	private void updateStateLabel() {
		if (!ModConfig.get().showWorkerState) {
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
	public ScarecrowBlockEntity getStation() {
		if (stationPos == null || !getWorld().isChunkLoaded(stationPos.getX() >> 4, stationPos.getZ() >> 4)) {
			return null;
		}

		return getWorld().getBlockEntity(stationPos) instanceof ScarecrowBlockEntity station ? station : null;
	}

	@Nullable
	public WorkArea getWorkArea() {
		if (stationPos == null) {
			return null;
		}

		ModConfig config = ModConfig.get();
		return new WorkArea(stationPos, config.workRadius, config.workHeight);
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
		feedCooldown = ModConfig.get().breedIntervalTicks;
	}

	public void startCullCooldown() {
		cullCooldown = ModConfig.get().cullIntervalTicks;
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

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_VILLAGER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_VILLAGER_DEATH;
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);

		if (stationPos != null) {
			nbt.put(STATION_KEY, NbtHelper.fromBlockPos(stationPos));
		}

		nbt.put(CARRIED_KEY, carried.toNbtList());
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);

		if (nbt.contains(STATION_KEY, NbtElement.COMPOUND_TYPE)) {
			stationPos = NbtHelper.toBlockPos(nbt.getCompound(STATION_KEY));
		}

		carried.readNbtList(nbt.getList(CARRIED_KEY, NbtElement.COMPOUND_TYPE));
	}
}
