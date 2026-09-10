package baritone.plus.task;

import baritone.plus.BaritonePlusClient;
import baritone.plus.util.AutoCrafter;
import baritone.api.IBaritone;
import net.minecraft.client.Minecraft;

public class MineGravelAction implements Action {

    private final int requiredFlint;
    private boolean startedMining = false;
    private int lastLoggedFlint = -1;

    public MineGravelAction(int requiredFlint) {
        this.requiredFlint = requiredFlint;
    }

    public int getRequiredFlint() {
        return requiredFlint;
    }

    @Override
    public void execute(Minecraft client, IBaritone baritone) {
        if (client.player == null) return;

        int currentFlint = AutoCrafter.getItemCount(client, "flint");
        if (currentFlint >= requiredFlint) {
            AutoCrafter.log(client, "§aCollected enough flint (" + currentFlint + "/" + requiredFlint + ")");
            return; // Finished!
        }

        if (currentFlint != lastLoggedFlint) {
            AutoCrafter.log(client, "Checking for flint (" + currentFlint + "/" + requiredFlint + ")");
            lastLoggedFlint = currentFlint;
        }

        // If Baritone is not already busy, command it to mine gravel
        boolean isBusy = baritone.getPathingBehavior().isPathing() 
                || baritone.getMineProcess().isActive() 
                || baritone.getGetToBlockProcess().isActive()
                || baritone.getFollowProcess().isActive();

        if (!isBusy) {
            if (!startedMining) {
                AutoCrafter.log(client, "§eExecuting: #mine gravel");
                startedMining = true;
            }
            baritone.getCommandManager().execute("mine gravel");
        }

        // Prepend itself to run again
        BaritonePlusClient.TASK_MANAGER.prependAction(this);
        BaritonePlusClient.TASK_MANAGER.setTickDelay(10);
    }

    @Override
    public boolean shouldLog() {
        return false;
    }

    @Override
    public String getDescription() {
        return "Mining gravel for flint";
    }
}
