package dev.keyboard.workstations;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Starting values for newly placed stations, and the values the settings screen's reset button
 * restores, stored in {@code config/keyboard_workstations.json}.
 *
 * <p>Ranchers do not read this, with three exceptions noted below. Each station keeps its own
 * {@link dev.keyboard.workstations.work.StationSettings}, copied from here when the block is
 * placed and edited from the block's own screen afterwards. Changing the file only affects
 * stations built from then on, unless an existing station is reset from its settings screen.
 *
 * <p>{@link #requireFeedItems}, {@link #consumeSeeds} and {@link #invulnerable} are the exceptions.
 * They are read straight from here every time they are needed, are not copied into any station and
 * appear on no settings screen, so they hold for every workstation in the world and change the
 * moment the file does.
 *
 * <p>Nothing is range checked here either. A station clamps whatever it is given to the bounds its
 * settings screen offers, which keeps those bounds stated in exactly one place.
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
	/**
	 * Finish an animal off in a single blow rather than hacking away at it. On by default: the
	 * rancher is a machine for turning a pen into drops, and drawing that out only means more time
	 * spent standing over one cow while the rest of the herd goes unattended.
	 */
	public boolean instantKill = true;
	/** Shear anything wearing a coat. The wool lands on the ground and is swept up straight after. */
	public boolean enableShearing = true;
	/**
	 * Milk grown cows into a milk barrel standing against the station. On but idle until a barrel is
	 * there to take it: buckets in the station's own shelves would bury everything else it produces.
	 */
	public boolean enableMilking = true;
	/**
	 * Whether anything is allowed to hurt the worker. On by default; {@code /kill} still works.
	 *
	 * <p>One of the three settings a station cannot override. Whether a worker can be killed decides
	 * whether a station is a machine or something you have to defend, which is a decision about the
	 * whole world rather than about one block.
	 */
	public boolean invulnerable = true;
	/** Horizontal reach of the work area, measured out from the station block. */
	public int workRadius = 8;
	/** How far the work area reaches above and below the station block. */
	public int workHeight = 4;
	/** How often a worker looks around for its next job, in ticks. Both stations read this. */
	public int workIntervalTicks = 10;
	/** Ticks the rancher waits after feeding one animal before feeding another. */
	public int breedIntervalTicks = 60;
	/** Ticks the rancher waits after finishing one animal off before starting on the next. */
	public int cullIntervalTicks = 60;
	/**
	 * When true feeding spends matching items out of the station inventory, so the ranch only runs
	 * as long as you keep it stocked. When false the rancher breeds for free.
	 *
	 * <p>One of the three settings that is only ever read from here. See the note on {@link #consumeSeeds}.
	 */
	public boolean requireFeedItems = true;
	/** Adults of one species kept as breeding stock. Anything above this gets slaughtered. */
	public int keepAdultsPerType = 8;
	/** Animals of one species allowed inside the area before breeding pauses. */
	public int maxAnimalsPerType = 12;
	/** Also feed babies to speed up their growth. Off by default because it eats through feed quickly. */
	public boolean feedBabies = false;
	/** Play the eating sound every time an animal is fed. Off by default since a busy ranch gets noisy. */
	public boolean playFeedSound = false;
	/** Ticks the station waits before replacing a rancher that died or went missing. */
	public int workerRespawnTicks = 200;
	/** Show the work area highlight without holding the station block. Purely local to your client. */
	public boolean highlightAlwaysOn = false;
	/**
	 * Print the rancher's current state over its head. Off by default: it is there for working out
	 * why a station is idle, and a ranch that is running fine reads better without a name tag
	 * rewriting itself over the worker's head all day.
	 */
	public boolean showWorkerState = false;

	/** Re-hoe plots that got trampled back to dirt, so a field survives being walked over. */
	public boolean enableTilling = true;
	/** Sow bare plots with whatever seeds the farm station has been given. */
	public boolean enableSowing = true;
	/** Harvest grown crops. The produce lands on the ground and is swept up straight after. */
	public boolean enableHarvesting = true;
	/**
	 * Pick melons and pumpkins that grew off a stem. Off by default, because unlike the rest of
	 * the field this reaches past the plots the station registered and onto whatever ground the
	 * fruit happened to grow on.
	 */
	public boolean harvestGourds = false;
	/** Pick mushrooms anywhere in the work area. Off by default for the same reason. */
	public boolean harvestMushrooms = false;
	/**
	 * When true sowing spends seeds out of the farm station, so the field only runs as long as you
	 * keep it stocked. When false what is in the container only says which seeds the farmer is
	 * allowed to plant, and the field keeps going once you have shown it the mix.
	 *
	 * <p>This, {@link #requireFeedItems} and {@link #invulnerable} are the settings a station cannot
	 * override, and the only ones read from here while the game runs rather than copied out when a
	 * block is placed. They decide whether a workstation is something you keep supplied and defend
	 * or something that runs on nothing and cannot be touched, which is the sort of decision a pack
	 * settles once for the whole world rather than leaving to be turned off at each block by
	 * whoever owns it.
	 */
	public boolean consumeSeeds = true;
	/** Ticks the farmer waits between one plot and the next. */
	public int farmIntervalTicks = 10;

	public static ModConfig get() {
		if (instance == null) {
			instance = load();
		}

		return instance;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(WorkstationsMod.MOD_ID + ".json");
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
				WorkstationsMod.LOGGER.warn("Could not read {}, falling back to defaults", path, exception);
			}
		}

		config.save();
		return config;
	}

	public void save() {
		try {
			Files.writeString(path(), GSON.toJson(this));
		} catch (Exception exception) {
			WorkstationsMod.LOGGER.warn("Could not write {}", path(), exception);
		}
	}
}
