package baritone.plus.gui;

import baritone.plus.config.BaritonePlusConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

/**
 * Lightweight native Vanilla Screen for configuring Baritone+.
 * Uses standard Minecraft widgets with zero external UI dependencies.
 */
public class BaritonePlusScreen extends Screen {

    private enum Tab {
        GENERAL("General"),
        COMBAT("Combat"),
        CONTAINER_CHAT("Container & Chat"),
        AI_OPTIONAL("AI (Optional)");

        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private final Screen parent;
    private Tab activeTab = Tab.GENERAL;

    private static final int ROW_HEIGHT = 24;
    private static final int FULL_WIDTH = 260;
    private static final int STEPPER_BTN_WIDTH = 22;

    public BaritonePlusScreen(Screen parent) {
        super(Component.literal("Baritone+ Settings"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int tabWidth = 94;
        int tabGap = 4;
        int totalTabsWidth = Tab.values().length * tabWidth + (Tab.values().length - 1) * tabGap;
        int tabStartX = centerX - totalTabsWidth / 2;
        int tabY = 28;

        for (int i = 0; i < Tab.values().length; i++) {
            Tab tab = Tab.values()[i];
            String label = (tab == activeTab) ? "§b[" + tab.label + "]" : tab.label;
            Button tabBtn = Button.builder(Component.literal(label), btn -> {
                this.activeTab = tab;
                this.rebuildWidgets();
            }).bounds(tabStartX + i * (tabWidth + tabGap), tabY, tabWidth, 20).build();
            this.addRenderableWidget(tabBtn);
        }

        int contentX = centerX - FULL_WIDTH / 2;
        int y = 60;

        BaritonePlusConfig cfg = BaritonePlusConfig.get();

        switch (activeTab) {
            case GENERAL -> {
                y = addActionRow(contentX, y,
                        "Anti-Cheat Profile: " + cfg.general.antiCheatProfile.name(),
                        () -> {
                            BaritonePlusConfig.AntiCheatProfile next =
                                    cfg.general.antiCheatProfile == BaritonePlusConfig.AntiCheatProfile.VANILLA
                                            ? BaritonePlusConfig.AntiCheatProfile.STRICT
                                            : BaritonePlusConfig.AntiCheatProfile.VANILLA;
                            cfg.applyProfile(next);
                            this.rebuildWidgets();
                        });
                y = addToggleRow(contentX, y, "Beta Features",
                        () -> cfg.general.betaFeatures,
                        val -> cfg.general.betaFeatures = val);
                y = addToggleRow(contentX, y, "Debug Logging",
                        () -> cfg.general.debugLogging,
                        val -> cfg.general.debugLogging = val);
            }
            case COMBAT -> {
                y = addToggleRow(contentX, y, "Combat Controller",
                        () -> cfg.combat.enabled,
                        val -> cfg.combat.enabled = val);
                y = addToggleRow(contentX, y, "Auto Shield",
                        () -> cfg.combat.autoShield,
                        val -> cfg.combat.autoShield = val);
                y = addToggleRow(contentX, y, "Auto Eat",
                        () -> cfg.combat.autoEat,
                        val -> cfg.combat.autoEat = val);
                y = addToggleRow(contentX, y, "Respect Attack Cooldown",
                        () -> cfg.combat.respectAttackCooldown,
                        val -> cfg.combat.respectAttackCooldown = val);
                y = addToggleRow(contentX, y, "Target Hostiles",
                        () -> cfg.combat.targetHostiles,
                        val -> cfg.combat.targetHostiles = val);
                y = addToggleRow(contentX, y, "Target Players",
                        () -> cfg.combat.targetPlayers,
                        val -> cfg.combat.targetPlayers = val);
                y = addActionRow(contentX, y,
                        "Rotation Mode: " + cfg.combat.rotationMode.name(),
                        () -> {
                            cfg.combat.rotationMode =
                                    cfg.combat.rotationMode == BaritonePlusConfig.RotationMode.SERVER_SILENT
                                            ? BaritonePlusConfig.RotationMode.CLIENT
                                            : BaritonePlusConfig.RotationMode.SERVER_SILENT;
                            BaritonePlusConfig.save();
                            this.rebuildWidgets();
                        });
                y = addDoubleStepperRow(contentX, y, "Attack Range",
                        () -> cfg.combat.attackRange, 0.1, 2.0, 4.5,
                        val -> cfg.combat.attackRange = val);
                y = addDoubleStepperRow(contentX, y, "Max Turn Speed (deg/tick)",
                        () -> cfg.combat.maxTurnSpeedDegPerTick, 5.0, 10.0, 180.0,
                        val -> cfg.combat.maxTurnSpeedDegPerTick = val);
                y = addDoubleStepperRow(contentX, y, "Flee Health Threshold",
                        () -> cfg.combat.fleeHealthThreshold, 1.0, 0.0, 18.0,
                        val -> cfg.combat.fleeHealthThreshold = val);
            }
            case CONTAINER_CHAT -> {
                y = addIntStepperRow(contentX, y, "Container Click Delay (ticks)",
                        () -> cfg.container.clickDelayTicks, 1, 0, 20,
                        val -> cfg.container.clickDelayTicks = val);
                y = addIntStepperRow(contentX, y, "Chat Min Send Interval (ms)",
                        () -> cfg.chat.minSendIntervalMs, 100, 200, 5000,
                        val -> cfg.chat.minSendIntervalMs = val);
                y = addIntStepperRow(contentX, y, "Chat History Size",
                        () -> cfg.chat.maxHistorySize, 10, 10, 200,
                        val -> cfg.chat.maxHistorySize = val);
            }
            case AI_OPTIONAL -> {
                y = addToggleRow(contentX, y, "AI Bridge Enabled (Optional)",
                        () -> cfg.aiBridge.enabled,
                        val -> cfg.aiBridge.enabled = val);
                y = addIntStepperRow(contentX, y, "Bridge Port (" + cfg.aiBridge.host + ")",
                        () -> cfg.aiBridge.port, 1, 1024, 65535,
                        val -> cfg.aiBridge.port = val);
                y = addIntStepperRow(contentX, y, "Snapshot Interval (ticks)",
                        () -> cfg.aiBridge.snapshotIntervalTicks, 5, 5, 200,
                        val -> cfg.aiBridge.snapshotIntervalTicks = val);
                y = addToggleRow(contentX, y, "Allow AI Chat Send",
                        () -> cfg.aiBridge.allowChatSend,
                        val -> cfg.aiBridge.allowChatSend = val);
                y = addToggleRow(contentX, y, "Allow AI GUI Interaction",
                        () -> cfg.aiBridge.allowGuiInteraction,
                        val -> cfg.aiBridge.allowGuiInteraction = val);
                y = addToggleRow(contentX, y, "Allow AI Combat Override",
                        () -> cfg.aiBridge.allowCombatOverride,
                        val -> cfg.aiBridge.allowCombatOverride = val);
            }
        }

        int footerY = this.height - 28;
        int footerBtnWidth = 124;
        this.addRenderableWidget(Button.builder(Component.literal("Reset Defaults"), btn -> {
            BaritonePlusConfig.resetDefaults();
            this.rebuildWidgets();
        }).bounds(centerX - footerBtnWidth - 6, footerY, footerBtnWidth, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Done"), btn -> onClose())
                .bounds(centerX + 6, footerY, footerBtnWidth, 20).build());
    }

    private int addToggleRow(int x, int y, String label, BooleanSupplier getter, Consumer<Boolean> setter) {
        boolean current = getter.getAsBoolean();
        String text = label + ": " + (current ? "§aON" : "§cOFF");
        this.addRenderableWidget(Button.builder(Component.literal(text), btn -> {
            setter.accept(!getter.getAsBoolean());
            BaritonePlusConfig.save();
            this.rebuildWidgets();
        }).bounds(x, y, FULL_WIDTH, 20).build());
        return y + ROW_HEIGHT;
    }

    private int addActionRow(int x, int y, String label, Runnable action) {
        this.addRenderableWidget(Button.builder(Component.literal(label), btn -> action.run())
                .bounds(x, y, FULL_WIDTH, 20).build());
        return y + ROW_HEIGHT;
    }

    private int addIntStepperRow(int x, int y, String label, IntSupplier getter, int step, int min, int max, Consumer<Integer> setter) {
        int val = getter.getAsInt();
        int centerWidth = FULL_WIDTH - (STEPPER_BTN_WIDTH * 2) - 8;

        this.addRenderableWidget(Button.builder(Component.literal("-"), btn -> {
            int next = Math.max(min, getter.getAsInt() - step);
            setter.accept(next);
            BaritonePlusConfig.save();
            this.rebuildWidgets();
        }).bounds(x, y, STEPPER_BTN_WIDTH, 20).build());

        Button display = Button.builder(Component.literal(label + ": " + val), btn -> {})
                .bounds(x + STEPPER_BTN_WIDTH + 4, y, centerWidth, 20).build();
        display.active = false;
        this.addRenderableWidget(display);

        this.addRenderableWidget(Button.builder(Component.literal("+"), btn -> {
            int next = Math.min(max, getter.getAsInt() + step);
            setter.accept(next);
            BaritonePlusConfig.save();
            this.rebuildWidgets();
        }).bounds(x + FULL_WIDTH - STEPPER_BTN_WIDTH, y, STEPPER_BTN_WIDTH, 20).build());

        return y + ROW_HEIGHT;
    }

    private int addDoubleStepperRow(int x, int y, String label, DoubleSupplier getter, double step, double min, double max, Consumer<Double> setter) {
        double val = getter.getAsDouble();
        int centerWidth = FULL_WIDTH - (STEPPER_BTN_WIDTH * 2) - 8;
        String formatted = String.format(Locale.ROOT, "%.2f", val);

        this.addRenderableWidget(Button.builder(Component.literal("-"), btn -> {
            double next = Math.max(min, Math.round((getter.getAsDouble() - step) * 100.0) / 100.0);
            setter.accept(next);
            BaritonePlusConfig.save();
            this.rebuildWidgets();
        }).bounds(x, y, STEPPER_BTN_WIDTH, 20).build());

        Button display = Button.builder(Component.literal(label + ": " + formatted), btn -> {})
                .bounds(x + STEPPER_BTN_WIDTH + 4, y, centerWidth, 20).build();
        display.active = false;
        this.addRenderableWidget(display);

        this.addRenderableWidget(Button.builder(Component.literal("+"), btn -> {
            double next = Math.min(max, Math.round((getter.getAsDouble() + step) * 100.0) / 100.0);
            setter.accept(next);
            BaritonePlusConfig.save();
            this.rebuildWidgets();
        }).bounds(x + FULL_WIDTH - STEPPER_BTN_WIDTH, y, STEPPER_BTN_WIDTH, 20).build());

        return y + ROW_HEIGHT;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int titleWidth = this.font.width(this.title);
        graphics.text(this.font, this.title, (this.width - titleWidth) / 2, 10, 0xFFFFFFFF, true);
    }

    @Override
    public void onClose() {
        BaritonePlusConfig.save();
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(parent);
        }
    }
}
