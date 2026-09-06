package dev.keyboard.breederscarecrow.breeding;

import dev.keyboard.breederscarecrow.ModConfig;
import dev.keyboard.breederscarecrow.pen.PenRegion;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Feeds breeding items from the scarecrow inventory to animals penned in around it.
 */
public final class BreedingManager {
	private BreedingManager() {
	}

	public static void run(ServerWorld world, PenRegion region, Inventory inventory) {
		Box searchBox = region.getSearchBox();

		if (searchBox == null) {
			return;
		}

		ModConfig config = ModConfig.get();
		List<AnimalEntity> animals = world.getEntitiesByClass(AnimalEntity.class, searchBox,
				animal -> animal.isAlive() && region.containsEntity(animal));

		if (animals.isEmpty()) {
			return;
		}

		Map<EntityType<?>, List<AnimalEntity>> bySpecies = new HashMap<>();

		for (AnimalEntity animal : animals) {
			bySpecies.computeIfAbsent(animal.getType(), type -> new ArrayList<>()).add(animal);
		}

		int pairsFed = 0;

		for (List<AnimalEntity> herd : bySpecies.values()) {
			if (config.feedBabies) {
				feedBabies(world, herd, inventory, config.requireFeedItems);
			}

			if (herd.size() >= config.maxAnimalsPerType || pairsFed >= config.maxPairsPerCycle) {
				continue;
			}

			pairsFed += matchCouples(world, herd, inventory, config.maxPairsPerCycle - pairsFed,
					config.requireFeedItems);
		}
	}

	private static int matchCouples(ServerWorld world, List<AnimalEntity> herd, Inventory inventory, int pairBudget,
			boolean requireFeed) {
		List<AnimalEntity> ready = new ArrayList<>();

		for (AnimalEntity animal : herd) {
			// Same gate vanilla uses when a player hand feeds an animal: grown up, off cooldown, not already in love.
			if (!animal.isBaby() && animal.getBreedingAge() == 0 && animal.canEat() && !animal.isInLove()) {
				ready.add(animal);
			}
		}

		int pairsFed = 0;

		for (int index = 0; index + 1 < ready.size() && pairsFed < pairBudget; index += 2) {
			AnimalEntity first = ready.get(index);
			AnimalEntity second = ready.get(index + 1);

			if (!canPair(first, second)) {
				continue;
			}

			if (requireFeed) {
				int firstSlot = findFoodSlot(inventory, first, -1);

				if (firstSlot < 0) {
					return pairsFed;
				}

				// The second lookup has to know one item of the first slot is already spoken for.
				int secondSlot = findFoodSlot(inventory, second, firstSlot);

				if (secondSlot < 0) {
					return pairsFed;
				}

				consume(world, inventory, firstSlot, first);
				consume(world, inventory, secondSlot, second);
			} else {
				celebrate(world, first);
				celebrate(world, second);
			}

			first.lovePlayer(null);
			second.lovePlayer(null);
			pairsFed++;
		}

		return pairsFed;
	}

	/**
	 * {@link AnimalEntity#canBreedWith} only answers once both partners are already in love, so the
	 * extra conditions its subclasses add have to be checked here before any food is spent.
	 */
	private static boolean canPair(AnimalEntity first, AnimalEntity second) {
		if (first == second || first.getClass() != second.getClass()) {
			return false;
		}

		if (first instanceof TameableEntity || second instanceof TameableEntity) {
			return isTameReady(first) && isTameReady(second);
		}

		if (first instanceof AbstractHorseEntity || second instanceof AbstractHorseEntity) {
			return isHorseReady(first) && isHorseReady(second);
		}

		return true;
	}

	private static boolean isTameReady(AnimalEntity animal) {
		return animal instanceof TameableEntity tameable && tameable.isTamed() && !tameable.isInSittingPose();
	}

	private static boolean isHorseReady(AnimalEntity animal) {
		return animal instanceof AbstractHorseEntity horse && horse.isTame() && !horse.hasPassengers()
				&& !horse.hasVehicle() && horse.getHealth() >= horse.getMaxHealth();
	}

	private static void feedBabies(ServerWorld world, List<AnimalEntity> herd, Inventory inventory, boolean requireFeed) {
		for (AnimalEntity animal : herd) {
			if (!animal.isBaby()) {
				continue;
			}

			if (requireFeed) {
				int slot = findFoodSlot(inventory, animal, -1);

				if (slot < 0) {
					return;
				}

				consume(world, inventory, slot, animal);
			} else {
				celebrate(world, animal);
			}

			animal.growUp(PassiveEntity.toGrowUpAge(-animal.getBreedingAge()), true);
		}
	}

	/**
	 * @param reservedSlot slot that already has one item spoken for in this cycle, or {@code -1}
	 */
	private static int findFoodSlot(Inventory inventory, AnimalEntity animal, int reservedSlot) {
		for (int slot = 0; slot < inventory.size(); slot++) {
			ItemStack stack = inventory.getStack(slot);
			int available = stack.getCount() - (slot == reservedSlot ? 1 : 0);

			if (available > 0 && animal.isBreedingItem(stack)) {
				return slot;
			}
		}

		return -1;
	}

	private static void consume(ServerWorld world, Inventory inventory, int slot, AnimalEntity animal) {
		ItemStack eaten = inventory.removeStack(slot, 1);
		Item remainder = eaten.getItem().getRecipeRemainder();

		if (remainder != null) {
			returnRemainder(world, inventory, animal, new ItemStack(remainder));
		}

		celebrate(world, animal);
	}

	private static void celebrate(ServerWorld world, AnimalEntity animal) {
		world.playSound(null, animal.getBlockPos(), SoundEvents.ENTITY_GENERIC_EAT, SoundCategory.NEUTRAL, 0.5F,
				world.random.nextFloat() * 0.2F + 0.9F);
		Vec3d center = animal.getPos().add(0.0, animal.getHeight() * 0.5, 0.0);
		world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, center.x, center.y, center.z, 4, 0.3, 0.3, 0.3, 0.0);
	}

	private static void returnRemainder(ServerWorld world, Inventory inventory, AnimalEntity animal, ItemStack remainder) {
		for (int slot = 0; slot < inventory.size() && !remainder.isEmpty(); slot++) {
			ItemStack stack = inventory.getStack(slot);

			if (stack.isEmpty()) {
				inventory.setStack(slot, remainder.copy());
				return;
			}

			if (ItemStack.canCombine(stack, remainder) && stack.getCount() < stack.getMaxCount()) {
				stack.increment(1);
				return;
			}
		}

		world.spawnEntity(new ItemEntity(world, animal.getX(), animal.getY() + 0.5, animal.getZ(), remainder));
	}
}
