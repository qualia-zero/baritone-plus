package baritone.plus.task;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.plus.util.AutoCrafter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

public class TaskManager {

    private CraftingTask currentTask = null;
    private int tickDelay = 0;

    private String lastTargetItem = null;
    private int lastTargetAmount = 0;
    private int retryDelay = 0;
    private int retryCount = 0;
    private static final int MAX_RETRIES = 3;

    private java.util.List<net.minecraft.world.item.Item> originalThrowawayItems = null;
    private boolean minedStoneDuringTask = false;
    private boolean wasBusyLastTick = false;

    private void updateThrowawaySettings(Minecraft client, boolean disableCobble) {
        var setting = BaritoneAPI.getSettings().acceptableThrowawayItems;
        if (disableCobble) {
            if (originalThrowawayItems == null) {
                originalThrowawayItems = new java.util.ArrayList<>(setting.value);
                java.util.List<net.minecraft.world.item.Item> newItems = new java.util.ArrayList<>(setting.value);
                if (newItems.remove(net.minecraft.world.item.Items.COBBLESTONE)) {
                    setting.value = newItems;
                    AutoCrafter.log(client, "§eTemporarily protecting Cobblestone from being used as a throwaway block.");
                }
            }
        } else {
            if (originalThrowawayItems != null) {
                setting.value = originalThrowawayItems;
                originalThrowawayItems = null;
                minedStoneDuringTask = false;
            }
        }
    }

    private void stopBaritoneAndReleaseKeys(Minecraft client, IBaritone baritone) {
        if (baritone != null) {
            baritone.getPathingBehavior().cancelEverything();
            baritone.getInputOverrideHandler().clearAllKeys();
        }
        if (client != null && client.options != null) {
            client.options.keyAttack.setDown(false);
            client.options.keyUse.setDown(false);
            client.options.keyUp.setDown(false);
            client.options.keyDown.setDown(false);
            client.options.keyLeft.setDown(false);
            client.options.keyRight.setDown(false);
            client.options.keyJump.setDown(false);
            client.options.keyShift.setDown(false);
        }
    }

    public void startTask(CraftingTask task) {
        startTask(task, false);
    }

    public void startTask(CraftingTask task, boolean isRetry) {
        this.currentTask = task;
        this.tickDelay = 20; // 1 second delay before starting
        this.lastTargetItem = task.getTargetItem();
        this.lastTargetAmount = task.getTargetAmount();
        this.retryDelay = 0;
        if (!isRetry) {
            this.retryCount = 0;
        }
        this.minedStoneDuringTask = false;
        this.wasBusyLastTick = false;
        stopBaritoneAndReleaseKeys(Minecraft.getInstance(), BaritoneAPI.getProvider().getPrimaryBaritone());
        updateThrowawaySettings(Minecraft.getInstance(), false);
    }

    public void cancelTask() {
        this.currentTask = null;
        this.tickDelay = 0;
        this.lastTargetItem = null;
        this.lastTargetAmount = 0;
        this.retryDelay = 0;
        this.retryCount = 0;
        this.minedStoneDuringTask = false;
        this.wasBusyLastTick = false;
        stopBaritoneAndReleaseKeys(Minecraft.getInstance(), BaritoneAPI.getProvider().getPrimaryBaritone());
        updateThrowawaySettings(Minecraft.getInstance(), false);
    }

    public void prependAction(Action action) {
        if (currentTask != null) {
            currentTask.prependAction(action);
        }
    }

    public void setTickDelay(int ticks) {
        this.tickDelay = ticks;
    }

    public void failTask() {
        stopBaritoneAndReleaseKeys(Minecraft.getInstance(), BaritoneAPI.getProvider().getPrimaryBaritone());
        updateThrowawaySettings(Minecraft.getInstance(), false);
        this.minedStoneDuringTask = false;
        this.wasBusyLastTick = false;
        if (lastTargetItem != null && retryCount < MAX_RETRIES) {
            AutoCrafter.log(Minecraft.getInstance(), "§eTask failed. Retrying in 5 seconds... (Attempt " + (retryCount + 1) + "/" + MAX_RETRIES + ")");
            this.retryDelay = 100; // 5 seconds
            this.retryCount++;
            this.currentTask = null;
            this.tickDelay = 0;
        } else {
            if (retryCount >= MAX_RETRIES) {
                AutoCrafter.log(Minecraft.getInstance(), "§cTask failed: Maximum retries reached.");
            }
            cancelTask();
        }
    }

