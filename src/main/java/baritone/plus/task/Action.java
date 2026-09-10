package baritone.plus.task;

import baritone.api.IBaritone;
import net.minecraft.client.Minecraft;

public interface Action {
    void execute(Minecraft client, IBaritone baritone);
    String getDescription();
    default boolean shouldLog() {
        return true;
    }
}
