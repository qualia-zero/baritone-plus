package baritone.plus.task;

import baritone.plus.util.AutoCrafter;
import net.minecraft.client.Minecraft;
import java.util.LinkedList;
import java.util.Queue;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

public class CraftingTask {
    
    private final String targetItem;
    private final int targetAmount;
    private final Queue<Action> actionQueue = new LinkedList<>();
    private final Map<String, Integer> virtualInventory = new HashMap<>();
    private final Minecraft client;
    private boolean virtualCraftingTablePlaced = false;
    private boolean virtualFurnacePlaced = false;

    public CraftingTask(Minecraft client, String targetItem, int targetAmount) {
        this.client = client;
        this.targetItem = targetItem;
        this.targetAmount = targetAmount;
        initializeVirtualInventory();
        buildQueue();
    }

    private void initializeVirtualInventory() {
        String[] itemsToCheck = {
            "log", "planks", "stick", "crafting_table",
            "stone", "cobblestone", "coal", "iron_ore", "raw_iron",
            "furnace", "iron_ingot", "wooden_pickaxe", "stone_pickaxe", "iron_pickaxe", "diamond_pickaxe",
            "diamond", "obsidian", "flint", "flint_and_steel", "nether_portal"
        };
        for (String itemName : itemsToCheck) {
            int count = AutoCrafter.getItemCount(client, itemName);
            if (count > 0) {
                virtualInventory.put(itemName, count);
            }
        }
        // Check counts for specific target item and related log source if requested
        if (targetItem != null) {
            int count = AutoCrafter.getItemCount(client, targetItem);
            if (count > 0) {
                virtualInventory.put(targetItem, count);
            }
            if (targetItem.endsWith("_planks")) {
                String logType = AutoCrafter.getLogForPlanks(targetItem);
                int logCount = AutoCrafter.getItemCount(client, logType);
                if (logCount > 0) {
                    virtualInventory.put(logType, logCount);
                }
            }
        }
    }

    private void buildQueue() {
        ensureItem(targetItem, targetAmount);
    }

    private void ensureItem(String item, int amount) {
        int current = virtualInventory.getOrDefault(item, 0);
        if (current >= amount) {
            return;
        }
        int needed = amount - current;
        
        if (item.equals("log")) {
            actionQueue.add(new BaritoneCommandAction(AutoCrafter.getMineLogsCommand(client, amount)));
            virtualInventory.put(item, current + needed);
        } else if (item.endsWith("_log") || item.endsWith("_stem")) {
            actionQueue.add(new BaritoneCommandAction("mine " + amount + " " + item));
            virtualInventory.put(item, current + needed);
        } else if (item.equals("stone")) {
            ensureItem("wooden_pickaxe", 1);
            actionQueue.add(new BaritoneCommandAction("mine " + amount + " stone deepslate"));
            virtualInventory.put(item, current + needed);
        } else if (item.equals("cobblestone")) {
            ensureItem("wooden_pickaxe", 1);
            actionQueue.add(new BaritoneCommandAction("mine " + amount + " stone deepslate"));
            virtualInventory.put(item, current + needed);
        } else if (item.equals("coal")) {
            ensureItem("wooden_pickaxe", 1);
            actionQueue.add(new BaritoneCommandAction("mine " + amount + " coal_ore deepslate_coal_ore"));
            virtualInventory.put(item, current + needed);
        } else if (item.equals("iron_ore")) {
            ensureItem("stone_pickaxe", 1);
            actionQueue.add(new BaritoneCommandAction("mine " + amount + " iron_ore deepslate_iron_ore"));
            virtualInventory.put(item, current + needed);
        } else if (item.equals("raw_iron")) {
            ensureItem("iron_ore", needed);
            virtualInventory.put(item, current + needed);
        } else if (item.equals("diamond")) {
            ensureItem("iron_pickaxe", 1);
            actionQueue.add(new BaritoneCommandAction("mine " + amount + " diamond_ore deepslate_diamond_ore"));
            virtualInventory.put(item, current + needed);
        } else if (item.equals("planks")) {
            int logsNeeded = (needed + 3) / 4;
            ensureItem("log", logsNeeded);
            actionQueue.add(new CraftAction("planks", logsNeeded * 4));
            virtualInventory.put("log", virtualInventory.getOrDefault("log", 0) - logsNeeded);
            virtualInventory.put(item, current + (logsNeeded * 4));
        } else if (item.endsWith("_planks")) {
            int logsNeeded = (needed + 3) / 4;
            String requiredLog = AutoCrafter.getLogForPlanks(item);
            ensureItem(requiredLog, logsNeeded);
            actionQueue.add(new CraftAction(item, logsNeeded * 4));
            virtualInventory.put(requiredLog, virtualInventory.getOrDefault(requiredLog, 0) - logsNeeded);
            virtualInventory.put(item, current + (logsNeeded * 4));
        } else if (item.equals("stick")) {
            int craftRuns = (needed + 3) / 4;
            ensureItem("planks", craftRuns * 2);
            actionQueue.add(new CraftAction("stick", craftRuns * 4));
            virtualInventory.put("planks", virtualInventory.getOrDefault("planks", 0) - (craftRuns * 2));
            virtualInventory.put(item, current + (craftRuns * 4));
        } else if (item.equals("crafting_table")) {
            ensureItem("planks", needed * 4);
            actionQueue.add(new CraftAction("crafting_table", needed));
            virtualInventory.put("planks", virtualInventory.getOrDefault("planks", 0) - (needed * 4));
            virtualInventory.put(item, current + needed);
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
            
            ensureItem(material, needed * materialCount);
            virtualInventory.put(material, virtualInventory.getOrDefault(material, 0) - (needed * materialCount));
            
            ensureItem("stick", needed * stickCount);
            virtualInventory.put("stick", virtualInventory.getOrDefault("stick", 0) - (needed * stickCount));
            
            ensureCraftingTablePlaced();
            actionQueue.add(new CraftAction(item, needed));
            virtualInventory.put(item, current + needed);
        } else if (item.equals("furnace")) {
            ensureItem("cobblestone", needed * 8);
            ensureCraftingTablePlaced();
            actionQueue.add(new CraftAction("furnace", needed));
            virtualInventory.put("cobblestone", virtualInventory.getOrDefault("cobblestone", 0) - (needed * 8));
            virtualInventory.put(item, current + needed);
        } else if (item.equals("iron_ingot")) {
            ensureItem("raw_iron", needed);
            virtualInventory.put("raw_iron", virtualInventory.getOrDefault("raw_iron", 0) - needed);
            
            ensureItem("coal", needed);
            virtualInventory.put("coal", virtualInventory.getOrDefault("coal", 0) - needed);
            
            ensureFurnacePlaced();
            actionQueue.add(new SmeltAction("raw_iron", needed, "coal", needed));
            actionQueue.add(new CollectSmeltingResultAction("iron_ingot", needed));
            virtualInventory.put(item, current + needed);
        } else if (item.equals("obsidian")) {
            ensureItem("diamond_pickaxe", 1);
            actionQueue.add(new BaritoneCommandAction("mine " + amount + " obsidian"));
            virtualInventory.put(item, current + needed);
        } else if (item.equals("flint")) {
            actionQueue.add(new MineGravelAction(amount));
            virtualInventory.put(item, current + needed);
        } else if (item.equals("flint_and_steel")) {
            ensureItem("iron_ingot", needed);
            virtualInventory.put("iron_ingot", virtualInventory.getOrDefault("iron_ingot", 0) - needed);
            ensureItem("flint", needed);
            virtualInventory.put("flint", virtualInventory.getOrDefault("flint", 0) - needed);
            ensureCraftingTablePlaced();
            actionQueue.add(new CraftAction("flint_and_steel", needed));
            virtualInventory.put(item, current + needed);
        } else if (item.equals("nether_portal")) {
            ensureItem("diamond_pickaxe", 1);
            ensureItem("obsidian", 10);
            ensureItem("flint_and_steel", 1);
            actionQueue.add(new BuildPortalAction());
            virtualInventory.put(item, current + needed);
        } else {
            // Generic fallback
            ensureCraftingTablePlaced();
            actionQueue.add(new CraftAction(item, needed));
            virtualInventory.put(item, current + needed);
        }
    }

