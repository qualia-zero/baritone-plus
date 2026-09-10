package baritone.plus.task;

import baritone.plus.BaritonePlusClient;
import baritone.plus.util.AutoCrafter;
import baritone.api.IBaritone;
import net.minecraft.client.Minecraft;

public class CollectSmeltingResultAction implements Action {

    private final String item;
    private final int amount;
    private int attempts = 0;
    private static final int MAX_ATTEMPTS = 30; // Max 60 seconds (30 attempts * 2 seconds)

    public CollectSmeltingResultAction(String item, int amount) {
        this.item = item;
        this.amount = amount;
    }

    @Override
    public void execute(Minecraft client, IBaritone baritone) {
        if (!AutoCrafter.isFurnaceScreenOpen(client)) {
            AutoCrafter.log(client, "Error: Furnace screen not open while waiting for result!");
            BaritonePlusClient.TASK_MANAGER.failTask();
            return;
        }

        attempts++;
        if (attempts > MAX_ATTEMPTS) {
            AutoCrafter.log(client, "Error: Smelting timed out after 60 seconds!");
            BaritonePlusClient.TASK_MANAGER.failTask();
            return;
        }

        // Take whatever is in slot 2 (result slot)
        if (client.player.containerMenu.getSlot(2).hasItem()) {
            AutoCrafter.takeResult(client, 2);
        }

        // Check if there is still raw iron being smelted
        boolean stillSmelting = client.player.containerMenu.getSlot(0).hasItem() 
                || client.player.containerMenu.getSlot(2).hasItem();

        if (stillSmelting) {
            // Re-queue this action to check again in 2 seconds
            BaritonePlusClient.TASK_MANAGER.prependAction(this);
            BaritonePlusClient.TASK_MANAGER.setTickDelay(40);
        }
    }

    @Override
    public String getDescription() {
        return "Waiting for smelting result: " + amount + "x " + item;
    }

    @Override
    public boolean shouldLog() {
        return attempts == 0;
    }
}
