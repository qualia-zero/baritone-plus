package baritone.plus.command;

import baritone.plus.BaritonePlusClient;
import baritone.plus.task.CraftingTask;
import net.minecraft.client.Minecraft;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

public class CraftCommand {

    public static boolean betaFeaturesEnabled = baritone.plus.util.BetaConfig.isBetaEnabled();

    private static void startCrafting(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> context, String item, int amount) {
        context.getSource().sendFeedback(Component.literal("§b[Baritone+] §7Starting to craft " + amount + " " + item + "..."));
        BaritonePlusClient.TASK_MANAGER.startTask(new CraftingTask(Minecraft.getInstance(), item, amount));
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("allowBaritonePlusBetaFeatures")
                .executes(context -> {
                    betaFeaturesEnabled = !betaFeaturesEnabled;
                    baritone.plus.util.BetaConfig.setBetaEnabled(betaFeaturesEnabled);
                    if (betaFeaturesEnabled) {
                        context.getSource().sendFeedback(Component.literal("§b[Baritone+] §7Beta features have been enabled."));
                    } else {
                        context.getSource().sendFeedback(Component.literal("§b[Baritone+] §7Beta features have been disabled."));
                    }
                    return 1;
                })
        );

        com.mojang.brigadier.suggestion.SuggestionProvider<FabricClientCommandSource> suggestionsProvider = (context, builder) -> {
            // Basic auto-completion for common items
            String[] suggestions = {
                    "planks", "stick", "crafting_table", "furnace", "iron_ingot", "diamond",
                    "wooden_pickaxe", "wooden_sword", "wooden_shovel", "wooden_axe", "wooden_hoe",
                    "stone_pickaxe", "stone_sword", "stone_shovel", "stone_axe", "stone_hoe",
                    "iron_pickaxe", "iron_sword", "iron_shovel", "iron_axe", "iron_hoe",
                    "diamond_pickaxe", "diamond_sword", "diamond_shovel", "diamond_axe", "diamond_hoe",
                    "oak_planks", "spruce_planks", "birch_planks", "jungle_planks", "acacia_planks", "dark_oak_planks", "mangrove_planks", "cherry_planks", "crimson_planks", "warped_planks"
            };
            String remaining = builder.getRemaining().toLowerCase();
            for (String s : suggestions) {
                if (s.startsWith(remaining)) {
                    builder.suggest(s);
                }
            }
            if (betaFeaturesEnabled) {
                if ("nether_portal".startsWith(remaining)) {
                    builder.suggest("nether_portal");
                }
                if ("flint_and_steel".startsWith(remaining)) {
                    builder.suggest("flint_and_steel");
                }
            }
            return builder.buildFuture();
        };

        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("craft")
                // 1. /craft <amount> <item>
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("amount", IntegerArgumentType.integer(1, 64))
                        .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("item", StringArgumentType.word())
                                .suggests(suggestionsProvider)
                                .executes(context -> {
                                    int amount = IntegerArgumentType.getInteger(context, "amount");
                                    String item = StringArgumentType.getString(context, "item");
                                    startCrafting(context, item, amount);
                                    return 1;
                                })
                        )
                )
                // 2. /craft <item> (defaults to 1)
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("item", StringArgumentType.word())
                        .suggests(suggestionsProvider)
                        .executes(context -> {
                            String item = StringArgumentType.getString(context, "item");
                            startCrafting(context, item, 1);
                            return 1;
                        })
                )
        );

        // Cancel command
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("cancel")
                .executes(context -> {
                    context.getSource().sendFeedback(Component.literal("§b[Baritone+] §7Cancelled current tasks."));
                    BaritonePlusClient.TASK_MANAGER.cancelTask();
                    // Also stop baritone
                    baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("stop");
                    return 1;
                })
        );
        
        // Alias /c for cancel
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("c")
                .executes(context -> {
                    context.getSource().sendFeedback(Component.literal("§b[Baritone+] §7Cancelled current tasks."));
                    BaritonePlusClient.TASK_MANAGER.cancelTask();
                    baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("stop");
                    return 1;
                })
        );
    }
}