    private void ensureCraftingTablePlaced() {
        BlockPos tablePos = AutoCrafter.findNearbyBlock(client, Blocks.CRAFTING_TABLE, 25);
        if (tablePos != null || virtualCraftingTablePlaced) {
            actionQueue.add(new BaritoneCommandAction("goto crafting_table"));
        } else {
            int tables = virtualInventory.getOrDefault("crafting_table", 0);
            if (tables == 0) {
                ensureItem("crafting_table", 1);
            }
            actionQueue.add(new PlaceAction("crafting_table"));
            actionQueue.add(new OpenCraftingTableAction());
            virtualCraftingTablePlaced = true;
        }
    }

    private void ensureFurnacePlaced() {
        BlockPos furnacePos = AutoCrafter.findNearbyBlock(client, Blocks.FURNACE, 25);
        if (furnacePos != null || virtualFurnacePlaced) {
            actionQueue.add(new BaritoneCommandAction("goto furnace"));
        } else {
            int furnaces = virtualInventory.getOrDefault("furnace", 0);
            if (furnaces == 0) {
                ensureItem("furnace", 1);
            }
            actionQueue.add(new PlaceAction("furnace"));
            actionQueue.add(new OpenFurnaceAction());
            virtualFurnacePlaced = true;
        }
    }

    public void prependAction(Action action) {
        ((LinkedList<Action>) actionQueue).addFirst(action);
    }

    public boolean hasMoreActions() {
        return !actionQueue.isEmpty();
    }

    public Action getNextAction() {
        return actionQueue.poll();
    }

    public Action peekNextAction() {
        return actionQueue.peek();
    }

    public String getTargetItem() {
        return targetItem;
    }

    public int getTargetAmount() {
        return targetAmount;
    }

    public boolean requiresCobblestone() {
        if (targetItem == null) return false;
        String item = targetItem;
        return item.contains("stone") 
                || item.contains("iron") 
                || item.contains("diamond") 
                || item.equals("cobblestone") 
                || item.equals("furnace")
                || item.equals("obsidian")
                || item.equals("flint_and_steel")
                || item.equals("nether_portal");
    }
}
