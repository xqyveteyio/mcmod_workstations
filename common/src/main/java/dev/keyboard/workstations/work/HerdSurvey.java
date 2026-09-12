package dev.keyboard.workstations.work;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.Shearable;
import net.minecraft.entity.passive.HorseBaseEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * One look at every animal inside a work area, grouped by species, plus the rules deciding what
 * the rancher should do about them. Kept separate from the AI so the "who gets fed, who gets
 * slaughtered" question can be reasoned about on its own.
 */
public final class HerdSurvey {
	/** How far vanilla's {@code AnimalMateGoal} looks for a partner once an animal is in love. */
	private static final double MATE_SEARCH_RANGE = 8.0;

	private final Map<EntityType<?>, Herd> herds = new HashMap<>();

	private HerdSurvey() {
	}

	public static HerdSurvey of(ServerWorld world, WorkArea area) {
		HerdSurvey survey = new HerdSurvey();

		for (AnimalEntity animal : world.getEntitiesByClass(AnimalEntity.class, area.getBox(),
				animal -> animal.isAlive() && !animal.removed)) {
			Herd herd = survey.herds.computeIfAbsent(animal.getType(), type -> new Herd());

			if (animal.isBaby()) {
				herd.babies.add(animal);
			} else {
				herd.adults.add(animal);
			}
		}

		return survey;
	}

	/**
	 * One pairing's worth of feeding, in the order it should be carried out. {@code second} is null
	 * when a partner is already in love and all that is missing is one more animal brought up to it.
	 *
	 * <p>Feed is planned in whole pairings rather than one animal at a time because a portion spent
	 * on its own is a portion wasted. Being in love lasts 600 ticks, and re-deciding who to feed
	 * after the first portion used to lose the pairing outright: the two could drift past the eight
	 * block mate search range in that time, at which point the herd offers no candidates at all and
	 * the animal falls out of love having bred nothing.
	 */
	public record FeedPlan(AnimalEntity first, @Nullable AnimalEntity second) {
		public int portions() {
			return second == null ? 1 : 2;
		}
	}

	/**
	 * Pairings worth starting, each one already matched up so the rancher can commit to serving
	 * both halves without asking again.
	 *
	 * <p>Pairings are only ever counted in twos, so three cows standing together yield one plan and
	 * the odd one out waits for the next round rather than falling in love with nobody left over.
	 */
	public List<FeedPlan> feedPlans(StationSettings config) {
		List<FeedPlan> plans = new ArrayList<>();

		for (Herd herd : herds.values()) {
			if (herd.total() >= config.maxAnimalsPerType) {
				continue;
			}

			// A herd still being trimmed back to its breeding stock is left alone. Feeding an animal
			// that is already queued for slaughter spends feed on nothing.
			if (herd.adults.size() > config.keepAdultsPerType) {
				continue;
			}

			List<AnimalEntity> courting = new ArrayList<>();
			List<AnimalEntity> ready = new ArrayList<>();

			for (AnimalEntity animal : herd.adults) {
				if (!isPairable(animal)) {
					continue;
				}

				if (animal.isInLove()) {
					courting.add(animal);
				} else if (animal.getBreedingAge() == 0 && animal.canEat()) {
					// Same gate vanilla uses when a player hand feeds an animal.
					ready.add(animal);
				}
			}

			int portions = Math.min(evenShare(courting.size(), ready.size()),
					roomForPairings(config, herd, courting.size()));

			// An animal already in love is burning through its timer, so bringing it a partner comes
			// before starting any pairing from scratch.
			for (AnimalEntity waiting : courting) {
				if (portions < 1) {
					break;
				}

				AnimalEntity mate = nearestWithinRange(waiting, ready);

				if (mate == null) {
					continue;
				}

				ready.remove(mate);
				plans.add(new FeedPlan(mate, null));
				portions--;
			}

			// Then pairings from scratch, closest two first, so the pair being committed to is the
			// one least likely to have wandered apart before the second portion is handed over.
			while (portions >= 2) {
				FeedPlan pair = closestPair(ready);

				if (pair == null) {
					break;
				}

				ready.remove(pair.first());
				ready.remove(pair.second());
				plans.add(pair);
				portions -= 2;
			}
		}

		return plans;
	}

	/** Nearest animal in {@code pool} that vanilla's mate goal could still pair with {@code animal}. */
	@Nullable
	private static AnimalEntity nearestWithinRange(AnimalEntity animal, List<AnimalEntity> pool) {
		AnimalEntity best = null;
		double bestDistance = MATE_SEARCH_RANGE * MATE_SEARCH_RANGE;

		for (AnimalEntity candidate : pool) {
			if (candidate == animal) {
				continue;
			}

			double distance = candidate.squaredDistanceTo(animal);

			if (distance <= bestDistance) {
				best = candidate;
				bestDistance = distance;
			}
		}

		return best;
	}

