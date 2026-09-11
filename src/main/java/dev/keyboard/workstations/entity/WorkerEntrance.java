package dev.keyboard.workstations.entity;

import dev.keyboard.workstations.Mc;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
//? if >=1.17 {
import net.minecraft.util.math.random.Random;
//?} else {
/* import java.util.Random; */
//?}
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * The show a worker puts on as it turns up for work, and as it leaves.
 *
 * <p>A worker that blinked into being beside a freshly placed station read as a glitch rather than
 * as the station doing its job, so it now arrives by some route it could plausibly have taken: it
 * drops out of open sky where there is sky to drop from, digs its way up through the ground where
 * there is not, and where it has neither it simply appears, in a shower of sparks that at least
 * admits as much.
 *
 * <p>Only the choice and the timing belong to the server. The bursts are played by each client off
 * a single entity status byte, the trick vanilla uses to make a wolf shake itself dry, so they cost
 * no packet of their own. How deep a digger is buried cannot be sent that way, because a status
 * only reaches players already being sent the worker: it is tracked data instead, so it travels
 * with the worker itself and the first frame anybody sees is already underground. That depth is
 * only ever read to start the climb off, though, the client running the rest of it out on its own
 * ticks.
 *
 * <p>Deliberately not saved. A worker whose chunk unloads mid entrance is simply on duty when the
 * chunk comes back, which is the right answer anyway: the show is for whoever was watching, and by
 * then nobody was.
 */
public final class WorkerEntrance {
	/** How a worker gets to its post the first time. */
	public enum Style {
		/** Out of open sky, landing on its feet. */
		FALL,
		/** Up through the ground it is about to stand on. */
		DIG,
		/** Neither sky above nor ground below, so there is nothing to arrive out of. */
		SPARK
	}

	/** How far above its post a falling worker starts, in blocks. Rather over a second of drop. */
	public static final int FALL_HEIGHT = 24;

	/** Vanilla's own entity statuses stop at 63, so ours can never be mistaken for one. */
	private static final byte SPARK_STATUS = 65;
	private static final byte LAND_STATUS = 66;
	private static final byte LEAVE_STATUS = 67;

	/** How long digging up out of the ground takes, in ticks. */
	private static final int DIG_TICKS = 60;
	/** How deep the worker is drawn as it starts digging, in blocks. Buries a villager twice over. */
	private static final double DIG_DEPTH = 3.0;
	/** How long the sparks hold the worker still, in ticks. */
	private static final int SPARK_TICKS = 10;
	/**
	 * Ticks to wait before setting off a burst.
	 *
	 * <p>An entity status only goes to players already being sent the entity itself, and on the
	 * tick a worker is spawned that is nobody: the trackers are brought up to date at the top of
	 * the following tick. Going off straight away would play to an empty house.
	 */
	private static final int ANNOUNCE_DELAY = 2;
	/** Ceiling on a fall, in ticks, for a worker that finds something to hang in on the way down. */
	private static final int FALL_LIMIT = 200;
	private static final int SPARK_COUNT = 50;
	private static final int LANDING_PUFF = 12;

	/** Reads the worker's synced burial, in ticks left to climb. Zero once it is above ground. */
	private final IntSupplier buried;
	/** Writes the same, which only the server has any business doing. */
	private final IntConsumer setBuried;

	/** The entrance still owed, on the server. {@code null} once the worker is on duty. */
	@Nullable
	private Style arriving;
	/** Ticks since the worker was put in the world, on the server. */
	private int waited;
	/** Ticks of climb left, as the client is drawing it once it has taken the count over. */
	private int climbLeft;
	/** Whether the client has taken the count over from the synced one. */
	private boolean climbing;

	/**
	 * @param buried    reads the burial the worker has synced to its clients
	 * @param setBuried writes it, for the server to count down
	 */
	public WorkerEntrance(IntSupplier buried, IntConsumer setBuried) {
		this.buried = buried;
		this.setBuried = setBuried;
	}

	/**
	 * Which entrance suits the spot the worker is about to stand in.
	 *
	 * <p>Open sky is asked about in two senses here, because they disagree. Sky light reaches a post
	 * under a glass roof at full strength, glass costing light nothing at all, so the light alone
	 * sends the worker up to drop from a sky it cannot get back down out of, and it lands on the
	 * roof. Light says whether the spot is out of doors; only collision says whether there is a
	 * shaft to fall down.
	 */
	public static Style styleFor(World world, BlockPos post) {
		if (world.isSkyVisibleAllowingSea(post) && isDropClear(world, post)) {
			return Style.FALL;
		}

		return world.isAir(post.down()) ? Style.SPARK : Style.DIG;
	}

