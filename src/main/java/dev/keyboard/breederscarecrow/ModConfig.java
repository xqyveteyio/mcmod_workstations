package dev.keyboard.breederscarecrow;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.MathHelper;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Runtime tunables, stored in {@code config/breeder_scarecrow.json}.
 */
public class ModConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static ModConfig instance;

	/** Maximum number of floor tiles a single pen may contain. */
	public int scanMaxCells = 2048;
	/** Maximum horizontal distance a pen may extend away from the scarecrow. */
	public int scanMaxRadius = 32;
	/**
	 * Fallback interval for re-scanning the pen shape, in ticks. Player block changes near the pen
	 * trigger a scan on the next tick regardless, so this only catches pistons, explosions and the like.
	 */
	public int rescanIntervalTicks = 20;
	/** How often the scarecrow tries to feed animals, in ticks. */
	public int breedIntervalTicks = 60;
	/**
	 * When true the scarecrow only breeds animals as long as matching feed has been put in it by hand,
	 * and every pairing spends two items. When false it breeds for free and needs no feed at all.
	 */
	public boolean requireFeedItems = true;
	/** Animals of one species allowed inside the pen before breeding stops. */
	public int maxAnimalsPerType = 16;
	/** Couples fed per breeding attempt. */
	public int maxPairsPerCycle = 2;
	/** Also feed babies to speed up their growth. Off by default because it eats through feed quickly. */
	public boolean feedBabies = false;
	/** Show the pen highlight without holding a scarecrow. */
	public boolean highlightAlwaysOn = false;

	public static ModConfig get() {
		if (instance == null) {
			instance = load();
		}

		return instance;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(BreederScarecrowMod.MOD_ID + ".json");
	}

	private static ModConfig load() {
		Path path = path();
		ModConfig config = new ModConfig();

		if (Files.isRegularFile(path)) {
			try {
				ModConfig parsed = GSON.fromJson(Files.readString(path), ModConfig.class);

				if (parsed != null) {
					config = parsed;
				}
			} catch (Exception exception) {
				BreederScarecrowMod.LOGGER.warn("Could not read {}, falling back to defaults", path, exception);
			}
		}

		config.clamp();
		config.save();
		return config;
	}

	private void clamp() {
		scanMaxCells = MathHelper.clamp(scanMaxCells, 1, 16384);
		scanMaxRadius = MathHelper.clamp(scanMaxRadius, 1, 128);
		rescanIntervalTicks = MathHelper.clamp(rescanIntervalTicks, 1, 12000);
		breedIntervalTicks = MathHelper.clamp(breedIntervalTicks, 20, 12000);
		maxAnimalsPerType = MathHelper.clamp(maxAnimalsPerType, 2, 512);
		maxPairsPerCycle = MathHelper.clamp(maxPairsPerCycle, 1, 32);
	}

	private void save() {
		try {
			Files.writeString(path(), GSON.toJson(this));
		} catch (Exception exception) {
			BreederScarecrowMod.LOGGER.warn("Could not write {}", path(), exception);
		}
	}
}
