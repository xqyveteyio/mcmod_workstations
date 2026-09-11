package dev.keyboard.workstations.work;

import dev.keyboard.workstations.WorkstationsMod;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.List;

/**
 * A look a worker can be given: the base skin, and the overlay carrying its hat. Both are laid out
 * for the villager model rather than for a player skin, and the base must leave the hat and hat rim
 * boxes blank for the overlay to fill.
 *
 * <p>Which look a worker wears is one of its station's settings, so the lists live here beside the
 * settings rather than with the renderers: the server has to know how many there are to keep a
 * chosen one in range, even though only the client ever loads the files.
 *
 * <p>What is saved and sent is the position in the list, so entries may be appended but never
 * reordered: shuffling them would quietly redress every station already built.
 */
public record WorkerSkin(String id, Identifier texture, Identifier hatTexture) {
	public static final List<WorkerSkin> RANCHER = List.of(
			of("preset_1", "rancher", "rancher_hat"),
			of("scarecrow", "rancher_1", "rancher_hat_1"));

	/** The farmer's own look is vanilla's plains farmer, flattened out of the villager layers. */
	public static final List<WorkerSkin> FARMER = List.of(
			of("villager", "villager_farmer", "villager_farmer_hat"),
			of("preset_1", "farmer", "farmer_hat"));

	/**
	 * The lumberjack's own look is a jungle villager in a leatherworker's apron under a
	 * fisherman's hat, flattened out of the villager layers the same way. Its second preset is
	 * still the farmer's files, waiting on a pair of its own. Entries may be appended but the
	 * first two must stay, or every station already built would change clothes.
	 */
	public static final List<WorkerSkin> LUMBERJACK = List.of(
			of("villager", "villager_lumberjack", "villager_lumberjack_hat"),
			of("preset_1", "farmer", "farmer_hat"));

	private static WorkerSkin of(String id, String texture, String hatTexture) {
		return new WorkerSkin(id, file(texture), file(hatTexture));
	}

	private static Identifier file(String name) {
		return WorkstationsMod.id("textures/entity/" + name + ".png");
	}

	/** Clamped rather than checked, so a save naming a look this build dropped still draws. */
	public static WorkerSkin get(List<WorkerSkin> skins, int index) {
		return skins.get(MathHelper.clamp(index, 0, skins.size() - 1));
	}

	/** The names the setting cycles through, which are also what its translations are keyed on. */
	public static List<String> ids(List<WorkerSkin> skins) {
		return skins.stream().map(WorkerSkin::id).toList();
	}
}
