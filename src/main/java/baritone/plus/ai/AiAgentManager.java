package baritone.plus.ai;

import baritone.api.BaritoneAPI;
import baritone.plus.BaritonePlusClient;
import baritone.plus.config.BaritonePlusConfig;
import baritone.plus.task.CraftingTask;
import baritone.plus.util.AutoCrafter;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ContainerInput;

import java.util.List;

/**
 * Optional Gemini-powered agent loop embedded directly inside Baritone+.
 * Completely idle when aiBridge.enabled is false.
 */
public class AiAgentManager {

    private static final String SYSTEM_INSTRUCTION =
            "You are the optional AI assistant embedded inside Baritone+ for Minecraft (Fabric). "
                    + "Use the provided tools to inspect the game state, craft items, run Baritone navigation/mining commands, "
                    + "click server GUI slots (if allowed), or send chat/commands (if allowed). "
                    + "Always keep responses concise and action-oriented. Never invent items or coordinates you did not observe.";

    private static final JsonArray TOOLS = buildTools();

    private final GeminiClient geminiClient = new GeminiClient();
    private volatile String lastInteractionId = null;
    private volatile boolean running = false;
    private int stepsInCurrentRun = 0;
    private long lastChatSentMs = 0L;

    public boolean isRunning() {
        return running;
    }

    public boolean hasMemory() {
        return lastInteractionId != null && !lastInteractionId.isBlank();
    }

    public void resetMemory() {
        lastInteractionId = null;
        running = false;
    }

    public void stop() {
        running = false;
    }

    public void ask(Minecraft client, String prompt) {
        if (client == null) return;
        BaritonePlusConfig cfg = BaritonePlusConfig.get();
        if (!cfg.aiBridge.enabled) {
            AutoCrafter.log(client, "§eAI is disabled. Enable it via /bp ai on or in the AI (Opt.) tab.");
            return;
        }
        String apiKey = AiSecrets.getApiKey();
        if (apiKey == null) {
            AutoCrafter.log(client, "§cNo Gemini API key found. Set GEMINI_API_KEY or run /bp ai key <key>.");
            return;
        }
        if (running) {
            AutoCrafter.log(client, "§eAI is already processing a request. Use /bp stop to cancel.");
            return;
        }

        running = true;
        stepsInCurrentRun = 0;
        JsonObject state = GameStateCollector.collect(client);
        AutoCrafter.log(client, "§d[AI] §7Thinking (" + cfg.aiBridge.model + ")...");

        String fullInput = prompt + "\n\n[Live Game State]\n" + state;
        sendTurn(client, apiKey, new JsonPrimitive(fullInput));
    }

    private void sendTurn(Minecraft client, String apiKey, JsonElement input) {
        BaritonePlusConfig cfg = BaritonePlusConfig.get();
        geminiClient.createInteraction(
                apiKey,
                cfg.aiBridge.model,
                SYSTEM_INSTRUCTION,
                input,
                lastInteractionId,
                TOOLS
        ).thenAccept(resp -> client.execute(() -> handleResponse(client, apiKey, resp)));
    }

    private void handleResponse(Minecraft client, String apiKey, GeminiClient.InteractionResponse resp) {
        if (!running) {
            return;
        }

        if (resp.error() != null) {
            AutoCrafter.log(client, "§c[AI Error] " + resp.error());
            running = false;
            return;
        }

        if (resp.id() != null && !resp.id().isBlank()) {
            lastInteractionId = resp.id();
        }

        if (resp.outputText() != null && !resp.outputText().isBlank()) {
            AutoCrafter.log(client, "§d[AI] §f" + resp.outputText());
        }

        List<GeminiClient.FunctionCall> calls = resp.functionCalls();
        int maxSteps = BaritonePlusConfig.get().aiBridge.maxStepsPerRequest;
        if (!calls.isEmpty() && stepsInCurrentRun < maxSteps) {
            stepsInCurrentRun++;
            JsonArray results = new JsonArray();
            for (GeminiClient.FunctionCall fc : calls) {
                results.add(executeTool(client, fc));
            }
            sendTurn(client, apiKey, results);
        } else {
            if (!calls.isEmpty()) {
                AutoCrafter.log(client, "§e[AI] Reached maxStepsPerRequest (" + maxSteps + ").");
            }
            running = false;
        }
    }

    private JsonObject executeTool(Minecraft client, GeminiClient.FunctionCall fc) {
        JsonObject resultObj = new JsonObject();
        resultObj.addProperty("type", "function_result");
        resultObj.addProperty("call_id", fc.id());
        resultObj.addProperty("name", fc.name());

        try {
            String output = switch (fc.name()) {
                case "get_game_state" -> GameStateCollector.collect(client).toString();
                case "craft_item" -> toolCraftItem(client, fc.arguments());
                case "baritone_command" -> toolBaritoneCommand(client, fc.arguments());
                case "click_gui_slot" -> toolClickGuiSlot(client, fc.arguments());
                case "send_chat" -> toolSendChat(client, fc.arguments());
                case "stop_all" -> {
                    BaritonePlusClient.TASK_MANAGER.cancelTask();
                    BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("stop");
                    yield "Stopped all Baritone+ tasks and Baritone.";
                }
                default -> {
                    resultObj.addProperty("is_error", true);
                    yield "Unknown tool: " + fc.name();
                }
            };
            resultObj.addProperty("result", output);
        } catch (Exception e) {
            resultObj.addProperty("is_error", true);
            resultObj.addProperty("result", "Tool error: " + e.getMessage());
        }

        return resultObj;
    }

