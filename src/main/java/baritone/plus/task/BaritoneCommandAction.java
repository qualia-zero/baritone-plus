package baritone.plus.task;

import baritone.api.IBaritone;
import net.minecraft.client.Minecraft;

public class BaritoneCommandAction implements Action {

    private final String command;

    public BaritoneCommandAction(String command) {
        this.command = command;
    }

    @Override
    public void execute(Minecraft client, IBaritone baritone) {
        baritone.getCommandManager().execute(command);
    }

    @Override
    public String getDescription() {
        return "Executing: #" + command;
    }

    public String getCommand() {
        return command;
    }
}
