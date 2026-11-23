package baritone.plus.command;

import baritone.api.BaritoneAPI;
import baritone.plus.BaritonePlusClient;
import baritone.plus.ai.AiSecrets;
import baritone.plus.config.BaritonePlusConfig;
import baritone.plus.task.CraftingTask;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Unified /bp client command for Baritone+ configuration, profiles, and direct Baritone/Baritone+ control.
 */
public class BpCommand {

    private record ConfigEntry(Supplier<String> getter, Consumer<String> setter) {}

    private static final Map<String, ConfigEntry> CONFIG_ENTRIES = buildConfigEntries();

    private static final String[] CRAFT_SUGGESTIONS = {
            "planks", "stick", "crafting_table", "furnace", "iron_ingot", "diamond",
            "wooden_pickaxe", "wooden_sword", "wooden_shovel", "wooden_axe", "wooden_hoe",
            "stone_pickaxe", "stone_sword", "stone_shovel", "stone_axe", "stone_hoe",
            "iron_pickaxe", "iron_sword", "iron_shovel", "iron_axe", "iron_hoe",
            "diamond_pickaxe", "diamond_sword", "diamond_shovel", "diamond_axe", "diamond_hoe",
            "oak_planks", "spruce_planks", "birch_planks", "jungle_planks", "acacia_planks",
            "dark_oak_planks", "mangrove_planks", "cherry_planks", "crimson_planks", "warped_planks"
    };

