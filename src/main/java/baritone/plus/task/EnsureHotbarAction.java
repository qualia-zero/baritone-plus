package baritone.plus.task;

import baritone.plus.util.AutoCrafter;
import baritone.api.IBaritone;
import net.minecraft.client.Minecraft;

public class EnsureHotbarAction implements Action {

    private final String item;
    private final boolean isCraftingScreen;

    public EnsureHotbarAction(String item, boolean isCraftingScreen) {
        this.item = item;
        this.isCraftingScreen = isCraftingScreen;
    }

    @Override
    public void execute(Minecraft client, IBaritone baritone) {
        AutoCrafter.ensureItemInHotbar(client, item, isCraftingScreen);
    }

    @Override
    public String getDescription() {
        return "Ensuring " + item + " in hotbar";
    }

    @Override
    public boolean shouldLog() {
        return false;
    }
}
