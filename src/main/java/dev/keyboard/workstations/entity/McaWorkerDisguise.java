package dev.keyboard.workstations.entity;

import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.relationship.Gender;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;

/**
 * Rolls a Minecraft Comes Alive villager for a worker to be dressed as.
 *
 * <p>The villager is built, stripped of the one thing that makes it look the way it does, and
 * dropped. It is never put in the world and never ticks: all that is kept is the data MCA reads a
 * villager's body out of, which travels on the worker and is handed to a stand in on each client.
 *
 * <p>Built by hand rather than through MCA's own {@code VillagerFactory}, which names the villager
 * it builds, and naming one on the server writes it into MCA's family tree. That tree is saved with
 * the world, so a station would leave behind a relative for every worker it ever summoned.
 *
 * <p>None of this goes through an API MCA offers, because it offers none for this, so anything here
 * is free to throw and this class makes no attempt to catch it. {@link WorkerDisguise} does the
 * catching, out where it can also survive this class failing to load at all.
 */
final class McaWorkerDisguise {
	private McaWorkerDisguise() {
	}

	/**
	 * The key MCA files a villager's gender under, which is the one field of a disguise anything
	 * outside the rendering has to read: the two halves of MCA's cast do not share a voice.
	 */
	private static final String GENDER_KEY = "gender";

	static boolean isFemale(NbtCompound disguise) {
		return Gender.byId(disguise.getInt(GENDER_KEY)) == Gender.FEMALE;
	}

	static NbtCompound roll(MobEntity worker) {
		// Gender picks the entity type, and the type's own constructor records it from there.
		VillagerEntityMCA villager = Gender.getRandom().getVillagerType().create(worker.getWorld());

		// Grown up. Age is otherwise rolled too, and a station is no place for a toddler.
		villager.setBreedingAge(0);

		// Skin tone and build are drawn partly from the climate a villager is standing in, so it
		// has to be standing somewhere real rather than at the world origin when they are rolled.
		villager.setPos(worker.getX(), worker.getY(), worker.getZ());
		villager.getGenetics().randomize();

		// Clothes, hair and hair colour. False asks for a villager's roll rather than a player's.
		villager.initializeSkin(false);

		// MCA's own way of lifting a villager's data off it, which it uses to carry a villager
		// across when one turns into a zombie. What it is being carried into is not read.
		return villager.toNbtForConversion(villager.getType());
	}
}
