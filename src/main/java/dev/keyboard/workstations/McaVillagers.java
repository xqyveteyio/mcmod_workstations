package dev.keyboard.workstations;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The handful of Minecraft Comes Alive calls a worker's borrowed looks are made of, found by name
 * at runtime rather than linked against.
 *
 * <p>Found rather than imported because there is no one name to import. MCA now publishes 1.20.1 as
 * a single combined download carrying a copy of itself for every loader, and keeps those copies
 * from colliding by renaming their packages apart: the villager that is
 * {@code net.mca.entity.VillagerEntityMCA} in the plain Fabric build is
 * {@code fabric.net.mca.entity.VillagerEntityMCA} in the combined one. Nothing compiled against
 * either name can find the other, so both are looked for instead.
 *
 * <p>None of this costs anything to draw with. A worker's villager is built once and kept, so every
 * call here runs at most once per worker, and the frames in between touch only the ordinary
 * villager that villager already is.
 */
public final class McaVillagers {
	/** Where MCA keeps its own classes: the plain build's layout first, then the combined build's. */
	private static final String[] ROOTS = {"net.mca.", "fabric.net.mca."};

	/**
	 * The key MCA files a villager's gender under, which is the one field of a disguise anything
	 * outside the rendering has to read: the two halves of MCA's cast do not share a voice.
	 */
	private static final String GENDER_KEY = "gender";

	@Nullable private static final Calls CALLS = find();

	private McaVillagers() {
	}

	/**
	 * Everything reached for, resolved in one go.
	 *
	 * <p>All of it or none of it. A bridge short one call is of no use, and finding that out
	 * partway through a roll would leave a worker half dressed rather than plainly dressed.
	 */
	private record Calls(Object male, Object female, Method randomGender, Method genderById,
			Method villagerType, Method genetics, Method randomize, Method initializeSkin,
			Method toNbt, Method readNbt) {
	}

	@Nullable
	private static Calls find() {
		if (!McaSupport.available()) {
			return null;
		}

		for (String root : ROOTS) {
			try {
				return findUnder(root);
			} catch (ReflectiveOperationException | LinkageError elsewhere) {
				// Not the layout this build uses. Try the other before giving up on it.
			}
		}

		WorkstationsMod.LOGGER.warn(
				"Minecraft Comes Alive is installed, but its villagers are not laid out either way this mod"
						+ " knows to look ({}), so workers will keep their own look. Nothing else about either"
						+ " mod is affected.",
				String.join(" or ", ROOTS));
		return null;
	}

	private static Calls findUnder(String root) throws ReflectiveOperationException {
		Class<?> gender = Class.forName(root + "entity.ai.relationship.Gender");
		// The interface rather than the villager: everything borrowed but the genetics is a default
		// method on it, and looking there says plainly that none of this is villager specific.
		Class<?> villager = Class.forName(root + "entity.VillagerLike");
		Class<?> genetics = Class.forName(root + "entity.ai.Genetics");

		return new Calls(
				gender.getField("MALE").get(null),
				gender.getField("FEMALE").get(null),
				gender.getMethod("getRandom"),
				gender.getMethod("byId", int.class),
				gender.getMethod("getVillagerType"),
				villager.getMethod("getGenetics"),
				genetics.getMethod("randomize"),
				villager.getMethod("initializeSkin", boolean.class),
				villager.getMethod("toNbtForConversion", EntityType.class),
				villager.getMethod("readNbtForConversion", EntityType.class, NbtCompound.class));
	}

	/** Whether MCA is installed, of a version worth asking, and laid out somewhere recognised. */
	public static boolean usable() {
		return CALLS != null;
	}

	/**
	 * A new villager of MCA's own choosing between its two, with nothing rolled onto it yet.
	 *
	 * <p>Built by hand rather than through MCA's own {@code VillagerFactory}, which names the
	 * villager it builds, and naming one on the server writes it into MCA's family tree. That tree
	 * is saved with the world, so a station would leave behind a relative for every worker it ever
	 * summoned.
	 */
	public static VillagerEntity createRandom(World world) {
		// Gender picks the entity type, and the type's own constructor records it from there.
		return create(world, invoke(calls().randomGender(), null));
	}

	/**
	 * A new villager to pour a finished disguise into.
	 *
	 * <p>Which of the two types is built makes no difference. Gender is part of what is being
	 * poured in, and MCA's model reads it back off the villager rather than off its type.
	 */
	public static VillagerEntity createStandIn(World world) {
		return create(world, calls().male());
	}

	/**
	 * Rolls skin tone and build. Drawn partly from the climate the villager is standing in, so it
	 * has to be standing somewhere real rather than at the world origin when this is called.
	 */
	public static void randomizeGenetics(VillagerEntity villager) {
		invoke(calls().randomize(), invoke(calls().genetics(), villager));
	}

	/** Rolls clothes, hair and hair colour. */
	public static void rollSkin(VillagerEntity villager) {
		// False asks for a villager's roll rather than a player's.
		invoke(calls().initializeSkin(), villager, false);
	}

	/**
	 * Everything that makes this villager look the way it does, lifted off it.
	 *
	 * <p>MCA's own way of carrying a villager across when one turns into a zombie. What it is being
	 * carried into is not read.
	 */
	public static NbtCompound wearingOf(VillagerEntity villager) {
		return (NbtCompound) invoke(calls().toNbt(), villager, villager.getType());
	}

	/** The other half of {@link #wearingOf}, pouring a disguise into a villager built to hold it. */
	public static void wear(VillagerEntity villager, NbtCompound disguise) {
		invoke(calls().readNbt(), villager, villager.getType(), disguise);
	}

	/** Which of MCA's two voices a worker wearing this disguise speaks in. */
	public static boolean isFemale(NbtCompound disguise) {
		return invoke(calls().genderById(), null, disguise.getInt(GENDER_KEY)) == calls().female();
	}

	private static VillagerEntity create(World world, Object gender) {
		EntityType<?> type = (EntityType<?>) invoke(calls().villagerType(), gender);
		return (VillagerEntity) type.create(world);
	}

	private static Calls calls() {
		if (CALLS == null) {
			throw new IllegalStateException("No Minecraft Comes Alive villager to borrow anything from");
		}

		return CALLS;
	}

	/**
	 * Calls one of the resolved methods, turning both reflection's own failures and whatever MCA
	 * threw underneath into the one unchecked failure the callers' safety nets are watching for.
	 */
	@Nullable
	private static Object invoke(Method method, @Nullable Object on, Object... arguments) {
		try {
			return method.invoke(on, arguments);
		} catch (IllegalAccessException | InvocationTargetException failure) {
			throw new IllegalStateException("Minecraft Comes Alive could not " + method.getName(), failure);
		}
	}
}