	/**
	 * Whether a worker could fall the whole way from where it would be put in to where it belongs.
	 *
	 * <p>Asked of collision rather than of what each block is. Being fallen through is the only
	 * thing being asked of them, so whether they would stop a body is the only property that
	 * decides it, and glass, its panes, iron bars, barriers and whatever a mod adds in the same
	 * spirit are all covered without any of them being named. Torches, ladders and long grass go on
	 * being fallen past, as they should.
	 *
	 * <p>One block higher than the drop, because the worker is put in with its feet at the top of
	 * the shaft and its head above that.
	 */
	private static boolean isDropClear(World world, BlockPos post) {
		BlockPos.Mutable cursor = new BlockPos.Mutable();

		for (int above = 1; above <= FALL_HEIGHT + 1; above++) {
			cursor.set(post.getX(), post.getY() + above, post.getZ());

			if (!world.getBlockState(cursor).getCollisionShape(world, cursor).isEmpty()) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Set by the station before the worker goes into the world, so its first tick already knows.
	 *
	 * <p>A digger is buried here rather than on that first tick, so that the depth goes out with
	 * the worker instead of chasing it: told to bury itself afterwards, it would stand in the open
	 * for the round trip and only then drop through the floor.
	 */
	public void begin(Style style) {
		arriving = style;
		waited = 0;
		setBuried.accept(style == Style.DIG ? DIG_TICKS : 0);
	}

	/** Whether the worker is still making its entrance, and so has no business starting work. */
	public boolean isArriving() {
		return arriving != null;
	}

	/** Whether the worker is underground, and so has no business wearing a name tag. */
	public boolean isBuried() {
		return depth() > 0;
	}

	/**
	 * How far below its feet to draw the worker while it digs its way up, in blocks.
	 *
	 * <p>The count moves a step per tick, so the frame's own fraction of a tick is taken off it to
	 * keep the climb smooth however fast the game is being drawn.
	 */
	public double sink(float tickDelta) {
		int left = depth();
		return left <= 0 ? 0.0 : DIG_DEPTH * Math.max(left - tickDelta, 0.0F) / DIG_TICKS;
	}

	/** Driven from the worker's own tick on both sides: the server times it, the client shows it. */
	public void tick(MobEntity worker) {
		if (Mc.world(worker).isClient()) {
			climb(worker);
			return;
		}

		if (arriving == null) {
			return;
		}

		waited++;

		switch (arriving) {
			case FALL -> fall(worker);
			case DIG -> dig();
			case SPARK -> spark(worker);
		}
	}

	/**
	 * Recognises the statuses this class sends.
	 *
	 * @return whether the status was one of ours, so the worker knows whether to keep looking
	 */
	public boolean handleStatus(MobEntity worker, byte status) {
		switch (status) {
			case SPARK_STATUS, LEAVE_STATUS -> sparkle(worker);
			case LAND_STATUS -> land(worker);
			default -> {
				return false;
			}
		}

		return true;
	}

	/** Sends a worker off in a shower of sparks. Called on the server; the sparks are the clients'. */
	public static void leave(MobEntity worker) {
		Mc.world(worker).sendEntityStatus(worker, LEAVE_STATUS);
		Mc.discard(worker);
	}

	/**
	 * The drop is the whole effect, so it is over when the worker is standing on something. The
	 * fall damage is written off every tick rather than caught on landing, because whether a worker
	 * can be hurt at all is a station setting and this must not depend on how it is set.
	 */
	private void fall(MobEntity worker) {
		worker.fallDistance = 0.0F;

		if (waited < ANNOUNCE_DELAY) {
			return;
		}

		// Water counts as having arrived. A worker that came down in a pond floats rather than
		// landing, and would otherwise sit out the whole ceiling with its work suspended.
		if (!worker.isOnGround() && !worker.isTouchingWater() && waited < FALL_LIMIT) {
			return;
		}

		Mc.world(worker).sendEntityStatus(worker, LAND_STATUS);
		arriving = null;
	}

	/** The climb, counted down here and synced, so every client draws the same worker at the same depth. */
	private void dig() {
		int left = Math.max(buried.getAsInt() - 1, 0);
		setBuried.accept(left);

		if (left <= 0) {
			arriving = null;
		}
	}

	/** Nothing to do but wait for the burst to go off and for it to have finished going off. */
	private void spark(MobEntity worker) {
		if (waited == ANNOUNCE_DELAY) {
			Mc.world(worker).sendEntityStatus(worker, SPARK_STATUS);
		}

		if (waited >= ANNOUNCE_DELAY + SPARK_TICKS) {
			arriving = null;
		}
	}

	/** The count the drawing goes by: the client's own once it has one, the synced one until then. */
	private int depth() {
		return climbing ? climbLeft : buried.getAsInt();
	}

	/**
	 * The client's own account of the climb, and the dirt the worker throws while it makes it.
	 *
	 * <p>The synced count is picked up once, to start whoever has just come into view off at the
	 * right depth, and run out locally from there. Following it tick by tick instead would peg the
	 * worker's height to the moment each update happened to arrive, which lands anywhere within a
	 * frame and reads as a shudder rather than a climb.
	 */
	private void climb(MobEntity worker) {
		int synced = buried.getAsInt();

		if (!climbing) {
			if (synced <= 0) {
				return;
			}

			climbLeft = synced;
			climbing = true;
		} else if (synced <= 0) {
			// Up, as far as the server is concerned. Snap rather than leave it half sunk.
			climbLeft = 0;
		} else if (climbLeft > 0) {
			climbLeft--;
		}

		int left = climbLeft;

		if (left <= 0) {
			return;
		}

		World world = Mc.world(worker);
		Random random = worker.getRandom();
		// The worker only looks buried: it stands on the floor throughout, so the block under it is
		// the one it is supposedly clawing through.
		BlockState under = world.getBlockState(worker.getBlockPos().down());
		BlockState spoil = under.isAir() ? Blocks.DIRT.getDefaultState() : under;
		double x = worker.getX();
		double y = worker.getY();
		double z = worker.getZ();

		for (int i = 0; i < 4; i++) {
			world.addParticle(new BlockStateParticleEffect(ParticleTypes.BLOCK, spoil), x, y, z,
					random.nextDouble() * 2.0 - 1.0, random.nextDouble() * 4.0, random.nextDouble() * 2.0 - 1.0);
			world.addParticle(new BlockStateParticleEffect(ParticleTypes.FALLING_DUST, spoil), x, y, z,
					(random.nextDouble() - 0.5) * 0.5, random.nextDouble() * 0.5, (random.nextDouble() - 0.5) * 0.5);
		}

		// Every other tick: one scrape per tick for three seconds would be a drill, not digging.
		if (left % 2 == 0) {
			world.playSound(x, y, z, BlockSoundGroup.GRAVEL.getHitSound(), SoundCategory.BLOCKS, 0.2F,
					random.nextFloat() + 0.5F, false);
		}
	}

	/** The burst that stands in for an entrance, and doubles as the way every worker leaves. */
	private static void sparkle(MobEntity worker) {
		World world = Mc.world(worker);
		Random random = worker.getRandom();
		double x = worker.getX();
		double y = worker.getBodyY(0.5);
		double z = worker.getZ();

		world.playSound(x, y, z, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.NEUTRAL, 0.6F, 1.0F, false);

		for (int i = 0; i < SPARK_COUNT; i++) {
			world.addParticle(ParticleTypes.FIREWORK, x, y, z,
					(random.nextDouble() - 0.5) * 0.5, (random.nextDouble() - 0.5) * 0.5,
					(random.nextDouble() - 0.5) * 0.5);
		}

		world.addParticle(ParticleTypes.EXPLOSION, x, y, z, 0.0, 0.0, 0.0);
	}

	/** The thump at the end of a drop, kicking up dust around the worker's boots. */
	private static void land(MobEntity worker) {
		World world = Mc.world(worker);
		Random random = worker.getRandom();
		double x = worker.getX();
		double y = worker.getY();
		double z = worker.getZ();

		world.playSound(x, y, z, SoundEvents.ENTITY_GENERIC_BIG_FALL, SoundCategory.NEUTRAL, 0.6F, 1.0F, false);

		for (int i = 0; i < LANDING_PUFF; i++) {
			world.addParticle(ParticleTypes.CLOUD, x, y, z,
					(random.nextDouble() - 0.5) * 0.4, random.nextDouble() * 0.1, (random.nextDouble() - 0.5) * 0.4);
		}
	}
}
