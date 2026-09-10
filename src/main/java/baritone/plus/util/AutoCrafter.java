package baritone.plus.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.FurnaceScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

public class AutoCrafter {

    public static int screenCloseDelay = -1;

    public static void clickSlot(Minecraft client, int syncId, int slotId, int button, ContainerInput actionType) {
        if (client.gameMode != null && client.player != null) {
            client.gameMode.handleContainerInput(syncId, slotId, button, actionType, client.player);
        }
    }

    public static Item getItem(String itemName) {
        if (itemName == null) return null;
        try {
            Identifier id = Identifier.parse(itemName.contains(":") ? itemName : "minecraft:" + itemName);
            return BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    public static int findItemSlot(Minecraft client, String itemName, int startIndex, int endIndex) {
        if (client.player == null || client.player.containerMenu == null) return -1;
        
        int totalSlots = client.player.containerMenu.slots.size();
        int actualEnd = Math.min(endIndex, totalSlots);
        
        if (itemName.equals("log")) {
            for (int i = startIndex; i < actualEnd; i++) {
                if (client.player.containerMenu.getSlot(i).getItem().is(net.minecraft.tags.ItemTags.LOGS)) {
                    return i;
                }
            }
            return -1;
        }
        if (itemName.equals("planks")) {
            for (int i = startIndex; i < actualEnd; i++) {
                if (client.player.containerMenu.getSlot(i).getItem().is(net.minecraft.tags.ItemTags.PLANKS)) {
                    return i;
                }
            }
            return -1;
        }

        Item targetItem = getItem(itemName);
        if (targetItem == null) return -1;
        
        for (int i = startIndex; i < actualEnd; i++) {
            if (client.player.containerMenu.getSlot(i).getItem().is(targetItem)) {
                return i;
            }
        }
        return -1;
    }

    public static boolean isCraftingScreenOpen(Minecraft client) {
        return client.gui.screen() instanceof CraftingScreen;
    }

    public static boolean isFurnaceScreenOpen(Minecraft client) {
        return client.gui.screen() instanceof FurnaceScreen;
    }
    
    // Moves an item from inventory (searchSlots) to a specific targetSlot
    public static boolean moveItemToSlot(Minecraft client, String itemName, int searchStart, int searchEnd, int targetSlot) {
        int itemSlot = findItemSlot(client, itemName, searchStart, searchEnd);
        if (itemSlot == -1) return false;

        int syncId = client.player.containerMenu.containerId;

        // Pick up item
        clickSlot(client, syncId, itemSlot, 0, ContainerInput.PICKUP);
        // Place one item in target slot
        clickSlot(client, syncId, targetSlot, 1, ContainerInput.PICKUP); // Right click to place 1
        // Put the rest back
        clickSlot(client, syncId, itemSlot, 0, ContainerInput.PICKUP);
        
        return true;
    }

    public static void takeResult(Minecraft client, int resultSlot) {
        if (client.player == null) return;
        int syncId = client.player.containerMenu.containerId;
        // Shift click result to move it to inventory
        clickSlot(client, syncId, resultSlot, 0, ContainerInput.QUICK_MOVE);
    }

    public static int getTagItemCount(Minecraft client, net.minecraft.tags.TagKey<Item> tagKey) {
        if (client.player == null) return 0;
        int count = 0;
        for (int i = 0; i < client.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = client.player.getInventory().getItem(i);
            if (stack.is(tagKey)) {
                count += stack.getCount();
            }
        }
        return count;
    }
 
    public static int getItemCount(Minecraft client, String itemName) {
        if (client.player == null) return 0;
        if (itemName.equals("log")) {
            return getTagItemCount(client, net.minecraft.tags.ItemTags.LOGS);
        }
        if (itemName.equals("planks")) {
            return getTagItemCount(client, net.minecraft.tags.ItemTags.PLANKS);
        }
        Item targetItem = getItem(itemName);
        if (targetItem == null) return 0;
        
        int count = 0;
        for (int i = 0; i < client.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = client.player.getInventory().getItem(i);
            if (stack.is(targetItem)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public static BlockPos findNearbyBlock(Minecraft client, Block block, int range) {
        if (client.player == null || client.level == null) return null;
        BlockPos playerPos = client.player.blockPosition();
        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    BlockPos pos = playerPos.offset(x, y, z);
                    if (client.level.getBlockState(pos).is(block)) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    public static int getSelectedSlot(Minecraft client) {
        if (client.player == null) return 0;
        try {
            java.lang.reflect.Field field = net.minecraft.world.entity.player.Inventory.class.getDeclaredField("selected");
            field.setAccessible(true);
            return field.getInt(client.player.getInventory());
        } catch (Exception e) {
            try {
                java.lang.reflect.Field field = net.minecraft.world.entity.player.Inventory.class.getDeclaredField("selectedSlot");
                field.setAccessible(true);
                return field.getInt(client.player.getInventory());
            } catch (Exception ex) {
                return 0;
            }
        }
    }

    public static void setSelectedSlot(Minecraft client, int slot) {
        if (client.player == null) return;
        try {
            java.lang.reflect.Field field = net.minecraft.world.entity.player.Inventory.class.getDeclaredField("selected");
            field.setAccessible(true);
            field.setInt(client.player.getInventory(), slot);
        } catch (Exception e) {
            try {
                java.lang.reflect.Field field = net.minecraft.world.entity.player.Inventory.class.getDeclaredField("selectedSlot");
                field.setAccessible(true);
                field.setInt(client.player.getInventory(), slot);
            } catch (Exception ex) {
                // Ignore
            }
        }
        if (client.player.connection != null) {
            client.player.connection.send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(slot));
        }
    }

    public static boolean selectItemInHotbar(Minecraft client, String itemName) {
        if (client.player == null) return false;
        Item targetItem = getItem(itemName);
        if (targetItem == null) return false;

        // Search hotbar (0-8 in Inventory)
        for (int i = 0; i < 9; i++) {
            if (client.player.getInventory().getItem(i).is(targetItem)) {
                setSelectedSlot(client, i);
                return true;
            }
        }

        // If not in hotbar, search main inventory (9-35 in Inventory)
        for (int i = 9; i < 36; i++) {
            if (client.player.getInventory().getItem(i).is(targetItem)) {
                if (client.gui.screen() == null) {
                    client.gui.setScreen(new InventoryScreen(client.player));
                }
                int selectedSlot = getSelectedSlot(client);
                int menuInvSlot = i;
                int syncId = client.player.containerMenu.containerId;
                clickSlot(client, syncId, menuInvSlot, selectedSlot, ContainerInput.SWAP);
                screenCloseDelay = 5; // Close the screen in 5 ticks
                return true;
            }
        }
        return false;
    }

    public static boolean moveItemToSlots(Minecraft client, String itemName, int searchStart, int searchEnd, int[] targetSlots) {
        if (client.player == null || client.player.containerMenu == null) return false;
        int syncId = client.player.containerMenu.containerId;

        for (int targetSlot : targetSlots) {
            int itemSlot = findItemSlot(client, itemName, searchStart, searchEnd);
            if (itemSlot == -1) return false;

            // Pick up item stack
            clickSlot(client, syncId, itemSlot, 0, ContainerInput.PICKUP);
            // Place 1 item in target slot
            clickSlot(client, syncId, targetSlot, 1, ContainerInput.PICKUP);
            // Put the rest back in the original slot
            clickSlot(client, syncId, itemSlot, 0, ContainerInput.PICKUP);
        }
        
        return true;
    }

    public static void log(Minecraft client, String message) {
        if (client.player != null) {
            String cleanMessage = message.replaceAll("§[0-9a-fA-Fk-oK-OrR]", "");
            client.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§b[Baritone+] §7" + cleanMessage));
        }
    }

    public static void lookAt(Minecraft client, Vec3 target) {
        if (client.player == null) return;
        Vec3 playerEyePos = client.player.getEyePosition(1.0F);
        double dx = target.x - playerEyePos.x;
        double dy = target.y - playerEyePos.y;
        double dz = target.z - playerEyePos.z;
        double r = Math.sqrt(dx * dx + dz * dz);
        
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, r));
        
        client.player.setYRot(yaw);
        client.player.setXRot(pitch);
    }

    public static String findNearbyLogBlock(Minecraft client, int range) {
        if (client.player == null || client.level == null) return null;
        BlockPos playerPos = client.player.blockPosition();
        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    BlockPos pos = playerPos.offset(x, y, z);
                    var state = client.level.getBlockState(pos);
                    if (state.is(net.minecraft.tags.BlockTags.LOGS)) {
                        var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                        if (id != null) {
                            String path = id.getPath();
                            if ((path.endsWith("_log") || path.endsWith("_stem")) && !path.startsWith("stripped_")) {
                                return path;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    public static String getMineLogsCommand(Minecraft client, int count) {
        // 1. Check inventory for any existing log
        if (client.player != null) {
            for (int i = 0; i < client.player.getInventory().getContainerSize(); i++) {
                ItemStack stack = client.player.getInventory().getItem(i);
                if (stack.is(net.minecraft.tags.ItemTags.LOGS)) {
                    var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    if (id != null) {
                        String path = id.getPath();
                        if ((path.endsWith("_log") || path.endsWith("_stem")) && !path.startsWith("stripped_")) {
                            return "mine " + count + " " + path;
                        }
                    }
                }
            }
        }

        // 2. Search nearby blocks for any log
        String nearbyLog = findNearbyLogBlock(client, 32);
        if (nearbyLog != null) {
            return "mine " + count + " " + nearbyLog;
        }

        // 3. Fallback to oak_log
        return "mine " + count + " oak_log";
    }

    public static boolean isItemInHotbar(Minecraft client, String itemName) {
        if (client.player == null) return false;
        Item targetItem = getItem(itemName);
        if (targetItem == null) return false;
        for (int i = 0; i < 9; i++) {
            if (client.player.getInventory().getItem(i).is(targetItem)) {
                return true;
            }
        }
        return false;
    }

    public static void ensureItemInHotbar(Minecraft client, String itemName, boolean isCraftingScreen) {
        if (client.player == null) return;
        
        Item targetItem = getItem(itemName);
        if (targetItem == null) return;

        // 1. Check if it's already in the hotbar
        for (int i = 0; i < 9; i++) {
            if (client.player.getInventory().getItem(i).getItem() == targetItem) {
                return; // Already in hotbar!
            }
        }

        // 2. Find it in the main inventory slots of the current screen container
        if (client.player.containerMenu == null) return;
        int totalSlots = client.player.containerMenu.slots.size();
        int searchStart = isCraftingScreen ? 10 : 9;
        int searchEnd = Math.min(isCraftingScreen ? 37 : 36, totalSlots);
        int toolSlot = -1;
        for (int i = searchStart; i < searchEnd; i++) {
            if (client.player.containerMenu.getSlot(i).getItem().is(targetItem)) {
                toolSlot = i;
                break;
            }
        }

        if (toolSlot != -1) {
            // 3. Find an empty hotbar slot or best slot to swap
            int targetHotbarSlot = -1;
            for (int i = 0; i < 9; i++) {
                if (client.player.getInventory().getItem(i).isEmpty()) {
                    targetHotbarSlot = i;
                    break;
                }
            }
            if (targetHotbarSlot == -1) {
                targetHotbarSlot = findBestHotbarSlotToSwap(client);
            }

            // 4. Swap it to the hotbar slot
            int syncId = client.player.containerMenu.containerId;
            clickSlot(client, syncId, toolSlot, targetHotbarSlot, ContainerInput.SWAP);
        }
    }

    private static int findBestHotbarSlotToSwap(Minecraft client) {
        if (client.player == null) return 0;
        
        // 1. First choice: any empty slot
        for (int i = 0; i < 9; i++) {
            if (client.player.getInventory().getItem(i).isEmpty()) {
                return i;
            }
        }
        
        // 2. Second choice: throwaway items (cobblestone, dirt, gravel, sand, etc.)
        for (int i = 0; i < 9; i++) {
            ItemStack stack = client.player.getInventory().getItem(i);
            Item item = stack.getItem();
            var id = BuiltInRegistries.ITEM.getKey(item);
            if (id != null) {
                String path = id.getPath();
                if (path.equals("cobblestone") || path.equals("dirt") || path.equals("gravel") 
                        || path.equals("sand") || path.equals("stone") || path.equals("andesite") 
                        || path.equals("diorite") || path.equals("granite") || path.equals("netherrack")) {
                    return i;
                }
            }
        }

        // 3. Third choice: any item that is not a tool or food
        for (int i = 0; i < 9; i++) {
            ItemStack stack = client.player.getInventory().getItem(i);
            Item item = stack.getItem();
            var id = BuiltInRegistries.ITEM.getKey(item);
            if (id != null) {
                String path = id.getPath();
                boolean isTool = path.endsWith("_pickaxe") || path.endsWith("_axe") || path.endsWith("_shovel") 
                        || path.endsWith("_hoe") || path.endsWith("_sword") || path.equals("flint_and_steel") 
                        || path.equals("shears") || path.equals("bucket") || path.equals("water_bucket") 
                        || path.equals("lava_bucket");
                boolean isFood = path.equals("apple") || path.equals("bread") || path.equals("cooked_beef") 
                        || path.equals("cooked_chicken") || path.equals("cooked_porkchop") || path.equals("cooked_mutton") 
                        || path.equals("cooked_rabbit") || path.equals("cooked_cod") || path.equals("cooked_salmon") 
                        || path.equals("golden_carrot") || path.equals("baked_potato") || path.equals("melon_slice") 
                        || path.equals("carrot") || path.equals("potato") || path.equals("beef") 
                        || path.equals("chicken") || path.equals("porkchop") || path.equals("mutton") 
                        || path.equals("rabbit") || path.equals("rotten_flesh");
                if (!isTool && !isFood) {
                    return i;
                }
            }
        }
        
        // 4. Fourth choice: fallback to slot 0
        return 0;
    }

    public static String getLogForPlanks(String planksName) {
        if (planksName.equals("crimson_planks")) return "crimson_stem";
        if (planksName.equals("warped_planks")) return "warped_stem";
        if (planksName.endsWith("_planks")) {
            return planksName.substring(0, planksName.length() - "planks".length()) + "log";
        }
        return "log";
    }
}

