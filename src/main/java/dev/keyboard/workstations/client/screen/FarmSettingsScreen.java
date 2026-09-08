package dev.keyboard.workstations.client.screen;

import dev.keyboard.workstations.WorkstationsMod;
import dev.keyboard.workstations.client.network.StationNetworkingClient;
import dev.keyboard.workstations.work.FarmSettings;
import dev.keyboard.workstations.work.SeedMix;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.function.Consumer;

public class FarmSettingsScreen extends WorkerSettingsScreen<FarmSettings> {
    private final List<Item> palette;

    public FarmSettingsScreen(BlockPos pos, FarmSettings settings, List<Item> palette) {
        super(new ItemStack(WorkstationsMod.FARM_BLOCK).getName(), pos, settings);
        this.palette = palette;
    }

    @Override
    protected void save() {
        StationNetworkingClient.saveFarmSettings(pos, settings);
    }

    private void surveyAndLeave() {
        save();
        StationNetworkingClient.rescanPlots(pos);
        dismiss();
    }

    @Override
    protected void addExtraRows(String category, Consumer<Row> add) {
        if (FarmSettings.FIELD.equals(category)) {
            add.accept(new Row(new TranslatableText("config.keyboard_workstations.rescan_plots"),
                    new ButtonWidget(0, 0, CONTROL_WIDTH, CONTROL_HEIGHT,
                            new TranslatableText("config.keyboard_workstations.rescan_plots.action"),
                            button -> surveyAndLeave()),
                    new TranslatableText("config.keyboard_workstations.rescan_plots.tooltip")));
            return;
        }

        if (!FarmSettings.SEEDS.equals(category)) {
            return;
        }

        if (palette.isEmpty()) {
            add.accept(new Row(new TranslatableText("config.keyboard_workstations.no_seeds")));
            return;
        }

        for (Item seed : palette) {
            add.accept(new Row(seed.getName().copy(), new SeedSlider(seed),
                    new TranslatableText("config.keyboard_workstations.seed_weight.tooltip")));
        }
    }

    private class SeedSlider extends SliderWidget {
        private final Item seed;

        SeedSlider(Item seed) {
            super(0, 0, CONTROL_WIDTH, CONTROL_HEIGHT, new LiteralText(""),
                    fraction(settings.seedMix.weight(seed)));
            this.seed = seed;
            updateMessage();
        }

        private static double fraction(int weight) {
            return (double) (weight - SeedMix.MIN_WEIGHT) / (SeedMix.MAX_WEIGHT - SeedMix.MIN_WEIGHT);
        }

        @Override
        protected void updateMessage() {
            setMessage(new TranslatableText("config.keyboard_workstations.seed_weight",
                    settings.seedMix.weight(seed), settings.seedMix.share(seed, palette)));
        }

        @Override
        protected void applyValue() {
            settings.seedMix.setWeight(seed, (int) Math.round(
                    MathHelper.lerp(value, SeedMix.MIN_WEIGHT, SeedMix.MAX_WEIGHT)));
        }
    }
}
