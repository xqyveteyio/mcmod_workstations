package dev.keyboard.workstations.client.screen;

import dev.keyboard.workstations.ModConfig;
import dev.keyboard.workstations.client.HighlightState;
import dev.keyboard.workstations.client.network.StationNetworkingClient;
import dev.keyboard.workstations.work.SeedMix;
import dev.keyboard.workstations.work.SettingOption;
import dev.keyboard.workstations.work.WorkerSettings;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.List;
import java.util.function.Consumer;

public abstract class WorkerSettingsScreen<S extends WorkerSettings<S>> extends Screen {
    protected static final int CONTROL_WIDTH = 100;
    protected static final int CONTROL_HEIGHT = 20;
    private static final int ROW_WIDTH = 320;
    private static final int BUTTON_ROW_WIDTH = 308;
    private static final int BUTTON_GAP = 4;
    private static final int TABS_TOP = 6;
    private static final int LIST_GAP = 8;
    private static final int FOOTER_HEIGHT = 40;

    protected final BlockPos pos;
    protected final S settings;
    private final List<String> categories;
    private String activeCategory;

    protected WorkerSettingsScreen(Text title, BlockPos pos, S settings) {
        super(title);
        this.pos = pos;
        this.settings = settings;
        this.categories = settings.categories();
        this.activeCategory = categories.get(0);
    }

    protected abstract void save();

    protected void addExtraRows(String category, Consumer<Row> add) {
    }

    /**
     * The ratio rows a planting station's mix tab is made of: one slider per item in the palette,
     * or a line saying the container is empty.
     *
     * <p>Shared so a farm and a lumber station cannot drift apart on how a weight is shown or when
     * the other rows' shares are refreshed. The keys are the caller's because a sapling tab should
     * not talk about seeds.
     */
    protected void addMixRows(SeedMix mix, List<Item> palette, String emptyKey, String tooltipKey,
            Consumer<Row> add) {
        if (palette.isEmpty()) {
            add.accept(new Row(new TranslatableText(emptyKey)));
            return;
        }

        for (Item item : palette) {
            add.accept(new Row(new ItemStack(item).getName(), new MixSlider(mix, item, palette),
                    new TranslatableText(tooltipKey)));
        }
    }

    @Override
    protected void init() {
        int tabWidth = (BUTTON_ROW_WIDTH - BUTTON_GAP * (categories.size() - 1)) / categories.size();
        int x = width / 2 - BUTTON_ROW_WIDTH / 2;

        for (String category : categories) {
            boolean selected = category.equals(activeCategory);
            Text label = new TranslatableText("config.keyboard_workstations." + category);

            addButton(new ButtonWidget(x, TABS_TOP, tabWidth, CONTROL_HEIGHT,
                    label,
                    button -> showCategory(category)));
            x += tabWidth + BUTTON_GAP;
        }

        addChild(new OptionList(activeCategory, listTop(), height - FOOTER_HEIGHT));

        int footerWidth = (BUTTON_ROW_WIDTH - BUTTON_GAP * 2) / 3;
        int footerX = width / 2 - BUTTON_ROW_WIDTH / 2;
        int footerY = height - FOOTER_HEIGHT + 10;

        addButton(new ButtonWidget(footerX, footerY, footerWidth, CONTROL_HEIGHT,
                new TranslatableText("config.keyboard_workstations.worker_recall"),
                button -> StationNetworkingClient.recallWorker(pos)));

        addButton(new ButtonWidget(footerX + footerWidth + BUTTON_GAP, footerY, footerWidth, CONTROL_HEIGHT,
                new TranslatableText("config.keyboard_workstations.reset"),
                button -> resetToDefaults()));

        addButton(new ButtonWidget(footerX + (footerWidth + BUTTON_GAP) * 2, footerY, footerWidth, CONTROL_HEIGHT,
                new TranslatableText("gui.done"), button -> onClose()));
    }

    private int listTop() {
        return TABS_TOP + CONTROL_HEIGHT + LIST_GAP;
    }

    private void rebuild() {
        children.clear();
        buttons.clear();
        init();
    }

    private void showCategory(String category) {
        if (!category.equals(activeCategory)) {
            activeCategory = category;
            rebuild();
        }
    }

    private void resetToDefaults() {
        settings.copyFrom(settings.shippedDefaults());
        rebuild();
    }

    protected void refresh() {
        rebuild();
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        for (Element child : children) {
            if (!(child instanceof ClickableWidget) && child instanceof Drawable drawable) {
                drawable.render(matrices, mouseX, mouseY, delta);
            }
        }
        for (Element child : children) {
            if (child instanceof ClickableWidget drawable) {
                drawable.render(matrices, mouseX, mouseY, delta);
            }
        }
    }

    @Override
    public void onClose() {
        save();
        super.onClose();
    }

    protected void dismiss() {
        super.onClose();
    }

    protected static Text onOff(boolean value) {
        return value ? new TranslatableText("options.on") : new TranslatableText("options.off");
    }

