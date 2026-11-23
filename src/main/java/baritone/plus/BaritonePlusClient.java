package baritone.plus;

import baritone.plus.ai.AiAgentManager;
import baritone.plus.command.BpCommand;
import baritone.plus.config.BaritonePlusConfig;
import baritone.plus.gui.BaritonePlusScreen;
import baritone.plus.task.TaskManager;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BaritonePlusClient implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("baritone-plus");
    public static final TaskManager TASK_MANAGER = new TaskManager();
    public static final AiAgentManager AI_AGENT = new AiAgentManager();

    private static final KeyMapping.Category KEY_CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("baritone_plus", "main"));
    private static KeyMapping openGuiKey;
    private static boolean openGuiNextTick = false;

    public static void requestOpenGui() {
        openGuiNextTick = true;
    }

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing Baritone Plus (Client)...");

        BaritonePlusConfig.load();

        openGuiKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.baritone_plus.open_gui",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_RSHIFT,
                KEY_CATEGORY
        ));

        // Register Commands
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            BpCommand.register(dispatcher);
        });

        // Register Tick Event for GUI & TaskManager
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openGuiNextTick) {
                openGuiNextTick = false;
                client.gui.setScreen(new BaritonePlusScreen(null));
            }
            while (openGuiKey.consumeClick()) {
                if (client.gui.screen() == null) {
                    client.gui.setScreen(new BaritonePlusScreen(null));
                }
            }
            TASK_MANAGER.tick(client);
        });
    }
}
