package baritone.plus.combat;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.plus.config.BaritonePlusConfig;
import baritone.plus.util.AutoCrafter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

/**
 * Meteor-grade standalone Auto-Eat controller.
 * Works independently of AI and CombatController, with food priority scoring,
 * threshold modes, blacklist filtering, inventory search, and Baritone pause/resume.
 */
public class AutoEatController {

    private static final int OFFHAND_SLOT = -2;

    private boolean eating = false;
    private int activeSlot = -1;
    private int prevHotbarSlot = -1;
    private boolean pausedBaritone = false;

    public boolean isEating() {
        return eating;
    }

    public void tick(Minecraft client) {
        if (client == null || client.player == null || client.level == null || client.gameMode == null) {
            if (eating) stopEating(client);
            return;
        }

        BaritonePlusConfig.AutoEatConfig cfg = BaritonePlusConfig.get().autoEat;
        if (!cfg.enabled) {
            if (eating) stopEating(client);
            return;
        }

        // Anti-cheat safety: never eat while a container GUI is open
        if (client.gui.screen() instanceof AbstractContainerScreen<?>) {
            if (eating) stopEating(client);
            return;
        }

        if (eating) {
            if (!shouldEat(client, cfg)) {
                stopEating(client);
                return;
            }

            ItemStack currentStack = getStackAtSlot(client.player, activeSlot);
            if (!isEdible(client.player, currentStack, cfg)) {
                int newSlot = findBestFoodSlot(client.player, cfg);
                if (newSlot == -1) {
                    stopEating(client);
                    return;
                }
                if (!prepareSlot(client, newSlot)) {
                    stopEating(client);
                    return;
                }
            }

            eat(client);
            return;
        }

        if (shouldEat(client, cfg)) {
            int foodSlot = findBestFoodSlot(client.player, cfg);
            if (foodSlot != -1) {
                startEating(client, cfg, foodSlot);
            }
        }
    }

    private void startEating(Minecraft client, BaritonePlusConfig.AutoEatConfig cfg, int foodSlot) {
        prevHotbarSlot = client.player.getInventory().getSelectedSlot();
        if (!prepareSlot(client, foodSlot)) {
            prevHotbarSlot = -1;
            return;
        }

        if (cfg.pauseBaritone && !pausedBaritone && isBaritoneActive()) {
            pausedBaritone = true;
            BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("pause");
        }

        eat(client);
    }

    private void eat(Minecraft client) {
        if (activeSlot >= 0 && activeSlot < 9) {
            client.player.getInventory().setSelectedSlot(activeSlot);
        }
        client.options.keyUse.setDown(true);
        if (!client.player.isUsingItem()) {
            InteractionHand hand = (activeSlot == OFFHAND_SLOT) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            client.gameMode.useItem(client.player, hand);
        }
        eating = true;
    }

    private void stopEating(Minecraft client) {
        if (client != null && client.options != null) {
            client.options.keyUse.setDown(false);
        }
        if (client != null && client.player != null && prevHotbarSlot >= 0 && prevHotbarSlot < 9) {
            client.player.getInventory().setSelectedSlot(prevHotbarSlot);
        }

        eating = false;
        activeSlot = -1;
        prevHotbarSlot = -1;

        if (pausedBaritone) {
            pausedBaritone = false;
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (baritone != null) {
                baritone.getCommandManager().execute("resume");
            }
        }
    }

    private boolean prepareSlot(Minecraft client, int slot) {
        if (slot == OFFHAND_SLOT) {
            activeSlot = OFFHAND_SLOT;
            return true;
        }
        if (slot >= 0 && slot < 9) {
            client.player.getInventory().setSelectedSlot(slot);
            activeSlot = slot;
            return true;
        }
        // Main inventory slot (9..35): move to an empty hotbar slot
        int emptyHotbar = -1;
        for (int i = 0; i < 9; i++) {
            if (client.player.getInventory().getItem(i).isEmpty()) {
                emptyHotbar = i;
                break;
            }
        }
        if (emptyHotbar == -1 || client.player.inventoryMenu == null) {
            return false;
        }
        int syncId = client.player.inventoryMenu.containerId;
        AutoCrafter.clickSlot(client, syncId, slot, emptyHotbar, ContainerInput.SWAP);
        client.player.getInventory().setSelectedSlot(emptyHotbar);
        activeSlot = emptyHotbar;
        return true;
    }

    private boolean shouldEat(Minecraft client, BaritonePlusConfig.AutoEatConfig cfg) {
        Player player = client.player;
        boolean healthLow = player.getHealth() <= cfg.healthThreshold;
        boolean hungerLow = player.getFoodData().getFoodLevel() <= cfg.hungerThreshold;

        return switch (cfg.thresholdMode) {
            case HEALTH -> healthLow;
            case HUNGER -> hungerLow;
            case BOTH -> healthLow && hungerLow;
            case ANY -> healthLow || hungerLow;
        };
    }

    private int findBestFoodSlot(Player player, BaritonePlusConfig.AutoEatConfig cfg) {
        if (isEdible(player, player.getOffhandItem(), cfg)) {
            return OFFHAND_SLOT;
        }
        int hotbarBest = findBestInRange(player, 0, 8, cfg);
        if (hotbarBest != -1) {
            return hotbarBest;
        }
        if (cfg.searchInventory) {
            return findBestInRange(player, 9, 35, cfg);
        }
        return -1;
    }

    private int findBestInRange(Player player, int start, int end, BaritonePlusConfig.AutoEatConfig cfg) {
        int bestSlot = -1;
        float bestScore = -1.0f;

        for (int i = start; i <= end; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!isEdible(player, stack, cfg)) continue;
            FoodProperties food = stack.get(DataComponents.FOOD);
            if (food == null) continue;

            float score = (cfg.priority == BaritonePlusConfig.FoodPriority.SATURATION)
                    ? food.saturation()
                    : food.nutrition();
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private boolean isEdible(Player player, ItemStack stack, BaritonePlusConfig.AutoEatConfig cfg) {
        if (stack == null || stack.isEmpty()) return false;
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food == null) return false;

        String itemPath = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (cfg.blacklist.contains(itemPath)) {
            return false;
        }
        return player.getFoodData().needsFood() || food.canAlwaysEat();
    }

    private ItemStack getStackAtSlot(Player player, int slot) {
        if (slot == OFFHAND_SLOT) return player.getOffhandItem();
        if (slot >= 0 && slot < player.getInventory().getContainerSize()) {
            return player.getInventory().getItem(slot);
        }
        return ItemStack.EMPTY;
    }

    private boolean isBaritoneActive() {
        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        return baritone != null && (
                baritone.getPathingBehavior().isPathing()
                        || baritone.getMineProcess().isActive()
                        || baritone.getFollowProcess().isActive()
                        || baritone.getCustomGoalProcess().isActive()
                        || baritone.getBuilderProcess().isActive()
                        || baritone.getExploreProcess().isActive()
                        || baritone.getFarmProcess().isActive()
        );
    }
}