    private ClickableWidget controlFor(SettingOption<S> option) {
        if (option instanceof SettingOption.Flag<S> flag) {
            return new ButtonWidget(0, 0, CONTROL_WIDTH, CONTROL_HEIGHT,
                    onOff(flag.get(settings)), button -> {
                        flag.set(settings, !flag.get(settings));
                        button.setMessage(onOff(flag.get(settings)));
                    });
        }

        if (option instanceof SettingOption.Choice<S> choice) {
            return new ButtonWidget(0, 0, CONTROL_WIDTH, CONTROL_HEIGHT,
                    new TranslatableText(choice.valueLabelKey(settings)), button -> {
                        choice.next(settings);
                        button.setMessage(new TranslatableText(choice.valueLabelKey(settings)));
                    });
        }

        return new OptionSlider<>((SettingOption.Range<S>) option, settings);
    }

    private ClickableWidget highlightControl() {
        ModConfig config = ModConfig.get();

        return new ButtonWidget(0, 0, CONTROL_WIDTH, CONTROL_HEIGHT,
                onOff(config.highlightAlwaysOn), button -> {
                    config.highlightAlwaysOn = !config.highlightAlwaysOn;
                    config.save();
                    HighlightState.applyConfig();
                    button.setMessage(onOff(config.highlightAlwaysOn));
                });
    }

    protected class Row extends ElementListWidget.Entry<Row> {
        private final Text label;
        private final ClickableWidget control;

        public Row(Text label, ClickableWidget control, Text tooltip) {
            this.label = label;
            this.control = control;
        }

        public Row(Text label) {
            this.label = label;
            this.control = null;
        }

        public List<? extends Element> children() {
            return control == null ? java.util.Collections.emptyList() : java.util.Collections.singletonList(control);
        }

        @Override
        public void render(MatrixStack matrices, int index, int y, int x, int entryWidth, int entryHeight,
                int mouseX, int mouseY, boolean hovered, float tickDelta) {
            textRenderer.draw(matrices, label, x, y + (entryHeight - textRenderer.fontHeight) / 2, 0xFFFFFF);

            if (control != null) {
                control.x = x + entryWidth - CONTROL_WIDTH;
                control.y = y;
                control.render(matrices, mouseX, mouseY, tickDelta);
            }
        }
    }

    private class OptionList extends ElementListWidget<Row> {
        OptionList(String category, int top, int bottom) {
            super(WorkerSettingsScreen.this.client, WorkerSettingsScreen.this.width,
                    WorkerSettingsScreen.this.height, top, bottom, CONTROL_HEIGHT + 5);

            for (SettingOption<S> option : settings.options()) {
                if (option.category().equals(category)) {
                    addEntry(new Row(new TranslatableText(option.labelKey()), controlFor(option),
                            new TranslatableText(option.tooltipKey())));
                }
            }

            addExtraRows(category, this::addEntry);

            if (WorkerSettings.DISPLAY.equals(category)) {
                addEntry(new Row(new TranslatableText("config.keyboard_workstations.highlight_always_on"),
                        highlightControl(),
                        new TranslatableText("config.keyboard_workstations.highlight_always_on.tooltip")));
            }
        }

        @Override
        public int getRowWidth() {
            return ROW_WIDTH;
        }

        @Override
        protected int getScrollbarPositionX() {
            return WorkerSettingsScreen.this.width / 2 + ROW_WIDTH / 2 + 8;
        }
    }

    /**
     * One kind's share of the plantings. The slider carries a weight rather than a percentage, and
     * reports the percentage that weight currently works out to beside it: the percentages depend
     * on every other kind, so they all move when any one of them does.
     */
    private class MixSlider extends SliderWidget {
        private final SeedMix mix;
        private final Item item;
        private final List<Item> palette;

        MixSlider(SeedMix mix, Item item, List<Item> palette) {
            super(0, 0, CONTROL_WIDTH, CONTROL_HEIGHT, new LiteralText(""), fraction(mix.weight(item)));
            this.mix = mix;
            this.item = item;
            this.palette = palette;
            updateMessage();
        }

        private static double fraction(int weight) {
            return (double) (weight - SeedMix.MIN_WEIGHT) / (SeedMix.MAX_WEIGHT - SeedMix.MIN_WEIGHT);
        }

        @Override
        protected void updateMessage() {
            setMessage(new TranslatableText("config.keyboard_workstations.seed_weight",
                    mix.weight(item), mix.share(item, palette)));
        }

        @Override
        protected void applyValue() {
            mix.setWeight(item, (int) Math.round(
                    MathHelper.lerp(value, SeedMix.MIN_WEIGHT, SeedMix.MAX_WEIGHT)));
        }

        /** Letting go rebuilds the tab, which is what brings the other kinds' shares up to date. */
        @Override
        public void onRelease(double mouseX, double mouseY) {
            super.onRelease(mouseX, mouseY);
            refresh();
        }
    }

    private static class OptionSlider<S extends WorkerSettings<S>> extends SliderWidget {
        private final SettingOption.Range<S> option;
        private final S settings;

        OptionSlider(SettingOption.Range<S> option, S settings) {
            super(0, 0, CONTROL_WIDTH, CONTROL_HEIGHT, new LiteralText(""), fraction(option, settings));
            this.option = option;
            this.settings = settings;
            updateMessage();
        }

        private static <S extends WorkerSettings<S>> double fraction(SettingOption.Range<S> option, S settings) {
            return (double) (option.get(settings) - option.min()) / (option.max() - option.min());
        }

        @Override
        protected void updateMessage() {
            setMessage(new LiteralText(String.valueOf(option.get(settings))));
        }

        @Override
        protected void applyValue() {
            option.set(settings, (int) Math.round(MathHelper.lerp(value, option.min(), option.max())));
        }
    }
}