    private static Map<String, ConfigEntry> buildConfigEntries() {
        Map<String, ConfigEntry> map = new LinkedHashMap<>();

        // General
        map.put("general.betaFeatures", new ConfigEntry(
                () -> String.valueOf(BaritonePlusConfig.get().general.betaFeatures),
                v -> BaritonePlusConfig.get().general.betaFeatures = parseBool(v)));
        map.put("general.debugLogging", new ConfigEntry(
                () -> String.valueOf(BaritonePlusConfig.get().general.debugLogging),
                v -> BaritonePlusConfig.get().general.debugLogging = parseBool(v)));
        map.put("general.antiCheatProfile", new ConfigEntry(
                () -> BaritonePlusConfig.get().general.antiCheatProfile.name(),
                v -> BaritonePlusConfig.get().applyProfile(
                        BaritonePlusConfig.AntiCheatProfile.valueOf(v.toUpperCase()))));

        // Combat
        map.put("combat.enabled", new ConfigEntry(
                () -> String.valueOf(BaritonePlusConfig.get().combat.enabled),
                v -> BaritonePlusConfig.get().combat.enabled = parseBool(v)));
        map.put("combat.autoShield", new ConfigEntry(
                () -> String.valueOf(BaritonePlusConfig.get().combat.autoShield),
                v -> BaritonePlusConfig.get().combat.autoShield = parseBool(v)));
        map.put("combat.fleeHealthThreshold", new ConfigEntry(
                () -> String.valueOf(BaritonePlusConfig.get().combat.fleeHealthThreshold),
                v -> BaritonePlusConfig.get().combat.fleeHealthThreshold = parseDouble(v, 0.0, 20.0)));
        map.put("combat.attackRange", new ConfigEntry(
                () -> String.valueOf(BaritonePlusConfig.get().combat.attackRange),
                v -> BaritonePlusConfig.get().combat.attackRange = parseDouble(v, 1.0, 6.0)));
        map.put("combat.rotationMode", new ConfigEntry(
                () -> BaritonePlusConfig.get().combat.rotationMode.name(),
                v -> BaritonePlusConfig.get().combat.rotationMode =
                        BaritonePlusConfig.RotationMode.valueOf(v.toUpperCase())));
        map.put("combat.maxTurnSpeedDegPerTick", new ConfigEntry(
                () -> String.valueOf(BaritonePlusConfig.get().combat.maxTurnSpeedDegPerTick),
                v -> BaritonePlusConfig.get().combat.maxTurnSpeedDegPerTick = parseDouble(v, 1.0, 180.0)));
        map.put("combat.respectAttackCooldown", new ConfigEntry(
                () -> String.valueOf(BaritonePlusConfig.get().combat.respectAttackCooldown),
                v -> BaritonePlusConfig.get().combat.respectAttackCooldown = parseBool(v)));
        map.put("combat.targetHostiles", new ConfigEntry(
                () -> String.valueOf(BaritonePlusConfig.get().combat.targetHostiles),
                v -> BaritonePlusConfig.get().combat.targetHostiles = parseBool(v)));
        map.put("combat.targetPlayers", new ConfigEntry(
                () -> String.valueOf(BaritonePlusConfig.get().combat.targetPlayers),
                v -> BaritonePlusConfig.get().combat.targetPlayers = parseBool(v)));

        // AutoEat
        map.put("autoeat.enabled", new ConfigEntry(
                () -> String.valueOf(BaritonePlusConfig.get().autoEat.enabled),
                v -> BaritonePlusConfig.get().autoEat.enabled = parseBool(v)));
        map.put("autoeat.thresholdMode", new ConfigEntry(
                () -> BaritonePlusConfig.get().autoEat.thresholdMode.name(),
                v -> BaritonePlusConfig.get().autoEat.thresholdMode =
                        BaritonePlusConfig.ThresholdMode.valueOf(v.toUpperCase())));
        map.put("autoeat.healthThreshold", new ConfigEntry(
                () -> String.valueOf(BaritonePlusConfig.get().autoEat.healthThreshold),
                v -> BaritonePlusConfig.get().autoEat.healthThreshold = parseDouble(v, 1.0, 19.0)));
        map.put("autoeat.hungerThreshold", new ConfigEntry(
                () -> String.valueOf(BaritonePlusConfig.get().autoEat.hungerThreshold),
                v -> BaritonePlusConfig.get().autoEat.hungerThreshold = parseInt(v, 1, 19)));
        map.put("autoeat.priority", new ConfigEntry(
                () -> BaritonePlusConfig.get().autoEat.priority.name(),
                v -> BaritonePlusConfig.get().autoEat.priority =
                        BaritonePlusConfig.FoodPriority.valueOf(v.toUpperCase())));
        map.put("autoeat.pauseBaritone", new ConfigEntry(
                () -> String.valueOf(BaritonePlusConfig.get().autoEat.pauseBaritone),
                v -> BaritonePlusConfig.get().autoEat.pauseBaritone = parseBool(v)));
        map.put("autoeat.pauseCombat", new ConfigEntry(
                () -> String.valueOf(BaritonePlusConfig.get().autoEat.pauseCombat),
                v -> BaritonePlusConfig.get().autoEat.pauseCombat = parseBool(v)));
        map.put("autoeat.searchInventory", new ConfigEntry(
                () -> String.valueOf(BaritonePlusConfig.get().autoEat.searchInventory),
                v -> BaritonePlusConfig.get().autoEat.searchInventory = parseBool(v)));

        // Container & Chat
        map.put("container.clickDelayTicks", new ConfigEntry(
                () -> String.valueOf(BaritonePlusConfig.get().container.clickDelayTicks),
                v -> BaritonePlusConfig.get().container.clickDelayTicks = parseInt(v, 0, 40)));
        map.put("chat.minSendIntervalMs", new ConfigEntry(
                () -> String.valueOf(BaritonePlusConfig.get().chat.minSendIntervalMs),
                v -> BaritonePlusConfig.get().chat.minSendIntervalMs = parseInt(v, 100, 10000)));

        // AI (Optional)
        map.put("ai.enabled", new ConfigEntry(
                () -> String.valueOf(BaritonePlusConfig.get().aiBridge.enabled),
                v -> BaritonePlusConfig.get().aiBridge.enabled = parseBool(v)));
        map.put("ai.model", new ConfigEntry(
                () -> BaritonePlusConfig.get().aiBridge.model,
                v -> {
                    if (v.isBlank()) throw new IllegalArgumentException("Model cannot be blank");
                    BaritonePlusConfig.get().aiBridge.model = v.trim();
                }));
        map.put("ai.thinkingLevel", new ConfigEntry(
                () -> BaritonePlusConfig.get().aiBridge.thinkingLevel.name(),
                v -> BaritonePlusConfig.get().aiBridge.thinkingLevel =
                        BaritonePlusConfig.ThinkingLevel.valueOf(v.trim().toUpperCase())));
        map.put("ai.host", new ConfigEntry(
                () -> BaritonePlusConfig.get().aiBridge.host,
                v -> {
                    if (v.isBlank()) throw new IllegalArgumentException("Host cannot be blank");
                    BaritonePlusConfig.get().aiBridge.host = v.trim();
                }));
        map.put("ai.port", new ConfigEntry(
                () -> String.valueOf(BaritonePlusConfig.get().aiBridge.port),
                v -> BaritonePlusConfig.get().aiBridge.port = parseInt(v, 1024, 65535)));
        map.put("ai.allowChatSend", new ConfigEntry(
                () -> String.valueOf(BaritonePlusConfig.get().aiBridge.allowChatSend),
                v -> BaritonePlusConfig.get().aiBridge.allowChatSend = parseBool(v)));
        map.put("ai.allowGuiInteraction", new ConfigEntry(
                () -> String.valueOf(BaritonePlusConfig.get().aiBridge.allowGuiInteraction),
                v -> BaritonePlusConfig.get().aiBridge.allowGuiInteraction = parseBool(v)));
        map.put("ai.allowCombatOverride", new ConfigEntry(
                () -> String.valueOf(BaritonePlusConfig.get().aiBridge.allowCombatOverride),
                v -> BaritonePlusConfig.get().aiBridge.allowCombatOverride = parseBool(v)));

        return map;
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        SuggestionProvider<FabricClientCommandSource> keySuggestions = (context, builder) -> {
            String rem = builder.getRemaining().toLowerCase();
            for (String key : CONFIG_ENTRIES.keySet()) {
                if (key.toLowerCase().startsWith(rem)) {
                    builder.suggest(key);
                }
            }
            return builder.buildFuture();
        };

        SuggestionProvider<FabricClientCommandSource> craftSuggestions = (context, builder) -> {
            String rem = builder.getRemaining().toLowerCase();
            for (String s : CRAFT_SUGGESTIONS) {
                if (s.startsWith(rem)) {
                    builder.suggest(s);
                }
            }
            if (BaritonePlusConfig.get().general.betaFeatures) {
                if ("nether_portal".startsWith(rem)) builder.suggest("nether_portal");
                if ("flint_and_steel".startsWith(rem)) builder.suggest("flint_and_steel");
            }
            return builder.buildFuture();
        };

        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("bp")
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("gui")
                        .executes(ctx -> {
                            BaritonePlusClient.requestOpenGui();
                            return 1;
                        }))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("craft")
                        .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("amount", IntegerArgumentType.integer(1, 64))
                                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("item", StringArgumentType.word())
                                        .suggests(craftSuggestions)
                                        .executes(ctx -> startCrafting(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "item"),
                                                IntegerArgumentType.getInteger(ctx, "amount")))))
                        .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("item", StringArgumentType.word())
                                .suggests(craftSuggestions)
                                .executes(ctx -> startCrafting(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "item"),
                                        1))))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("mine")
                        .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("blocks", StringArgumentType.greedyString())
                                .executes(ctx -> executeBaritone(ctx.getSource(), "mine " + StringArgumentType.getString(ctx, "blocks")))))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("goto")
                        .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("target", StringArgumentType.greedyString())
                                .executes(ctx -> executeBaritone(ctx.getSource(), "goto " + StringArgumentType.getString(ctx, "target")))))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("follow")
                        .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("target", StringArgumentType.greedyString())
                                .executes(ctx -> executeBaritone(ctx.getSource(), "follow " + StringArgumentType.getString(ctx, "target")))))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("explore")
                        .executes(ctx -> executeBaritone(ctx.getSource(), "explore")))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("farm")
                        .executes(ctx -> executeBaritone(ctx.getSource(), "farm")))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("pause")
                        .executes(ctx -> executeBaritone(ctx.getSource(), "pause")))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("resume")
                        .executes(ctx -> executeBaritone(ctx.getSource(), "resume")))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("run")
                        .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("command", StringArgumentType.greedyString())
                                .executes(ctx -> executeBaritone(ctx.getSource(), StringArgumentType.getString(ctx, "command")))))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("stop")
                        .executes(ctx -> {
                            BaritonePlusClient.AI_AGENT.stop();
                            BaritonePlusClient.TASK_MANAGER.cancelTask();
                            BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("stop");
                            send(ctx.getSource(), "§7Stopped all Baritone+ tasks, AI, and Baritone.");
                            return 1;
                        }))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("profile")
                        .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("vanilla")
                                .executes(ctx -> {
                                    BaritonePlusConfig.get().applyProfile(BaritonePlusConfig.AntiCheatProfile.VANILLA);
                                    send(ctx.getSource(), "§aApplied profile: VANILLA");
                                    return 1;
                                }))
                        .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("strict")
                                .executes(ctx -> {
                                    BaritonePlusConfig.get().applyProfile(BaritonePlusConfig.AntiCheatProfile.STRICT);
                                    send(ctx.getSource(), "§aApplied profile: STRICT");
                                    return 1;
                                })))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("combat")
                        .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("on")
                                .executes(ctx -> {
                                    BaritonePlusConfig.get().combat.enabled = true;
                                    BaritonePlusConfig.save();
                                    send(ctx.getSource(), "§aCombat controller enabled.");
                                    return 1;
                                }))
                        .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("off")
                                .executes(ctx -> {
                                    BaritonePlusConfig.get().combat.enabled = false;
                                    BaritonePlusConfig.save();
                                    send(ctx.getSource(), "§cCombat controller disabled.");
                                    return 1;
                                })))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("autoeat")
                        .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("on")
                                .executes(ctx -> {
                                    BaritonePlusConfig.get().autoEat.enabled = true;
                                    BaritonePlusConfig.save();
                                    send(ctx.getSource(), "§aAuto-Eat enabled.");
                                    return 1;
                                }))
                        .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("off")
                                .executes(ctx -> {
                                    BaritonePlusConfig.get().autoEat.enabled = false;
                                    BaritonePlusConfig.save();
                                    send(ctx.getSource(), "§cAuto-Eat disabled.");
                                    return 1;
                                })))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("shield")
                        .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("on")
                                .executes(ctx -> {
                                    BaritonePlusConfig.get().combat.autoShield = true;
                                    BaritonePlusConfig.save();
                                    send(ctx.getSource(), "§aAuto-Shield enabled.");
                                    return 1;
                                }))
                        .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("off")
                                .executes(ctx -> {
                                    BaritonePlusConfig.get().combat.autoShield = false;
                                    BaritonePlusConfig.save();
                                    send(ctx.getSource(), "§cAuto-Shield disabled.");
                                    return 1;
                                })))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("ai")
                        .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("on")
                                .executes(ctx -> {
                                    BaritonePlusConfig.get().aiBridge.enabled = true;
                                    BaritonePlusConfig.save();
                                    send(ctx.getSource(), "§aOptional Gemini AI enabled (model: "
                                            + BaritonePlusConfig.get().aiBridge.model + ").");
                                    return 1;
                                }))
                        .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("off")
                                .executes(ctx -> {
                                    BaritonePlusConfig.get().aiBridge.enabled = false;
                                    BaritonePlusClient.AI_AGENT.stop();
                                    BaritonePlusConfig.save();
                                    send(ctx.getSource(), "§cOptional Gemini AI disabled.");
                                    return 1;
                                }))
                        .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("ask")
                                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("prompt", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String prompt = StringArgumentType.getString(ctx, "prompt");
                                            BaritonePlusClient.AI_AGENT.ask(Minecraft.getInstance(), prompt);
                                            return 1;
                                        })))
                        .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("key")
                                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("apiKey", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String key = StringArgumentType.getString(ctx, "apiKey");
                                            AiSecrets.saveApiKey(key);
                                            send(ctx.getSource(), "§aSaved Gemini API key to config/baritone-plus-secrets.txt.");
                                            return 1;
                                        })))
                        .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("model")
                                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("model", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            String[] models = {"gemini-4-hydrogen", "gemini-3.5-flash-lite", "gemini-4-neon"};
                                            String rem = builder.getRemaining().toLowerCase();
                                            for (String m : models) {
                                                if (m.startsWith(rem)) builder.suggest(m);
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            String model = StringArgumentType.getString(ctx, "model");
                                            BaritonePlusConfig.get().aiBridge.model = model;
                                            BaritonePlusConfig.save();
                                            send(ctx.getSource(), "§aSet Gemini model to §f" + model);
                                            return 1;
                                        })))
                        .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset")
                                .executes(ctx -> {
                                    BaritonePlusClient.AI_AGENT.resetMemory();
                                    send(ctx.getSource(), "§7Reset Gemini conversation memory.");
                                    return 1;
                                }))
                        .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("thinking")
                                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("level", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            String rem = builder.getRemaining().toLowerCase();
                                            for (var lvl : BaritonePlusConfig.ThinkingLevel.values()) {
                                                String name = lvl.name().toLowerCase();
                                                if (name.startsWith(rem)) builder.suggest(name);
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            String raw = StringArgumentType.getString(ctx, "level");
                                            try {
                                                var level = BaritonePlusConfig.ThinkingLevel.valueOf(raw.trim().toUpperCase());
                                                BaritonePlusConfig.get().aiBridge.thinkingLevel = level;
                                                BaritonePlusConfig.save();
                                                send(ctx.getSource(), "§aSet Gemini thinking level to §f" + level.name());
                                                return 1;
                                            } catch (IllegalArgumentException e) {
                                                send(ctx.getSource(), "§cInvalid level. Use: default, minimal, low, medium, high");
                                                return 0;
                                            }
                                        })))
                        .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status")
                                .executes(ctx -> {
                                    var ai = BaritonePlusConfig.get().aiBridge;
                                    send(ctx.getSource(), "§7AI: " + (ai.enabled ? "§aON" : "§cOFF")
                                            + " §7| Model: §f" + ai.model
                                            + " §7| Thinking: §f" + ai.thinkingLevel.name()
                                            + " §7| Key: " + (AiSecrets.hasApiKey() ? "§aConfigured" : "§cMissing"));
                                    return 1;
                                })))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("config")
                        .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("key", StringArgumentType.word())
                                .suggests(keySuggestions)
                                .executes(ctx -> {
                                    String key = StringArgumentType.getString(ctx, "key");
                                    ConfigEntry entry = CONFIG_ENTRIES.get(key);
                                    if (entry == null) {
                                        send(ctx.getSource(), "§cUnknown config key: " + key);
                                        return 0;
                                    }
                                    send(ctx.getSource(), "§7" + key + " = §f" + entry.getter().get());
                                    return 1;
                                })
                                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("value", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String key = StringArgumentType.getString(ctx, "key");
                                            String value = StringArgumentType.getString(ctx, "value");
                                            ConfigEntry entry = CONFIG_ENTRIES.get(key);
                                            if (entry == null) {
                                                send(ctx.getSource(), "§cUnknown config key: " + key);
                                                return 0;
                                            }
                                            try {
                                                entry.setter().accept(value);
                                                BaritonePlusConfig.save();
                                                send(ctx.getSource(), "§aSet " + key + " = §f" + entry.getter().get());
                                                return 1;
                                            } catch (Exception e) {
                                                send(ctx.getSource(), "§cInvalid value for " + key + ": " + value);
                                                return 0;
                                            }
                                        }))))
        );
    }

    private static int startCrafting(FabricClientCommandSource source, String item, int amount) {
        send(source, "§7Starting to craft " + amount + "x " + item + "...");
        BaritonePlusClient.TASK_MANAGER.startTask(new CraftingTask(Minecraft.getInstance(), item, amount));
        return 1;
    }

    private static int executeBaritone(FabricClientCommandSource source, String cmd) {
        send(source, "§7Executing Baritone: §f#" + cmd);
        BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute(cmd);
        return 1;
    }

    private static void send(FabricClientCommandSource source, String message) {
        source.sendFeedback(Component.literal("§b[Baritone+] " + message));
    }

    private static boolean parseBool(String v) {
        if (v.equalsIgnoreCase("true") || v.equalsIgnoreCase("on")) return true;
        if (v.equalsIgnoreCase("false") || v.equalsIgnoreCase("off")) return false;
        throw new IllegalArgumentException("Expected boolean");
    }

    private static int parseInt(String v, int min, int max) {
        int val = Integer.parseInt(v.trim());
        if (val < min || val > max) throw new IllegalArgumentException("Out of range");
        return val;
    }

    private static double parseDouble(String v, double min, double max) {
        double val = Double.parseDouble(v.trim());
        if (val < min || val > max) throw new IllegalArgumentException("Out of range");
        return val;
    }
}