	/** The two closest animals in {@code pool}, or null when no two are near enough to pair. */
	@Nullable
	private static FeedPlan closestPair(List<AnimalEntity> pool) {
		FeedPlan best = null;
		double bestDistance = MATE_SEARCH_RANGE * MATE_SEARCH_RANGE;

		for (int first = 0; first < pool.size(); first++) {
			for (int second = first + 1; second < pool.size(); second++) {
				double distance = pool.get(first).squaredDistanceTo(pool.get(second));

				if (distance <= bestDistance) {
					best = new FeedPlan(pool.get(first), pool.get(second));
					bestDistance = distance;
				}
			}
		}

		return best;
	}

	/** How many of {@code ready} can be fed while leaving every animal in love with a partner. */
	private static int evenShare(int courting, int ready) {
		return Math.max(0, (courting + ready) / 2 * 2 - courting);
	}

	/** Every pairing adds one baby, so a herd near its limit only starts as many as still fit. */
	private static int roomForPairings(StationSettings config, Herd herd, int courting) {
		return Math.max(0, (config.maxAnimalsPerType - herd.total()) * 2 - courting);
	}

	/** Adults above the configured breeding stock, oldest pairings spared so the herd keeps going. */
	public List<AnimalEntity> cullCandidates(StationSettings config) {
		List<AnimalEntity> candidates = new ArrayList<>();

		for (Herd herd : herds.values()) {
			int excess = herd.adults.size() - config.keepAdultsPerType;

			if (excess <= 0) {
				continue;
			}

			List<AnimalEntity> cullable = new ArrayList<>();

			for (AnimalEntity animal : herd.adults) {
				if (!isProtected(animal)) {
					cullable.add(animal);
				}
			}

			// Animals currently in love are last in line, so an in progress pairing is not cut short.
			cullable.sort(Comparator.comparing(AnimalEntity::isInLove));
			candidates.addAll(cullable.subList(0, Math.min(excess, cullable.size())));
		}

		return candidates;
	}

	public List<AnimalEntity> babyCandidates() {
		List<AnimalEntity> candidates = new ArrayList<>();

		for (Herd herd : herds.values()) {
			candidates.addAll(herd.babies);
		}

		return candidates;
	}

	/**
	 * Anything wearing a coat right now. Vanilla's own check covers the rest: a lamb and a sheep
	 * already shorn both answer no, so a flock only offers up what shears would actually work on.
	 */
	public List<AnimalEntity> shearCandidates() {
		return matching(animal -> animal instanceof Shearable shearable && shearable.isShearable());
	}

	/**
	 * Grown cows. Vanilla puts no limit on milking, so nothing here says whether one is "ready":
	 * how often it happens is the rancher's business, and it gives each cow one turn per round.
	 */
	public List<AnimalEntity> milkCandidates() {
		return matching(animal -> animal instanceof CowEntity && !animal.isBaby());
	}

	/**
	 * Every animal in the area passing {@code test}, protected ones left out. Shearing a mooshroom
	 * turns it into a cow for good, so even the harmless looking jobs go by the same rule as
	 * slaughter: an animal someone named or tamed is not the rancher's to touch.
	 */
	private List<AnimalEntity> matching(Predicate<AnimalEntity> test) {
		List<AnimalEntity> candidates = new ArrayList<>();

		for (Herd herd : herds.values()) {
			for (AnimalEntity animal : herd.adults) {
				if (!isProtected(animal) && test.test(animal)) {
					candidates.add(animal);
				}
			}

			for (AnimalEntity animal : herd.babies) {
				if (!isProtected(animal) && test.test(animal)) {
					candidates.add(animal);
				}
			}
		}

		return candidates;
	}

	/**
	 * {@link AnimalEntity#canBreedWith} only answers once both partners are already in love, so the
	 * extra conditions its subclasses add have to be checked before any feed is spent.
	 */
	private static boolean isPairable(AnimalEntity animal) {
		if (animal instanceof TameableEntity tameable) {
			return tameable.isTamed() && !tameable.isInSittingPose();
		}

		if (animal instanceof HorseBaseEntity horse) {
			return horse.isTame() && !horse.hasPassengers() && !horse.hasVehicle()
					&& horse.getHealth() >= horse.getMaxHealth();
		}

		return true;
	}

	/** Pets and anything a player bothered to name are never slaughtered. */
	private static boolean isProtected(AnimalEntity animal) {
		return animal.hasCustomName() || animal instanceof TameableEntity || animal instanceof HorseBaseEntity;
	}

	private static final class Herd {
		private final List<AnimalEntity> adults = new ArrayList<>();
		private final List<AnimalEntity> babies = new ArrayList<>();

		private int total() {
			return adults.size() + babies.size();
		}
	}
}
