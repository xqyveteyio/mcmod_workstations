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

	/** Whether the rancher feeds animals to breed them. Independent of {@link #enableCulling}. */
	public boolean enableBreeding = true;
	/** Whether the rancher slaughters surplus animals. Independent of {@link #enableBreeding}. */
	public boolean enableCulling = true;
	/** Let the rancher work fence gates, shutting them behind itself so the herd stays put. */
	public boolean openFenceGates = true;
	/** Let the rancher shoulder livestock out of its way instead of pathing around it. */
	public boolean shoveBlockers = true;
	/** Horizontal reach of the work area, measured out from the station block. */
	public int workRadius = 8;
	/** How far the work area reaches above and below the station block. */
	public int workHeight = 4;
	/** How often the rancher looks around for its next job, in ticks. */
	public int workIntervalTicks = 20;
	/** Ticks the rancher waits after feeding one animal before feeding another. */
	public int breedIntervalTicks = 60;
	/** Ticks the rancher waits after finishing one animal off before starting on the next. */
	public int cullIntervalTicks = 100;
	/**
	 * When true feeding spends matching items out of the station inventory, so the ranch only runs
	 * as long as you keep it stocked. When false the rancher breeds for free.
	 */
	public boolean requireFeedItems = true;
	/** Adults of one species kept as breeding stock. Anything above this gets slaughtered. */
	public int keepAdultsPerType = 4;
	/** Animals of one species allowed inside the area before breeding pauses. */
	public int maxAnimalsPerType = 16;
	/** Also feed babies to speed up their growth. Off by default because it eats through feed quickly. */
	public boolean feedBabies = false;
	/** Play the eating sound every time an animal is fed. Off by default since a busy ranch gets noisy. */
	public boolean playFeedSound = false;
	/** Ticks the station waits before replacing a rancher that died or went missing. */
	public int workerRespawnTicks = 200;
	/** Show the work area highlight without holding the station block. */
	public boolean highlightAlwaysOn = false;
	/** Print the rancher's current state over its head, for working out why it is idle. */
	public boolean showWorkerState = true;

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

		config.save();
		return config;
	}

	private void clamp() {
		workRadius = MathHelper.clamp(workRadius, 1, 64);
		workHeight = MathHelper.clamp(workHeight, 1, 32);
		workIntervalTicks = MathHelper.clamp(workIntervalTicks, 1, 1200);
		breedIntervalTicks = MathHelper.clamp(breedIntervalTicks, 1, 12000);
		cullIntervalTicks = MathHelper.clamp(cullIntervalTicks, 1, 12000);
		keepAdultsPerType = MathHelper.clamp(keepAdultsPerType, 2, 128);
		maxAnimalsPerType = MathHelper.clamp(maxAnimalsPerType, keepAdultsPerType, 512);
		workerRespawnTicks = MathHelper.clamp(workerRespawnTicks, 20, 24000);
	}

	/** Clamps the current values back into range and writes them out. */
	public void save() {
		clamp();

		try {
			Files.writeString(path(), GSON.toJson(this));
		} catch (Exception exception) {
			BreederScarecrowMod.LOGGER.warn("Could not write {}", path(), exception);
		}
	}
}
