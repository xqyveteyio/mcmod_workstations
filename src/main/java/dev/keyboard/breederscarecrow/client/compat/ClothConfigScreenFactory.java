package dev.keyboard.breederscarecrow.client.compat;

import dev.keyboard.breederscarecrow.ModConfig;
import dev.keyboard.breederscarecrow.client.HighlightState;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Builds the Cloth Config screen. Every entry writes straight into the live {@link ModConfig}
 * instance, so changes apply without a restart, and the file is written when the screen is saved.
 */
public final class ClothConfigScreenFactory {
	private ClothConfigScreenFactory() {
	}

	public static Screen create(Screen parent) {
		ModConfig config = ModConfig.get();
		ModConfig defaults = new ModConfig();

		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(text("title"))
				.setSavingRunnable(() -> {
					config.save();
					HighlightState.applyConfig();
				});

		ConfigEntryBuilder entries = builder.entryBuilder();
		ConfigCategory breeding = builder.getOrCreateCategory(text("category.breeding"));
		ConfigCategory culling = builder.getOrCreateCategory(text("category.culling"));
		ConfigCategory area = builder.getOrCreateCategory(text("category.area"));
		ConfigCategory display = builder.getOrCreateCategory(text("category.display"));

		breeding.addEntry(entries.startBooleanToggle(text("enable_breeding"), config.enableBreeding)
				.setDefaultValue(defaults.enableBreeding)
				.setTooltip(text("enable_breeding.tooltip"))
				.setSaveConsumer(value -> config.enableBreeding = value)
				.build());

		breeding.addEntry(entries.startBooleanToggle(text("require_feed_items"), config.requireFeedItems)
				.setDefaultValue(defaults.requireFeedItems)
				.setTooltip(text("require_feed_items.tooltip"))
				.setSaveConsumer(value -> config.requireFeedItems = value)
				.build());

		breeding.addEntry(entries.startIntSlider(text("breed_interval_ticks"), config.breedIntervalTicks, 1, 600)
				.setDefaultValue(defaults.breedIntervalTicks)
				.setTooltip(text("breed_interval_ticks.tooltip"))
				.setSaveConsumer(value -> config.breedIntervalTicks = value)
				.build());

		breeding.addEntry(entries.startIntSlider(text("max_animals_per_type"), config.maxAnimalsPerType, 2, 64)
				.setDefaultValue(defaults.maxAnimalsPerType)
				.setTooltip(text("max_animals_per_type.tooltip"))
				.setSaveConsumer(value -> config.maxAnimalsPerType = value)
				.build());

		breeding.addEntry(entries.startBooleanToggle(text("feed_babies"), config.feedBabies)
				.setDefaultValue(defaults.feedBabies)
				.setTooltip(text("feed_babies.tooltip"))
				.setSaveConsumer(value -> config.feedBabies = value)
				.build());

		breeding.addEntry(entries.startBooleanToggle(text("play_feed_sound"), config.playFeedSound)
				.setDefaultValue(defaults.playFeedSound)
				.setTooltip(text("play_feed_sound.tooltip"))
				.setSaveConsumer(value -> config.playFeedSound = value)
				.build());

		culling.addEntry(entries.startBooleanToggle(text("enable_culling"), config.enableCulling)
				.setDefaultValue(defaults.enableCulling)
				.setTooltip(text("enable_culling.tooltip"))
				.setSaveConsumer(value -> config.enableCulling = value)
				.build());

		culling.addEntry(entries.startIntSlider(text("keep_adults_per_type"), config.keepAdultsPerType, 2, 32)
				.setDefaultValue(defaults.keepAdultsPerType)
				.setTooltip(text("keep_adults_per_type.tooltip"))
				.setSaveConsumer(value -> config.keepAdultsPerType = value)
				.build());

		culling.addEntry(entries.startIntSlider(text("cull_interval_ticks"), config.cullIntervalTicks, 1, 1200)
				.setDefaultValue(defaults.cullIntervalTicks)
				.setTooltip(text("cull_interval_ticks.tooltip"))
				.setSaveConsumer(value -> config.cullIntervalTicks = value)
				.build());

		area.addEntry(entries.startIntSlider(text("work_radius"), config.workRadius, 1, 64)
				.setDefaultValue(defaults.workRadius)
				.setTooltip(text("work_radius.tooltip"))
				.setSaveConsumer(value -> config.workRadius = value)
				.build());

		area.addEntry(entries.startIntSlider(text("work_height"), config.workHeight, 1, 32)
				.setDefaultValue(defaults.workHeight)
				.setTooltip(text("work_height.tooltip"))
				.setSaveConsumer(value -> config.workHeight = value)
				.build());

		area.addEntry(entries.startIntSlider(text("work_interval_ticks"), config.workIntervalTicks, 1, 200)
				.setDefaultValue(defaults.workIntervalTicks)
				.setTooltip(text("work_interval_ticks.tooltip"))
				.setSaveConsumer(value -> config.workIntervalTicks = value)
				.build());

		area.addEntry(entries.startIntSlider(text("worker_respawn_ticks"), config.workerRespawnTicks, 20, 2400)
				.setDefaultValue(defaults.workerRespawnTicks)
				.setTooltip(text("worker_respawn_ticks.tooltip"))
				.setSaveConsumer(value -> config.workerRespawnTicks = value)
				.build());

		area.addEntry(entries.startBooleanToggle(text("open_fence_gates"), config.openFenceGates)
				.setDefaultValue(defaults.openFenceGates)
				.setTooltip(text("open_fence_gates.tooltip"))
				.setSaveConsumer(value -> config.openFenceGates = value)
				.build());

		area.addEntry(entries.startBooleanToggle(text("shove_blockers"), config.shoveBlockers)
				.setDefaultValue(defaults.shoveBlockers)
				.setTooltip(text("shove_blockers.tooltip"))
				.setSaveConsumer(value -> config.shoveBlockers = value)
				.build());

		display.addEntry(entries.startBooleanToggle(text("highlight_always_on"), config.highlightAlwaysOn)
				.setDefaultValue(defaults.highlightAlwaysOn)
				.setTooltip(text("highlight_always_on.tooltip"))
				.setSaveConsumer(value -> config.highlightAlwaysOn = value)
				.build());

		display.addEntry(entries.startBooleanToggle(text("show_worker_state"), config.showWorkerState)
				.setDefaultValue(defaults.showWorkerState)
				.setTooltip(text("show_worker_state.tooltip"))
				.setSaveConsumer(value -> config.showWorkerState = value)
				.build());

		return builder.build();
	}

	private static Text text(String key) {
		return Text.translatable("config.breeder_scarecrow." + key);
	}
}
