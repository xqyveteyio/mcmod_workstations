package dev.keyboard.workstations.entity;

import dev.keyboard.workstations.McaSupport;
import dev.keyboard.workstations.WorkstationsMod;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
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
 * <p>This class names no class from the mod being borrowed from, which is the whole point of it. A
 * class is not loaded until a line naming it actually runs, so {@link McaSupport} is what keeps the
 * game from going looking for classes that are not installed, or installed in a version that no
 * longer has them. The safety net has to live out here too, rather than inside the bridge it
 * guards: a version that passes the check is still no promise that the classes compiled against are
 * the ones present, and if they are not, the failure is not something the bridge throws but the
 * bridge itself failing to load, which a net cast inside it would miss.
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
		return McaSupport.available() && !mcaGivenUp;
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
			female = McaWorkerDisguise.isFemale(disguise);
		} catch (Throwable failure) {
			mcaGivenUp = true;
			WorkstationsMod.LOGGER.warn("Could not read a worker's borrowed looks, so it keeps its own voice", failure);
			return null;
		}

		return Registries.SOUND_EVENT.get(
				new Identifier("mca", "villager." + (female ? "female" : "male") + "." + voice.name));
	}

	/**
	 * Rolls a Minecraft Comes Alive villager and hands back everything that makes it look the way
	 * it does, or an empty compound if that could not be managed.
	 */
	public static NbtCompound roll(MobEntity worker) {
		try {
			return McaWorkerDisguise.roll(worker);
		} catch (Throwable failure) {
			mcaGivenUp = true;
			WorkstationsMod.LOGGER.warn("""
					Could not dress workers as Minecraft Comes Alive villagers, so they will keep their own look. \
					If MCA was installed as its combined "universal" download, swap it for the Fabric one: the \
					combined build renames every class inside it, so nothing outside it can find them.""", failure);
			return new NbtCompound();
		}
	}
}