    public void tick(Minecraft client) {
        // Handle screen close delay
        if (AutoCrafter.screenCloseDelay > 0) {
            AutoCrafter.screenCloseDelay--;
            if (AutoCrafter.screenCloseDelay == 0) {
                if (client.gui.screen() instanceof InventoryScreen) {
                    client.gui.setScreen(null);
                }
            }
        }

        if (retryDelay > 0) {
            retryDelay--;
            if (retryDelay == 0 && lastTargetItem != null) {
                AutoCrafter.log(client, "§eRestarting task to craft " + lastTargetAmount + "x " + lastTargetItem + "...");
                startTask(new CraftingTask(client, lastTargetItem, lastTargetAmount), true);
            }
            return;
        }

        if (currentTask == null) return;

        if (tickDelay > 0) {
            tickDelay--;
            return;
        }

        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();

        // Check if Baritone is busy
        boolean isBusy = baritone.getPathingBehavior().isPathing() 
                || baritone.getMineProcess().isActive() 
                || baritone.getGetToBlockProcess().isActive()
                || baritone.getFollowProcess().isActive()
                || baritone.getCustomGoalProcess().isActive()
                || baritone.getBuilderProcess().isActive();

        if (isBusy) {
            if (currentTask != null && currentTask.hasMoreActions()) {
                Action peekAction = currentTask.peekNextAction();
                if (peekAction instanceof MineGravelAction) {
                    int currentFlint = AutoCrafter.getItemCount(client, "flint");
                    int required = ((MineGravelAction) peekAction).getRequiredFlint();
                    if (currentFlint >= required) {
                        stopBaritoneAndReleaseKeys(client, baritone);
                        isBusy = false;
                    }
                }
            }
        }

        if (isBusy) {
            wasBusyLastTick = true;
            return; // Wait until Baritone finishes current action
        }

        if (wasBusyLastTick) {
            wasBusyLastTick = false;
            tickDelay = 10; // Wait 10 ticks (0.5 seconds) for GUI to open or state to settle
            return;
        }

        // Baritone is not busy, ensure throwaway settings are restored
        updateThrowawaySettings(client, false);

        // If baritone is idle, pop the next action
        if (!currentTask.hasMoreActions()) {
            AutoCrafter.log(client, "§aTask finished!");
            if (client.player != null) {
                client.player.closeContainer();
            }
            client.gui.setScreen(null);
            updateThrowawaySettings(client, false);
            currentTask = null;
            return;
        }

        Action nextAction = currentTask.getNextAction();
        
        if (nextAction != null) {
            // Skip redundant screen opening actions if the corresponding GUI is already open
            if (nextAction instanceof OpenCraftingTableAction && AutoCrafter.isCraftingScreenOpen(client)) {
                tick(client);
                return;
            }
            if (nextAction instanceof OpenFurnaceAction && AutoCrafter.isFurnaceScreenOpen(client)) {
                tick(client);
                return;
            }

            if (nextAction instanceof BaritoneCommandAction || nextAction instanceof PlaceAction) {
                if (client.gui.screen() != null) {
                    client.gui.setScreen(null);
                }
            }

            // Temporarily protect Cobblestone before starting a 'goto' Baritone command after mining stone
            if (nextAction instanceof BaritoneCommandAction) {
                String cmd = ((BaritoneCommandAction) nextAction).getCommand();
                if (cmd != null) {
                    if (cmd.contains("mine") && cmd.contains("stone")) {
                        minedStoneDuringTask = true;
                    }
                    boolean isGoto = cmd.startsWith("goto");
                    boolean hasCobble = AutoCrafter.getItemCount(client, "cobblestone") > 0;
                    if (isGoto && currentTask.requiresCobblestone() && hasCobble && minedStoneDuringTask) {
                        updateThrowawaySettings(client, true);
                    }
                }
            }

            if (nextAction.shouldLog()) {
                AutoCrafter.log(client, "§e" + nextAction.getDescription());
            }
            if (!(nextAction instanceof BaritoneCommandAction)) {
                stopBaritoneAndReleaseKeys(client, baritone);
            }
            nextAction.execute(client, baritone);
            if (currentTask == null) {
                return;
            }
            if (tickDelay == 0) {
                if (!currentTask.hasMoreActions()) {
                    tickDelay = 5; // very short delay to let server process final craft/smelt before closing GUI
                } else if (nextAction instanceof CraftAction || nextAction instanceof SmeltAction || nextAction instanceof CollectSmeltingResultAction) {
                    tickDelay = 15; // 0.75 seconds is plenty for inventory actions
                } else {
                    tickDelay = 40; // 2 seconds for commands/placing
                }
            }
        }
    }
}
