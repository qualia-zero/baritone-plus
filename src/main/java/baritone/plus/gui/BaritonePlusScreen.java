package baritone.plus.gui;

import baritone.api.BaritoneAPI;
import baritone.api.Settings;
import baritone.api.utils.SettingsUtil;
import baritone.plus.BaritonePlusClient;
import baritone.plus.ai.AiSecrets;
import baritone.plus.config.BaritonePlusConfig;
import baritone.plus.task.CraftingTask;
import baritone.plus.util.AutoCrafter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

/**
 * Lightweight native Vanilla Screen for controlling Baritone/Baritone+ and configuring settings.
 * Uses standard Minecraft widgets with zero external UI dependencies.
 */
public class BaritonePlusScreen extends Screen {

    private enum Tab {
        CONTROL("Control"),
        BARITONE("Baritone"),
        GENERAL("General"),
        COMBAT("Combat"),
        CONTAINER_CHAT("Container"),
        AI_OPTIONAL("AI (Opt.)");

        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private static final String[] COMMON_BARITONE_BOOLS = {
            "allowBreak",
            "allowPlace",
            "allowSprint",
            "allowParkour",
            "allowParkourPlace",
            "allowWaterBucketFall",
            "antiCheatCompatibility",
            "legitMine",
            "freeLook",
            "autoTool",
            "avoidance",
            "allowInventory"
    };

    private final Screen parent;
    private Tab activeTab = Tab.CONTROL;

    // Persisted Control tab inputs across rebuilds/tab switches
    private String craftItemText = "";
    private int craftAmount = 1;
    private String mineText = "";
    private String gotoText = "";
    private String followText = "";
    private String rawCmdText = "";
    private String aiPromptText = "";

    // Persisted Baritone tab state
    private boolean showModifiedBaritoneOnly = false;
    private int modifiedPage = 0;

    // Persisted Combat tab sub-view (Combat & Shield vs Auto-Eat)
    private boolean showAutoEatSubTab = false;

    private static final int ROW_HEIGHT = 24;
    private static final int FULL_WIDTH = 260;
    private static final int CONTROL_WIDTH = 360;
    private static final int BARITONE_WIDTH = 396;
    private static final int STEPPER_BTN_WIDTH = 22;
    private static final int MODIFIED_PAGE_SIZE = 5;

