package dev.keyboard.workstations;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

/**
 * Whether the installed Minecraft Comes Alive is one this mod knows how to borrow a look from.
 *
 * <p>Installed is not the same as usable. The few MCA methods reached for are found by name at
 * runtime, so a build that has since renamed or dropped them does not fail politely: it fails deep
 * inside a render or a spawn, which is a poor place to find out. Checking the version up front
 * turns that into a line in the log, a word to each player as they join, and workers that simply
 * keep their own faces, which is what they look like without MCA anyway.
 *
 * <p>The range is deliberately no wider than what has actually been tried. Everything borrowed has
 * held still across the 7.5 and 7.6 lines; a major bump is a rewrite, and guessing that it kept the
 * same shape is exactly the guess that ends in a crash report.
 *
 * <p>Only the version is read here. Whether the classes behind that version are laid out somewhere
 * findable is {@link McaVillagers}' question, and a build can fail either test on its own: a 9.0
 * laid out exactly as expected is still turned away by this one.
 */
public final class McaSupport {
	private static final String MCA_ID = "mca";

	/** The only MCA line this mod is built to understand. */
	private static final int MAJOR = 7;
	/** Oldest release of that line worth trying, matching what the borrowed calls first appeared in. */
	private static final int OLDEST_MINOR = 5;

	private static final boolean AVAILABLE;
	/** The installed version, but only when it is one that has been turned away. */
	@Nullable private static final String REFUSED;

	static {
		String version = FabricLoader.getInstance().getModContainer(MCA_ID)
				.map(ModContainer::getMetadata)
				.map(metadata -> metadata.getVersion().getFriendlyString())
				.orElse(null);

		AVAILABLE = version != null && supports(version);
		REFUSED = version != null && !AVAILABLE ? version : null;

		if (REFUSED != null) {
			WorkstationsMod.LOGGER.warn(
					"Minecraft Comes Alive {} is not a version this mod knows how to borrow villager looks from"
							+ " (anything from {} up to but not including {}), so workers will keep their own"
							+ " look. Nothing else about either mod is affected.",
					REFUSED, oldestSupported(), firstUnsupported());
		}
	}

	private McaSupport() {
	}

	/** Whether MCA is installed and near enough to what this mod was built against to be worth asking. */
	public static boolean available() {
		return AVAILABLE;
	}

	/**
	 * A word for the player about an installed MCA that had to be turned away, or {@code null} when
	 * there is nothing to say.
	 *
	 * <p>The log already carries the same news, but nobody goes looking there to find out why their
	 * workers came out plain, and the answer is to swap a file rather than to change anything in the
	 * game, so it is worth saying somewhere it will actually be read.
	 */
	@Nullable
	public static Text refusalNotice() {
		if (REFUSED == null) {
			return null;
		}

		return Text.translatable("message.keyboard_workstations.mca_version",
				REFUSED, oldestSupported(), firstUnsupported()).formatted(Formatting.YELLOW);
	}

	private static String oldestSupported() {
		return MAJOR + "." + OLDEST_MINOR;
	}

	private static String firstUnsupported() {
		return (MAJOR + 1) + ".0";
	}

	/**
	 * Reads the major and minor out of a version string.
	 *
	 * <p>MCA numbers its builds like {@code 7.5.21+1.20.1+fabric}, which is not a version any
	 * stricter reader will take: the second {@code +} is not something the semantic version rules
	 * allow, so asking the loader to compare it would quietly answer no to everything. Only the part
	 * in front of the first {@code +} or {@code -} carries the number, and that part is plain enough
	 * to read here.
	 */
	private static boolean supports(String version) {
		String[] parts = version.split("[+-]", 2)[0].split("\\.");

		if (parts.length < 2) {
			return false;
		}

		try {
			return Integer.parseInt(parts[0]) == MAJOR && Integer.parseInt(parts[1]) >= OLDEST_MINOR;
		} catch (NumberFormatException unreadable) {
			return false;
		}
	}
}
