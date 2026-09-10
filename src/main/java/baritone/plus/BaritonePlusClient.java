package baritone.plus;

import baritone.plus.command.CraftCommand;
import baritone.plus.task.TaskManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BaritonePlusClient implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("baritone-plus");
    public static final TaskManager TASK_MANAGER = new TaskManager();

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing Baritone Plus (Client)...");

        // Register Commands
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            CraftCommand.register(dispatcher);
        });

        // Register Tick Event for TaskManager
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            TASK_MANAGER.tick(client);
        });
    }
}