    public BaritonePlusScreen(Screen parent) {
        super(Component.literal("Baritone+"));
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
        int tabWidth = 68;
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
        int y = 58;

        BaritonePlusConfig cfg = BaritonePlusConfig.get();

        switch (activeTab) {
            case CONTROL -> buildControlTab(centerX, y);
            case BARITONE -> buildBaritoneTab(centerX, y);
            case GENERAL -> {
                y = addActionRow(contentX, y, FULL_WIDTH,
                        "Anti-Cheat Profile: " + cfg.general.antiCheatProfile.name(),
                        () -> {
                            BaritonePlusConfig.AntiCheatProfile next =
                                    cfg.general.antiCheatProfile == BaritonePlusConfig.AntiCheatProfile.VANILLA
                                            ? BaritonePlusConfig.AntiCheatProfile.STRICT
                                            : BaritonePlusConfig.AntiCheatProfile.VANILLA;
                            cfg.applyProfile(next);
                            this.rebuildWidgets();
                        });
                y = addToggleRow(contentX, y, FULL_WIDTH, "Beta Features",
                        () -> cfg.general.betaFeatures,
                        val -> cfg.general.betaFeatures = val);
                addToggleRow(contentX, y, FULL_WIDTH, "Debug Logging",
                        () -> cfg.general.debugLogging,
                        val -> cfg.general.debugLogging = val);
            }
            case COMBAT -> buildCombatTab(centerX, y, cfg);
            case CONTAINER_CHAT -> {
                y = addIntStepperRow(contentX, y, FULL_WIDTH, "Container Click Delay (ticks)",
                        () -> cfg.container.clickDelayTicks, 1, 0, 20,
                        val -> cfg.container.clickDelayTicks = val);
                y = addIntStepperRow(contentX, y, FULL_WIDTH, "Chat Min Send Interval (ms)",
                        () -> cfg.chat.minSendIntervalMs, 100, 200, 5000,
                        val -> cfg.chat.minSendIntervalMs = val);
                addIntStepperRow(contentX, y, FULL_WIDTH, "Chat History Size",
                        () -> cfg.chat.maxHistorySize, 10, 10, 200,
                        val -> cfg.chat.maxHistorySize = val);
            }
            case AI_OPTIONAL -> buildAiTab(centerX, y, cfg);
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

    private void buildControlTab(int centerX, int y) {
        int x = centerX - CONTROL_WIDTH / 2;

        // Row 1: Craft (Baritone+)
        EditBox craftBox = new EditBox(this.font, x, y, 176, 20, Component.literal("Craft Item"));
        craftBox.setValue(craftItemText);
        craftBox.setHint(Component.literal("Item (e.g. iron_pickaxe)"));
        craftBox.setResponder(s -> craftItemText = s);
        this.addRenderableWidget(craftBox);

        this.addRenderableWidget(Button.builder(Component.literal("-"), btn -> {
            craftAmount = Math.max(1, craftAmount - 1);
            this.rebuildWidgets();
        }).bounds(x + 180, y, 20, 20).build());

        Button amtDisplay = Button.builder(Component.literal(String.valueOf(craftAmount)), btn -> {})
                .bounds(x + 202, y, 34, 20).build();
        amtDisplay.active = false;
        this.addRenderableWidget(amtDisplay);

        this.addRenderableWidget(Button.builder(Component.literal("+"), btn -> {
            craftAmount = Math.min(64, craftAmount + 1);
            this.rebuildWidgets();
        }).bounds(x + 238, y, 20, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Craft"), btn -> {
            String item = craftItemText.trim();
            if (!item.isEmpty() && this.minecraft != null) {
                AutoCrafter.log(this.minecraft, "Starting to craft " + craftAmount + "x " + item + "...");
                BaritonePlusClient.TASK_MANAGER.startTask(new CraftingTask(this.minecraft, item, craftAmount));
                onClose();
            }
        }).bounds(x + 264, y, 96, 20).build());
        y += ROW_HEIGHT;

        // Row 2: Mine (Baritone)
        y = addCommandInputRow(x, y, "Mine Block", "Block (e.g. diamond_ore)",
                mineText, s -> mineText = s, "Mine",
                val -> executeBaritone("mine " + val));

        // Row 3: Goto (Baritone)
        y = addCommandInputRow(x, y, "Goto Coords", "Coords (e.g. 100 64 -200)",
                gotoText, s -> gotoText = s, "Goto",
                val -> executeBaritone("goto " + val));

        // Row 4: Follow (Baritone)
        y = addCommandInputRow(x, y, "Follow Target", "Target (e.g. players / Name)",
                followText, s -> followText = s, "Follow",
                val -> executeBaritone("follow " + val));

        // Row 5: Raw Baritone Command
        y = addCommandInputRow(x, y, "Raw Command", "Baritone cmd (e.g. tunnel)",
                rawCmdText, s -> rawCmdText = s, "Run",
                this::executeBaritone);

        // Row 6: Quick Actions
        int btnW = 68;
        int gap = 5;
        this.addRenderableWidget(Button.builder(Component.literal("Explore"), btn -> {
            executeBaritone("explore");
            onClose();
        }).bounds(x, y, btnW, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Farm"), btn -> {
            executeBaritone("farm");
            onClose();
        }).bounds(x + (btnW + gap), y, btnW, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Pause"), btn -> {
            executeBaritone("pause");
            onClose();
        }).bounds(x + (btnW + gap) * 2, y, btnW, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Resume"), btn -> {
            executeBaritone("resume");
            onClose();
        }).bounds(x + (btnW + gap) * 3, y, btnW, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("§cStop All"), btn -> {
            BaritonePlusClient.AI_AGENT.stop();
            BaritonePlusClient.TASK_MANAGER.cancelTask();
            BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("stop");
            if (this.minecraft != null) {
                AutoCrafter.log(this.minecraft, "Stopped all tasks, AI, and Baritone.");
            }
            onClose();
        }).bounds(x + (btnW + gap) * 4, y, btnW, 20).build());
    }

    @SuppressWarnings("unchecked")
    private void buildBaritoneTab(int centerX, int y) {
        Settings settings = BaritoneAPI.getSettings();
        List<Settings.Setting<?>> modified = getModifiedBaritoneSettings(settings);
        int x = centerX - BARITONE_WIDTH / 2;

        // Top sub-bar: Common Settings | Modified (N) | Reset All Modified
        String commonLabel = !showModifiedBaritoneOnly ? "§b[Common Settings]" : "Common Settings";
        String modLabel = showModifiedBaritoneOnly
                ? "§e[Modified (" + modified.size() + ")]"
                : "Modified (" + modified.size() + ")";

        this.addRenderableWidget(Button.builder(Component.literal(commonLabel), btn -> {
            showModifiedBaritoneOnly = false;
            this.rebuildWidgets();
        }).bounds(x, y, 130, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal(modLabel), btn -> {
            showModifiedBaritoneOnly = true;
            modifiedPage = 0;
            this.rebuildWidgets();
        }).bounds(x + 134, y, 130, 20).build());

        Button resetAllBtn = Button.builder(Component.literal("Reset All Modified"), btn -> {
            for (Settings.Setting<?> s : modified) {
                s.reset();
            }
            SettingsUtil.save(settings);
            modifiedPage = 0;
            this.rebuildWidgets();
        }).bounds(x + 268, y, 128, 20).build();
        resetAllBtn.active = !modified.isEmpty();
        this.addRenderableWidget(resetAllBtn);
        y += ROW_HEIGHT + 2;

        if (!showModifiedBaritoneOnly) {
            int colWidth = 194;
            int colGap = 8;
            int leftX = centerX - colWidth - colGap / 2;
            int rightX = centerX + colGap / 2;

            for (int i = 0; i < COMMON_BARITONE_BOOLS.length; i++) {
                String name = COMMON_BARITONE_BOOLS[i];
                Settings.Setting<?> setting = findSettingByName(settings, name);
                int colX = (i % 2 == 0) ? leftX : rightX;
                int rowY = y + (i / 2) * ROW_HEIGHT;

                if (setting != null && setting.value instanceof Boolean boolVal) {
                    Settings.Setting<Boolean> boolSetting = (Settings.Setting<Boolean>) setting;
                    boolean isMod = !Objects.equals(boolSetting.value, boolSetting.defaultValue);
                    String prefix = isMod ? "§e* " : "";
                    String label = prefix + name + ": " + (boolVal ? "§aON" : "§cOFF");
                    this.addRenderableWidget(Button.builder(Component.literal(label), btn -> {
                        boolSetting.value = !boolSetting.value;
                        SettingsUtil.save(settings);
                        this.rebuildWidgets();
                    }).bounds(colX, rowY, colWidth, 20).build());
                } else {
                    Button missing = Button.builder(Component.literal("§7" + name + " (N/A)"), btn -> {})
                            .bounds(colX, rowY, colWidth, 20).build();
                    missing.active = false;
                    this.addRenderableWidget(missing);
                }
            }
            return;
        }

        // Modified Settings view
        if (modified.isEmpty()) {
            Button emptyBtn = Button.builder(Component.literal("All Baritone settings are at default."), btn -> {})
                    .bounds(x, y, BARITONE_WIDTH, 20).build();
            emptyBtn.active = false;
            this.addRenderableWidget(emptyBtn);
            return;
        }

        int totalPages = Math.max(1, (modified.size() + MODIFIED_PAGE_SIZE - 1) / MODIFIED_PAGE_SIZE);
        if (modifiedPage >= totalPages) {
            modifiedPage = totalPages - 1;
        }

        int start = modifiedPage * MODIFIED_PAGE_SIZE;
        int end = Math.min(modified.size(), start + MODIFIED_PAGE_SIZE);

        for (int i = start; i < end; i++) {
            Settings.Setting<?> s = modified.get(i);
            String label = "§e" + s.getName() + ": §f" + formatSettingValue(s.value);
            if (s.value instanceof Boolean) {
                Settings.Setting<Boolean> boolSetting = (Settings.Setting<Boolean>) s;
                this.addRenderableWidget(Button.builder(Component.literal(label), btn -> {
                    boolSetting.value = !boolSetting.value;
                    SettingsUtil.save(settings);
                    this.rebuildWidgets();
                }).bounds(x, y, 344, 20).build());
            } else {
                Button infoBtn = Button.builder(Component.literal(label), btn -> {})
                        .bounds(x, y, 344, 20).build();
                infoBtn.active = false;
                this.addRenderableWidget(infoBtn);
            }

            this.addRenderableWidget(Button.builder(Component.literal("Reset"), btn -> {
                s.reset();
                SettingsUtil.save(settings);
                this.rebuildWidgets();
            }).bounds(x + 348, y, 48, 20).build());

            y += ROW_HEIGHT;
        }

        if (totalPages > 1) {
            Button prev = Button.builder(Component.literal("< Prev"), btn -> {
                modifiedPage = Math.max(0, modifiedPage - 1);
                this.rebuildWidgets();
            }).bounds(x, y, 90, 20).build();
            prev.active = modifiedPage > 0;
            this.addRenderableWidget(prev);

            Button pageLabel = Button.builder(Component.literal("Page " + (modifiedPage + 1) + "/" + totalPages), btn -> {})
                    .bounds(x + 94, y, 208, 20).build();
            pageLabel.active = false;
            this.addRenderableWidget(pageLabel);

            Button next = Button.builder(Component.literal("Next >"), btn -> {
                modifiedPage = Math.min(totalPages - 1, modifiedPage + 1);
                this.rebuildWidgets();
            }).bounds(x + 306, y, 90, 20).build();
            next.active = modifiedPage < totalPages - 1;
            this.addRenderableWidget(next);
        }
    }

    private List<Settings.Setting<?>> getModifiedBaritoneSettings(Settings settings) {
        List<Settings.Setting<?>> modified = new ArrayList<>();
        for (Settings.Setting<?> s : settings.allSettings) {
            if (s.value == null || s.isJavaOnly()) {
                continue;
            }
            if (!Objects.equals(s.value, s.defaultValue)) {
                modified.add(s);
            }
        }
        return modified;
    }

    private Settings.Setting<?> findSettingByName(Settings settings, String name) {
        for (Settings.Setting<?> s : settings.allSettings) {
            if (name.equals(s.getName())) {
                return s;
            }
        }
        return null;
    }

    private String formatSettingValue(Object value) {
        String s = String.valueOf(value);
        return s.length() > 32 ? s.substring(0, 29) + "..." : s;
    }

    private int addCommandInputRow(int x, int y, String title, String hint, String initialValue,
                                   Consumer<String> onChange, String buttonLabel, Consumer<String> onRun) {
        EditBox box = new EditBox(this.font, x, y, 260, 20, Component.literal(title));
        box.setValue(initialValue);
        box.setHint(Component.literal(hint));
        box.setResponder(onChange);
        this.addRenderableWidget(box);

        this.addRenderableWidget(Button.builder(Component.literal(buttonLabel), btn -> {
            String val = box.getValue().trim();
            if (!val.isEmpty()) {
                onRun.accept(val);
                onClose();
            }
        }).bounds(x + 264, y, 96, 20).build());

        return y + ROW_HEIGHT;
    }

    private void executeBaritone(String cmd) {
        if (this.minecraft != null) {
            AutoCrafter.log(this.minecraft, "Executing Baritone: §f#" + cmd);
        }
        BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute(cmd);
    }

    private void buildAiTab(int centerX, int y, BaritonePlusConfig cfg) {
        int x = centerX - CONTROL_WIDTH / 2;
        int halfWidth = (CONTROL_WIDTH - 8) / 2;

        // Row 1: AI Enabled | Model Cycle
        addToggleRow(x, y, halfWidth, "AI Enabled",
                () -> cfg.aiBridge.enabled,
                val -> {
                    cfg.aiBridge.enabled = val;
                    if (!val) BaritonePlusClient.AI_AGENT.stop();
                });

        String[] models = {"gemini-4-hydrogen", "gemini-3.5-flash-lite", "gemini-4-neon"};
        addActionRow(x + halfWidth + 8, y, halfWidth, "Model: " + cfg.aiBridge.model, () -> {
            int idx = 0;
            for (int i = 0; i < models.length; i++) {
                if (models[i].equals(cfg.aiBridge.model)) {
                    idx = i;
                    break;
                }
            }
            cfg.aiBridge.model = models[(idx + 1) % models.length];
            BaritonePlusConfig.save();
            this.rebuildWidgets();
        });
        y += ROW_HEIGHT;

        // Row 2: Ask Gemini prompt + button
        y = addCommandInputRow(x, y, "Ask Gemini", "Ask Gemini (e.g. craft an iron pickaxe)",
                aiPromptText, s -> aiPromptText = s, "Ask AI",
                val -> BaritonePlusClient.AI_AGENT.ask(this.minecraft, val));

        // Row 3: Thinking Level | Max Steps
        addActionRow(x + 0, y, halfWidth, "Thinking: " + cfg.aiBridge.thinkingLevel.name(), () -> {
            var levels = BaritonePlusConfig.ThinkingLevel.values();
            cfg.aiBridge.thinkingLevel = levels[(cfg.aiBridge.thinkingLevel.ordinal() + 1) % levels.length];
            BaritonePlusConfig.save();
            this.rebuildWidgets();
        });
        addIntStepperRow(x + halfWidth + 8, y, halfWidth, "Max Steps",
                () -> cfg.aiBridge.maxStepsPerRequest, 1, 1, 50,
                val -> cfg.aiBridge.maxStepsPerRequest = val);
        y += ROW_HEIGHT;

        // Row 4: Allow Chat Send | Allow GUI Interaction
        addToggleRow(x, y, halfWidth, "Allow AI Chat Send",
                () -> cfg.aiBridge.allowChatSend,
                val -> cfg.aiBridge.allowChatSend = val);
        addToggleRow(x + halfWidth + 8, y, halfWidth, "Allow AI GUI Clicks",
                () -> cfg.aiBridge.allowGuiInteraction,
                val -> cfg.aiBridge.allowGuiInteraction = val);
        y += ROW_HEIGHT;

        // Row 5: Allow Combat Override | Reset Memory
        addToggleRow(x, y, halfWidth, "Allow Combat Override",
                () -> cfg.aiBridge.allowCombatOverride,
                val -> cfg.aiBridge.allowCombatOverride = val);
        this.addRenderableWidget(Button.builder(Component.literal("Reset Memory"), btn -> {
            BaritonePlusClient.AI_AGENT.resetMemory();
            if (this.minecraft != null) {
                AutoCrafter.log(this.minecraft, "Reset Gemini conversation memory.");
            }
            this.rebuildWidgets();
        }).bounds(x + halfWidth + 8, y, halfWidth, 20).build());
        y += ROW_HEIGHT;

        // Row 6: API Key status
        String keyStatus = AiSecrets.hasApiKey() ? "API Key: §aConfigured" : "API Key: §cMissing (/bp ai key)";
        Button keyBtn = Button.builder(Component.literal(keyStatus), btn -> {})
                .bounds(x, y, CONTROL_WIDTH, 20).build();
        keyBtn.active = false;
        this.addRenderableWidget(keyBtn);
    }

    private void buildCombatTab(int centerX, int y, BaritonePlusConfig cfg) {
        int subWidth = 190;
        int subGap = 8;
        int leftX = centerX - subWidth - subGap / 2;
        int rightX = centerX + subGap / 2;

        String combatSubLabel = !showAutoEatSubTab ? "§b[Combat & Shield]" : "Combat & Shield";
        String eatSubLabel = showAutoEatSubTab ? "§b[Auto-Eat]" : "Auto-Eat";

        this.addRenderableWidget(Button.builder(Component.literal(combatSubLabel), btn -> {
            showAutoEatSubTab = false;
            this.rebuildWidgets();
        }).bounds(leftX, y, subWidth, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal(eatSubLabel), btn -> {
            showAutoEatSubTab = true;
            this.rebuildWidgets();
        }).bounds(rightX, y, subWidth, 20).build());
        y += ROW_HEIGHT + 2;

        if (showAutoEatSubTab) {
            // Row 1
            addToggleRow(leftX, y, subWidth, "Auto-Eat",
                    () -> cfg.autoEat.enabled, val -> cfg.autoEat.enabled = val);
            addActionRow(rightX, y, subWidth,
                    "Mode: " + cfg.autoEat.thresholdMode.name(),
                    () -> {
                        var modes = BaritonePlusConfig.ThresholdMode.values();
                        cfg.autoEat.thresholdMode = modes[(cfg.autoEat.thresholdMode.ordinal() + 1) % modes.length];
                        BaritonePlusConfig.save();
                        this.rebuildWidgets();
                    });
            y += ROW_HEIGHT;

            // Row 2
            addDoubleStepperRow(leftX, y, subWidth, "Health <=",
                    () -> cfg.autoEat.healthThreshold, 1.0, 1.0, 19.0,
                    val -> cfg.autoEat.healthThreshold = val);
            addIntStepperRow(rightX, y, subWidth, "Hunger <=",
                    () -> cfg.autoEat.hungerThreshold, 1, 1, 19,
                    val -> cfg.autoEat.hungerThreshold = val);
            y += ROW_HEIGHT;

            // Row 3
            addActionRow(leftX, y, subWidth,
                    "Priority: " + cfg.autoEat.priority.name(),
                    () -> {
                        cfg.autoEat.priority =
                                cfg.autoEat.priority == BaritonePlusConfig.FoodPriority.SATURATION
                                        ? BaritonePlusConfig.FoodPriority.HUNGER
                                        : BaritonePlusConfig.FoodPriority.SATURATION;
                        BaritonePlusConfig.save();
                        this.rebuildWidgets();
                    });
            addToggleRow(rightX, y, subWidth, "Search Inventory",
                    () -> cfg.autoEat.searchInventory, val -> cfg.autoEat.searchInventory = val);
            y += ROW_HEIGHT;

            // Row 4
            addToggleRow(leftX, y, subWidth, "Pause Baritone",
                    () -> cfg.autoEat.pauseBaritone, val -> cfg.autoEat.pauseBaritone = val);
            addToggleRow(rightX, y, subWidth, "Pause Combat",
                    () -> cfg.autoEat.pauseCombat, val -> cfg.autoEat.pauseCombat = val);
            return;
        }

        // Combat & Shield sub-tab
        // Row 1
        addToggleRow(leftX, y, subWidth, "Combat Attacker",
                () -> cfg.combat.enabled, val -> cfg.combat.enabled = val);
        addToggleRow(rightX, y, subWidth, "Auto Shield",
                () -> cfg.combat.autoShield, val -> cfg.combat.autoShield = val);
        y += ROW_HEIGHT;

        // Row 2
        addToggleRow(leftX, y, subWidth, "Attack Cooldown",
                () -> cfg.combat.respectAttackCooldown, val -> cfg.combat.respectAttackCooldown = val);
        addToggleRow(rightX, y, subWidth, "Target Hostiles",
                () -> cfg.combat.targetHostiles, val -> cfg.combat.targetHostiles = val);
        y += ROW_HEIGHT;

        // Row 3
        addToggleRow(leftX, y, subWidth, "Target Players",
                () -> cfg.combat.targetPlayers, val -> cfg.combat.targetPlayers = val);
        addActionRow(rightX, y, subWidth,
                "Rotation: " + cfg.combat.rotationMode.name(),
                () -> {
                    cfg.combat.rotationMode =
                            cfg.combat.rotationMode == BaritonePlusConfig.RotationMode.SERVER_SILENT
                                    ? BaritonePlusConfig.RotationMode.CLIENT
                                    : BaritonePlusConfig.RotationMode.SERVER_SILENT;
                    BaritonePlusConfig.save();
                    this.rebuildWidgets();
                });
        y += ROW_HEIGHT;

        // Row 4
        addDoubleStepperRow(leftX, y, subWidth, "Range",
                () -> cfg.combat.attackRange, 0.1, 2.0, 4.5,
                val -> cfg.combat.attackRange = val);
        addDoubleStepperRow(rightX, y, subWidth, "Turn Speed",
                () -> cfg.combat.maxTurnSpeedDegPerTick, 5.0, 10.0, 180.0,
                val -> cfg.combat.maxTurnSpeedDegPerTick = val);
        y += ROW_HEIGHT;

        // Row 5
        addDoubleStepperRow(leftX, y, subWidth, "Flee HP",
                () -> cfg.combat.fleeHealthThreshold, 1.0, 0.0, 18.0,
                val -> cfg.combat.fleeHealthThreshold = val);
    }

    private int addToggleRow(int x, int y, int width, String label, BooleanSupplier getter, Consumer<Boolean> setter) {
        boolean current = getter.getAsBoolean();
        String text = label + ": " + (current ? "§aON" : "§cOFF");
        this.addRenderableWidget(Button.builder(Component.literal(text), btn -> {
            setter.accept(!getter.getAsBoolean());
            BaritonePlusConfig.save();
            this.rebuildWidgets();
        }).bounds(x, y, width, 20).build());
        return y + ROW_HEIGHT;
    }

    private int addActionRow(int x, int y, int width, String label, Runnable action) {
        this.addRenderableWidget(Button.builder(Component.literal(label), btn -> action.run())
                .bounds(x, y, width, 20).build());
        return y + ROW_HEIGHT;
    }

    private int addIntStepperRow(int x, int y, int width, String label, IntSupplier getter, int step, int min, int max, Consumer<Integer> setter) {
        int val = getter.getAsInt();
        int centerWidth = width - (STEPPER_BTN_WIDTH * 2) - 8;

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
        }).bounds(x + width - STEPPER_BTN_WIDTH, y, STEPPER_BTN_WIDTH, 20).build());

        return y + ROW_HEIGHT;
    }

    private int addDoubleStepperRow(int x, int y, int width, String label, DoubleSupplier getter, double step, double min, double max, Consumer<Double> setter) {
        double val = getter.getAsDouble();
        int centerWidth = width - (STEPPER_BTN_WIDTH * 2) - 8;
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
        }).bounds(x + width - STEPPER_BTN_WIDTH, y, STEPPER_BTN_WIDTH, 20).build());

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