    private String toolCraftItem(Minecraft client, JsonObject args) {
        String item = getStringArg(args, "item");
        if (item == null || item.isBlank()) {
            throw new IllegalArgumentException("Missing 'item'");
        }
        int amount = args.has("amount") ? Math.max(1, Math.min(64, args.get("amount").getAsInt())) : 1;
        AutoCrafter.log(client, "§d[AI] §7Crafting " + amount + "x " + item);
        BaritonePlusClient.TASK_MANAGER.startTask(new CraftingTask(client, item.trim(), amount));
        return "Started crafting task for " + amount + "x " + item.trim();
    }

    private String toolBaritoneCommand(Minecraft client, JsonObject args) {
        String cmd = getStringArg(args, "command");
        if (cmd == null || cmd.isBlank()) {
            throw new IllegalArgumentException("Missing 'command'");
        }
        String clean = cmd.trim();
        if (clean.startsWith("#")) clean = clean.substring(1).trim();
        AutoCrafter.log(client, "§d[AI] §7Baritone: §f#" + clean);
        BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute(clean);
        return "Executed Baritone command: #" + clean;
    }

    private String toolClickGuiSlot(Minecraft client, JsonObject args) {
        if (!BaritonePlusConfig.get().aiBridge.allowGuiInteraction) {
            throw new IllegalStateException("allowGuiInteraction is disabled in config.");
        }
        if (client.player == null || client.player.containerMenu == null || client.gameMode == null) {
            throw new IllegalStateException("No open container menu.");
        }
        int slot = args.get("slot").getAsInt();
        int button = args.has("button") ? args.get("button").getAsInt() : 0;
        boolean quickMove = args.has("quick_move") && args.get("quick_move").getAsBoolean();
        ContainerInput action = quickMove ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP;
        AutoCrafter.clickSlot(client, client.player.containerMenu.containerId, slot, button, action);
        return "Clicked slot " + slot + " (button=" + button + ", quick_move=" + quickMove + ")";
    }

    private String toolSendChat(Minecraft client, JsonObject args) {
        BaritonePlusConfig cfg = BaritonePlusConfig.get();
        if (!cfg.aiBridge.allowChatSend) {
            throw new IllegalStateException("allowChatSend is disabled in config.");
        }
        if (client.player == null || client.player.connection == null) {
            throw new IllegalStateException("Player is not connected.");
        }
        long now = System.currentTimeMillis();
        if (now - lastChatSentMs < cfg.chat.minSendIntervalMs) {
            throw new IllegalStateException("Chat rate limit active, try again shortly.");
        }
        String message = getStringArg(args, "message");
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Missing 'message'");
        }
        lastChatSentMs = now;
        String clean = message.trim();
        if (clean.startsWith("/")) {
            client.player.connection.sendCommand(clean.substring(1));
        } else {
            client.player.connection.sendChat(clean);
        }
        return "Sent: " + clean;
    }

    private static String getStringArg(JsonObject args, String key) {
        return args != null && args.has(key) && !args.get(key).isJsonNull()
                ? args.get(key).getAsString()
                : null;
    }

    private static JsonArray buildTools() {
        JsonArray tools = new JsonArray();
        tools.add(tool("get_game_state",
                "Reads current player position, health, inventory, scoreboard, and open GUI slots.",
                new JsonObject(), List.of()));

        JsonObject craftProps = new JsonObject();
        craftProps.add("item", prop("string", "Minecraft item name, e.g. iron_pickaxe or oak_planks"));
        craftProps.add("amount", prop("integer", "Amount to craft (1-64)"));
        tools.add(tool("craft_item",
                "Starts Baritone+ autonomous crafting (gathers materials, smelts, crafts).",
                craftProps, List.of("item")));

        JsonObject baritoneProps = new JsonObject();
        baritoneProps.add("command", prop("string", "Baritone command without #, e.g. 'mine diamond_ore', 'goto 100 64 -200', 'follow players', 'explore', 'stop'"));
        tools.add(tool("baritone_command",
                "Executes a Baritone pathing, mining, following, or utility command.",
                baritoneProps, List.of("command")));

        JsonObject guiProps = new JsonObject();
        guiProps.add("slot", prop("integer", "Container slot index"));
        guiProps.add("button", prop("integer", "Mouse button (0=left, 1=right)"));
        guiProps.add("quick_move", prop("boolean", "True for shift-click quick move"));
        tools.add(tool("click_gui_slot",
                "Clicks a slot in the currently open chest or server GUI menu.",
                guiProps, List.of("slot")));

        JsonObject chatProps = new JsonObject();
        chatProps.add("message", prop("string", "Chat message or /command to send"));
        tools.add(tool("send_chat",
                "Sends a chat message or /command if allowChatSend is enabled.",
                chatProps, List.of("message")));

        tools.add(tool("stop_all",
                "Cancels all active Baritone+ tasks and stops Baritone.",
                new JsonObject(), List.of()));

        return tools;
    }

    private static JsonObject tool(String name, String description, JsonObject properties, List<String> required) {
        JsonObject fn = new JsonObject();
        fn.addProperty("type", "function");
        fn.addProperty("name", name);
        fn.addProperty("description", description);

        JsonObject params = new JsonObject();
        params.addProperty("type", "object");
        params.add("properties", properties);
        if (!required.isEmpty()) {
            JsonArray req = new JsonArray();
            required.forEach(req::add);
            params.add("required", req);
        }
        fn.add("parameters", params);
        return fn;
    }

    private static JsonObject prop(String type, String description) {
        JsonObject p = new JsonObject();
        p.addProperty("type", type);
        p.addProperty("description", description);
        return p;
    }
}
