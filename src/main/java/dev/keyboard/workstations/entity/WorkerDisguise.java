package dev.keyboard.workstations.entity;

import dev.keyboard.workstations.Mc;

import dev.keyboard.workstations.McaVillagers;
import dev.keyboard.workstations.WorkstationsMod;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Where a worker's borrowed looks are settled, when another mod is around to lend them.
 *
 * <p>The roll happens on the server, once, and is then carried on the worker: kept in its saved
 * data so it survives a restart and sent to everyone watching so a worker looks the same to every
 * player. Rolling it on each client instead would be less work, but the same worker would then be
 * a different villager to everyone looking at it, and a different one again after a rejoin.
 *
 * <p>Every call into {@link McaVillagers} is netted, because none of what it reaches for is an API
 * MCA offers. A version that passes {@link dev.keyboard.workstations.McaSupport}'s check is still
 * no promise that what is reached for behaves as it did, and a worker in its own skin is a much
 * better answer to that than a crash.
 */
public final class WorkerDisguise {
	/** Set once the MCA side has failed even once, which puts every worker back in its own skin. */
	private static boolean mcaGivenUp;

	private WorkerDisguise() {
	}

	/** One of the noises a worker makes, in the two voices MCA records each of them in. */
	public enum Voice {
		HURT("hurt"),
		/** MCA gives its villagers no death rattle of their own and screams over the end instead. */
		DEATH("scream");

		private final String name;

		Voice(String name) {
			this.name = name;
		}
	}

	/** Whether there is anything to be gained by asking for a roll, worth checking first. */
	public static boolean canRoll() {
		return McaVillagers.usable() && !mcaGivenUp;
	}

	/**
	 * The sound a worker in this disguise should make, or {@code null} to leave it its own voice.
	 *
	 * <p>Looked up by name rather than off MCA's own list of sounds, which is held in a type
	 * belonging to a third mod that this one would otherwise have to be built against.
	 */
	@Nullable
	public static SoundEvent voice(NbtCompound disguise, Voice voice) {
		if (!canRoll() || disguise.isEmpty()) {
			return null;
		}

		boolean female;

		try {
			female = McaVillagers.isFemale(disguise);
		} catch (Throwable failure) {
			mcaGivenUp = true;
			WorkstationsMod.LOGGER.warn("Could not read a worker's borrowed looks, so it keeps its own voice", failure);
			return null;
		}

		return Mc.soundEvent(
				WorkstationsMod.id("mca", "villager." + (female ? "female" : "male") + "." + voice.name));
	}

	/**
	 * Rolls a Minecraft Comes Alive villager and hands back everything that makes it look the way
	 * it does, or an empty compound if that could not be managed.
	 *
	 * <p>The villager is built, read off, and dropped. It is never put in the world and never
	 * ticks: all that is kept is the data MCA reads a villager's body out of, which travels on the
	 * worker and is handed to a stand in on each client.
	 */
	public static NbtCompound roll(MobEntity worker) {
		try {
			VillagerEntity villager = McaVillagers.createRandom(Mc.world(worker));

			// Grown up. Age is otherwise rolled too, and a station is no place for a toddler.
			villager.setBreedingAge(0);

			// Standing where the worker stands, because some of what is about to be rolled is drawn
			// from the climate the villager finds itself in.
			villager.setPos(worker.getX(), worker.getY(), worker.getZ());
			McaVillagers.randomizeGenetics(villager);
			McaVillagers.rollSkin(villager);

			return McaVillagers.wearingOf(villager);
		} catch (Throwable failure) {
			mcaGivenUp = true;
			WorkstationsMod.LOGGER.warn(
					"Could not dress workers as Minecraft Comes Alive villagers, so they will keep their own look",
					failure);
			return new NbtCompound();
		}
	}
}
