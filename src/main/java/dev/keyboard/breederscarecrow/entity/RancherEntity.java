package dev.keyboard.breederscarecrow.entity;

import dev.keyboard.breederscarecrow.ModConfig;
import dev.keyboard.breederscarecrow.block.ScarecrowBlockEntity;
import dev.keyboard.breederscarecrow.entity.ai.GateOperator;
import dev.keyboard.breederscarecrow.entity.ai.RancherBrain;
import dev.keyboard.breederscarecrow.entity.ai.RancherNavigation;
import dev.keyboard.breederscarecrow.work.WorkArea;
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
		updateStateLabel();
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
