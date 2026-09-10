package dev.keyboard.workstations.entity.ai;

import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What every worker carries, and when it is time to do something about it. How full the pack is,
 * whether it should be emptied, and which loose drops are sitting underfoot are the same question
 * for a rancher, a farmer and a lumberjack, and none of them belong to any one brain.
 *
 * <p>What to do with a named drop — walking to it, writing it off, the cooldown that keeps an
 * unreachable one from being asked forever — stays with the brain that already owns that state.
 * This only answers the inventory and the short look around the worker's feet.
 */
public final class WorkerPack {
	/**
	 * How near a drop has to be to be grabbed mid-phase rather than left for the sweep, in
	 * blocks. Collect reach is two and a half: that is "standing on it". A broken block throws
	 * its items a little way, and a log higher up the trunk lands a step or two from the stump,
	 * so this wants to be larger than reach. Five is double that — enough for the scatter from
	 * the last swing, and still only a plot away, not a walk across the field.
	 */
	public static final double UNDERFOOT_RADIUS = 5.0;
	public static final double UNDERFOOT_RADIUS_SQUARED = UNDERFOOT_RADIUS * UNDERFOOT_RADIUS;

	/**
	 * Occupied slots at which the worker walks back and empties the pack. Packs are eight
	 * slots, and waiting for every one of them used to mean the last swings of a phase had
	 * nowhere to put what they produced, so the drops sat until a sweep and sometimes despawned.
	 *
	 * <p>Six leaves two slots of slack for whatever the last swing just threw — a crop's produce
	 * and its seed, a log and a sapling, meat and hide — without going home after every other
	 * plot. Four would empty the pack twice as often; a worker that spends its time commuting
	 * is worse than one that carries a full load.
	 */
	public static final int DEPOSIT_SLOTS = 6;

	private WorkerPack() {
	}

	/** How many slots of {@code pack} hold anything, which is what "full" actually means here. */
	public static int count(SimpleInventory pack) {
		int used = 0;

		for (int slot = 0; slot < pack.size(); slot++) {
			if (!pack.getStack(slot).isEmpty()) {
				used++;
			}
		}

		return used;
	}

	/** Whether every slot is occupied, so nothing more can be picked up that needs a new slot. */
	public static boolean isFull(SimpleInventory pack) {
		return count(pack) == pack.size();
	}

	/**
	 * Whether the pack has built up enough to be worth walking back. A completely full pack is
	 * included, but the point is to go earlier than that: see {@link #DEPOSIT_SLOTS}.
	 */
	public static boolean shouldDeposit(SimpleInventory pack) {
		return count(pack) >= DEPOSIT_SLOTS;
	}

	/**
	 * Loose drops inside {@code area} that {@code pack} can still take. The caller still has to
	 * path them and honour its own cooldown; this is only the filter every brain was writing out
	 * by hand.
	 */
	public static List<ItemEntity> looseIn(ServerWorld world, Box area, SimpleInventory pack) {
		return world.getEntitiesByClass(ItemEntity.class, area,
				item -> item.isAlive() && !item.cannotPickup() && pack.canInsert(item.getStack()));
	}

	/**
	 * Of those, the ones sitting within {@link #UNDERFOOT_RADIUS} of the worker, nearest first.
	 *
	 * <p>A drop across the field is the sweep's problem. This is only what the last swing is
	 * likely to have just produced, so a phase can pocket it and carry on rather than leaving a
	 * pile until the rotation comes around to collecting.
	 */
	public static List<ItemEntity> underfoot(MobEntity worker, ServerWorld world, Box area, SimpleInventory pack) {
		List<ItemEntity> nearby = new ArrayList<>();

		for (ItemEntity drop : looseIn(world, worker.getBoundingBox().expand(UNDERFOOT_RADIUS), pack)) {
			if (worker.squaredDistanceTo(drop) <= UNDERFOOT_RADIUS_SQUARED && area.contains(drop.getPos())) {
				nearby.add(drop);
			}
		}

		nearby.sort(Comparator.comparingDouble(worker::squaredDistanceTo));
		return nearby;
	}
}
