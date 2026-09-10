package baritone.plus.task;

import baritone.plus.BaritonePlusClient;
import baritone.plus.util.AutoCrafter;
import baritone.api.IBaritone;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

public class CraftAction implements Action {

    private final String item;
    private final int amount;
    private boolean hasLogged = false;

    public CraftAction(String item, int amount) {
        this.item = item;
        this.amount = amount;
    }

    @Override
    public void execute(Minecraft client, IBaritone baritone) {
        // Verify materials before crafting
        boolean hasMaterials = true;
        String missingItem = "";
        
        if (item.equals("planks")) {
            if (AutoCrafter.getItemCount(client, "log") < 1) {
                hasMaterials = false;
                missingItem = "log";
            }
        } else if (item.endsWith("_planks")) {
            String requiredLog = AutoCrafter.getLogForPlanks(item);
            if (AutoCrafter.getItemCount(client, requiredLog) < 1) {
                hasMaterials = false;
                missingItem = requiredLog;
            }
        } else if (item.equals("crafting_table")) {
            if (AutoCrafter.getItemCount(client, "planks") < 4) {
                hasMaterials = false;
                missingItem = "planks";
            }
        } else if (item.equals("stick")) {
            if (AutoCrafter.getItemCount(client, "planks") < 2) {
                hasMaterials = false;
                missingItem = "planks";
            }
        } else if (item.endsWith("_pickaxe") || item.endsWith("_axe") || item.endsWith("_shovel") || item.endsWith("_hoe") || item.endsWith("_sword")) {
            String material = "";
            int materialCount = 0;
            int stickCount = 0;
            if (item.startsWith("wooden_")) {
                material = "planks";
            } else if (item.startsWith("stone_")) {
                material = "cobblestone";
            } else if (item.startsWith("iron_")) {
                material = "iron_ingot";
            } else if (item.startsWith("diamond_")) {
                material = "diamond";
            }

            if (item.endsWith("_pickaxe") || item.endsWith("_axe")) {
                materialCount = 3;
                stickCount = 2;
            } else if (item.endsWith("_shovel")) {
                materialCount = 1;
                stickCount = 2;
            } else if (item.endsWith("_hoe")) {
                materialCount = 2;
                stickCount = 2;
            } else if (item.endsWith("_sword")) {
                materialCount = 2;
                stickCount = 1;
            }

            if (AutoCrafter.getItemCount(client, material) < materialCount) {
                hasMaterials = false;
                missingItem = material + " (" + materialCount + "x required)";
            } else if (stickCount > 0 && AutoCrafter.getItemCount(client, "stick") < stickCount) {
                hasMaterials = false;
                missingItem = "stick (" + stickCount + "x required)";
            }
        } else if (item.equals("furnace")) {
            if (AutoCrafter.getItemCount(client, "cobblestone") < 8) {
                hasMaterials = false;
                missingItem = "cobblestone (8x required)";
            }
        } else if (item.equals("flint_and_steel")) {
            if (AutoCrafter.getItemCount(client, "iron_ingot") < 1) {
                hasMaterials = false;
                missingItem = "iron_ingot";
            } else if (AutoCrafter.getItemCount(client, "flint") < 1) {
                hasMaterials = false;
                missingItem = "flint";
            }
        }
        
        if (!hasMaterials) {
            AutoCrafter.log(client, "Error: Missing materials to craft " + item + "! Missing: " + missingItem);
            BaritonePlusClient.TASK_MANAGER.failTask();
            return;
        }

        boolean is2x2Recipe = item.equals("planks") || item.endsWith("_planks") || item.equals("crafting_table") || item.equals("stick") || item.equals("flint_and_steel");
        boolean isCraftingScreen = AutoCrafter.isCraftingScreenOpen(client);
        
        if (!is2x2Recipe && !isCraftingScreen) {
            AutoCrafter.log(client, "§cError: Crafting screen not open (required for 3x3 recipes)!");
            BaritonePlusClient.TASK_MANAGER.failTask();
            return;
        }
        
        if (is2x2Recipe && !isCraftingScreen && !(client.gui.screen() instanceof InventoryScreen)) {
            this.hasLogged = true;
            client.gui.setScreen(new InventoryScreen(client.player));
            BaritonePlusClient.TASK_MANAGER.prependAction(this);
            BaritonePlusClient.TASK_MANAGER.setTickDelay(10);
            return;
        }
        
        int searchStart = isCraftingScreen ? 10 : 9;
        int searchEnd = isCraftingScreen ? 46 : 45;
        
        boolean isTool = item.endsWith("_pickaxe") || item.endsWith("_axe") || item.endsWith("_shovel") || item.endsWith("_hoe") || item.endsWith("_sword");

        if (item.equals("planks")) {
            AutoCrafter.moveItemToSlots(client, "log", searchStart, searchEnd, new int[]{1});
        } else if (item.endsWith("_planks")) {
            String requiredLog = AutoCrafter.getLogForPlanks(item);
            AutoCrafter.moveItemToSlots(client, requiredLog, searchStart, searchEnd, new int[]{1});
        } else if (item.equals("crafting_table")) {
            if (isCraftingScreen) {
                AutoCrafter.moveItemToSlots(client, "planks", searchStart, searchEnd, new int[]{1, 2, 4, 5});
            } else {
                AutoCrafter.moveItemToSlots(client, "planks", searchStart, searchEnd, new int[]{1, 2, 3, 4});
            }
        } else if (item.equals("stick")) {
            if (isCraftingScreen) {
                AutoCrafter.moveItemToSlots(client, "planks", searchStart, searchEnd, new int[]{2, 5});
            } else {
                AutoCrafter.moveItemToSlots(client, "planks", searchStart, searchEnd, new int[]{1, 3});
            }
        } else if (isTool) {
            String material = "";
            int[] materialSlots = new int[0];
            int[] stickSlots = new int[0];

            if (item.startsWith("wooden_")) {
                material = "planks";
            } else if (item.startsWith("stone_")) {
                material = "cobblestone";
            } else if (item.startsWith("iron_")) {
                material = "iron_ingot";
            } else if (item.startsWith("diamond_")) {
                material = "diamond";
            }

            if (item.endsWith("_pickaxe")) {
                materialSlots = new int[]{1, 2, 3};
                stickSlots = new int[]{5, 8};
            } else if (item.endsWith("_axe")) {
                materialSlots = new int[]{1, 2, 4};
                stickSlots = new int[]{5, 8};
            } else if (item.endsWith("_shovel")) {
                materialSlots = new int[]{2};
                stickSlots = new int[]{5, 8};
            } else if (item.endsWith("_hoe")) {
                materialSlots = new int[]{1, 2};
                stickSlots = new int[]{5, 8};
            } else if (item.endsWith("_sword")) {
                materialSlots = new int[]{2, 5};
                stickSlots = new int[]{8};
            }

            AutoCrafter.moveItemToSlots(client, material, 10, 46, materialSlots);
            if (stickSlots.length > 0) {
                AutoCrafter.moveItemToSlots(client, "stick", 10, 46, stickSlots);
            }
        } else if (item.equals("furnace")) {
            AutoCrafter.moveItemToSlots(client, "cobblestone", 10, 46, new int[]{1, 2, 3, 4, 6, 7, 8, 9});
        } else if (item.equals("flint_and_steel")) {
            if (isCraftingScreen) {
                AutoCrafter.moveItemToSlots(client, "iron_ingot", searchStart, searchEnd, new int[]{1});
                AutoCrafter.moveItemToSlots(client, "flint", searchStart, searchEnd, new int[]{5});
            } else {
                AutoCrafter.moveItemToSlots(client, "iron_ingot", searchStart, searchEnd, new int[]{1});
                AutoCrafter.moveItemToSlots(client, "flint", searchStart, searchEnd, new int[]{4});
            }
        }
        
        // Quick move the result
        AutoCrafter.takeResult(client, 0);
        
        if (isTool || item.equals("flint_and_steel")) {
            BaritonePlusClient.TASK_MANAGER.prependAction(new EnsureHotbarAction(item, isCraftingScreen));
            BaritonePlusClient.TASK_MANAGER.setTickDelay(5);
        }
    }

    @Override
    public String getDescription() {
        return "Crafting " + amount + "x " + item;
    }

    @Override
    public boolean shouldLog() {
        return !hasLogged;
    }
}
