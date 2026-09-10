package baritone.plus.task;

import baritone.plus.BaritonePlusClient;
import baritone.plus.util.AutoCrafter;
import baritone.api.IBaritone;
import baritone.api.utils.Helper;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class SmeltAction implements Action {

    private final String item;
    private final int amount;
    private final String fuel;
    private final int fuelAmount;

    public SmeltAction(String item, int amount, String fuel, int fuelAmount) {
        this.item = item;
        this.amount = amount;
        this.fuel = fuel;
        this.fuelAmount = fuelAmount;
    }

    @Override
    public void execute(Minecraft client, IBaritone baritone) {
        if (!AutoCrafter.isFurnaceScreenOpen(client)) {
            AutoCrafter.log(client, "Error: Furnace screen not open!");
            BaritonePlusClient.TASK_MANAGER.failTask();
            return;
        }

        // Furnace slots:
        // 0: Ingredient
        // 1: Fuel
        // 2: Result
        // 3-38: Inventory

        for (int i = 0; i < amount; i++) {
            AutoCrafter.moveItemToSlot(client, item, 3, 39, 0);
        }
        for (int i = 0; i < fuelAmount; i++) {
            AutoCrafter.moveItemToSlot(client, fuel, 3, 39, 1);
        }

        // We can't instantly take result, smelting takes time.
        // A full implementation would wait for the progress bar to finish.
        // For now, we assume the user/task manager handles the wait or takes it later.
    }

    @Override
    public String getDescription() {
        return "Smelting " + amount + "x " + item;
    }
}
