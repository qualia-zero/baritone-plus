package baritone.plus.ai;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.plus.BaritonePlusClient;
import baritone.plus.combat.RotationManager;
import baritone.plus.config.BaritonePlusConfig;
import baritone.plus.task.CraftingTask;
import baritone.plus.util.AutoCrafter;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Optional Gemini-powered autonomous step-by-step orchestrator embedded directly in Baritone+.
 * Waits for long-running Baritone / Baritone+ tasks to complete, inspects updated game state,
 * and decides the next step until the user's goal is reached.
 */
public class AiAgentManager {

    private enum State {
        IDLE,
        WAITING_API,
        WAITING_ACTION
    }

    private static final String SYSTEM_INSTRUCTION =
            "You are the autonomous AI orchestrator embedded inside Baritone+ for Minecraft (Fabric).\n"
                    + "Rules:\n"
                    + "1. LANGUAGE: Always reply in the exact same language as the user's instruction "
                    + "(e.g. German prompt -> German replies, English prompt -> English replies).\n"
                    + "2. ORCHESTRATION: You replace a fixed orchestrator. Break goals into clear steps using "
                    + "Baritone navigation/mining, Baritone+ crafting, container/GUI interaction, and built-in "
                    + "Meteor-inspired combat/defense.\n"
                    + "3. STEP-BY-STEP VERIFICATION: After every tool call, carefully inspect the returned live "
                    + "game state (inventory, position, openScreen, nearbyEntities, recentChat, health, combat) "
                    + "before choosing the next step. Never assume a step succeeded — verify from the new state "
                    + "and adapt or recover if something failed.\n"
                    + "4. PERSISTENT DEFENSE: Keep defensive functions (combat.enabled, autoShield, autoEat) "
                    + "active between tasks whenever threats exist or on survival/multiplayer servers. "
                    + "Do not disable autoShield or autoEat between tasks unless the user explicitly asks.\n"
                    + "5. OUTPUT: Keep chat messages concise. Only finish when the user's goal is complete or blocked.";

    private static final JsonArray TOOLS = buildTools();
    private static final int ACTION_TIMEOUT_TICKS = 2400; // 2 minutes at 20 TPS

    private final GeminiClient geminiClient = new GeminiClient();
    private volatile String lastInteractionId = null;
    private volatile State state = State.IDLE;

    private String activeApiKey = null;
    private int stepsInCurrentRun = 0;
    private long lastChatSentMs = 0L;

    // Pending long-running action state
    private GeminiClient.FunctionCall pendingActionCall = null;
    private String pendingActionSummary = null;
    private JsonArray pendingResults = null;
    private int actionGraceTicks = 0;
    private int actionWaitTicks = 0;

    public boolean isRunning() {
        return state != State.IDLE;
    }

    public boolean hasMemory() {
        return lastInteractionId != null && !lastInteractionId.isBlank();
    }

    public void resetMemory() {
        lastInteractionId = null;
        clearRunState();
    }

    public void stop() {
        clearRunState();
    }

    private void clearRunState() {
        state = State.IDLE;
        activeApiKey = null;
        pendingActionCall = null;
        pendingActionSummary = null;
        pendingResults = null;
        actionGraceTicks = 0;
        actionWaitTicks = 0;
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
        if (state != State.IDLE) {
            AutoCrafter.log(client, "§eAI is already running a task. Use /bp stop to cancel.");
            return;
        }

        state = State.WAITING_API;
        activeApiKey = apiKey;
        stepsInCurrentRun = 0;
        JsonObject gameState = GameStateCollector.collect(client);
        AutoCrafter.log(client, "§d[AI] §7Thinking (" + cfg.aiBridge.model + ")...");

        String fullInput = prompt + "\n\n[Live Game State]\n" + gameState;
        sendTurn(client, apiKey, new JsonPrimitive(fullInput));
    }

    public void tick(Minecraft client) {
        if (state != State.WAITING_ACTION || client == null) {
            return;
        }

        actionWaitTicks++;
        if (actionGraceTicks > 0) {
            actionGraceTicks--;
            return;
        }

        boolean busy = BaritonePlusClient.TASK_MANAGER.isBusy() || isBaritoneBusy();
        boolean danger = BaritonePlusClient.COMBAT_CONTROLLER.isInDanger(client);
        boolean timedOut = actionWaitTicks >= ACTION_TIMEOUT_TICKS;

        if (busy && !danger && !timedOut) {
            return;
        }

        String status = danger ? "interrupted_low_hp" : (timedOut ? "timeout" : "completed");
        JsonObject resultPayload = new JsonObject();
        resultPayload.addProperty("status", status);
        resultPayload.addProperty("action", pendingActionSummary != null ? pendingActionSummary : "");
        resultPayload.add("state", GameStateCollector.collect(client));

        JsonObject fnResult = new JsonObject();
        fnResult.addProperty("type", "function_result");
        fnResult.addProperty("call_id", pendingActionCall.id());
        fnResult.addProperty("name", pendingActionCall.name());
        fnResult.addProperty("result", resultPayload.toString());

        JsonArray toSend = pendingResults != null ? pendingResults : new JsonArray();
        toSend.add(fnResult);

        String key = activeApiKey;
        pendingActionCall = null;
        pendingActionSummary = null;
        pendingResults = null;
        actionGraceTicks = 0;
        actionWaitTicks = 0;
        state = State.WAITING_API;

        sendTurn(client, key, toSend);
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
        if (state != State.WAITING_API) {
            return;
        }

        if (resp.error() != null) {
            AutoCrafter.log(client, "§c[AI Error] " + resp.error());
            clearRunState();
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
        if (calls.isEmpty()) {
            clearRunState();
            return;
        }

        if (stepsInCurrentRun >= maxSteps) {
            AutoCrafter.log(client, "§e[AI] Reached maxStepsPerRequest (" + maxSteps + ").");
            clearRunState();
            return;
        }

        stepsInCurrentRun++;
        JsonArray syncResults = new JsonArray();
        GeminiClient.FunctionCall longRunningCall = null;
        String longRunningSummary = null;

        for (GeminiClient.FunctionCall fc : calls) {
            if (longRunningCall == null && isLongRunningCall(fc)) {
                try {
                    longRunningSummary = startLongRunningTool(client, fc);
                    longRunningCall = fc;
                } catch (Exception e) {
                    syncResults.add(errorResult(fc, "Tool error: " + e.getMessage()));
                }
            } else {
                syncResults.add(executeSyncTool(client, fc));
            }
        }

        if (longRunningCall != null) {
            this.activeApiKey = apiKey;
            this.pendingActionCall = longRunningCall;
            this.pendingActionSummary = longRunningSummary;
            this.pendingResults = syncResults;
            this.actionGraceTicks = 25;
            this.actionWaitTicks = 0;
            this.state = State.WAITING_ACTION;
        } else {
            sendTurn(client, apiKey, syncResults);
        }
    }

    private boolean isLongRunningCall(GeminiClient.FunctionCall fc) {
        if ("craft_item".equals(fc.name())) {
            return true;
        }
        if ("baritone_command".equals(fc.name())) {
            String cmd = getStringArg(fc.arguments(), "command");
            if (cmd == null) return false;
            String clean = cmd.trim().toLowerCase();
            if (clean.startsWith("#")) clean = clean.substring(1).trim();
            return !(clean.startsWith("stop") || clean.startsWith("pause"));
        }
        return false;
    }

    private String startLongRunningTool(Minecraft client, GeminiClient.FunctionCall fc) {
        if ("craft_item".equals(fc.name())) {
            String item = getStringArg(fc.arguments(), "item");
            if (item == null || item.isBlank()) {
                throw new IllegalArgumentException("Missing 'item'");
            }
            int amount = fc.arguments().has("amount")
                    ? Math.max(1, Math.min(64, fc.arguments().get("amount").getAsInt()))
                    : 1;
            AutoCrafter.log(client, "§d[AI] §7Crafting " + amount + "x " + item.trim());
            BaritonePlusClient.TASK_MANAGER.startTask(new CraftingTask(client, item.trim(), amount));
            return "craft_item " + amount + "x " + item.trim();
        }

        String cmd = getStringArg(fc.arguments(), "command");
        if (cmd == null || cmd.isBlank()) {
            throw new IllegalArgumentException("Missing 'command'");
        }
        String clean = cmd.trim();
        if (clean.startsWith("#")) clean = clean.substring(1).trim();
        AutoCrafter.log(client, "§d[AI] §7Baritone: §f#" + clean);
        BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute(clean);
        return "baritone_command #" + clean;
    }

    private JsonObject executeSyncTool(Minecraft client, GeminiClient.FunctionCall fc) {
        JsonObject resultObj = new JsonObject();
        resultObj.addProperty("type", "function_result");
        resultObj.addProperty("call_id", fc.id());
        resultObj.addProperty("name", fc.name());

        try {
            String output = switch (fc.name()) {
                case "get_game_state" -> GameStateCollector.collect(client).toString();
                case "baritone_command" -> startLongRunningTool(client, fc);
                case "set_combat_defense" -> toolSetCombatDefense(client, fc.arguments());
                case "interact_nearby" -> toolInteractNearby(client, fc.arguments());
                case "click_gui_slot" -> toolClickGuiSlot(client, fc.arguments());
                case "close_gui" -> toolCloseGui(client);
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

    private JsonObject errorResult(GeminiClient.FunctionCall fc, String message) {
        JsonObject err = new JsonObject();
        err.addProperty("type", "function_result");
        err.addProperty("call_id", fc.id());
        err.addProperty("name", fc.name());
        err.addProperty("is_error", true);
        err.addProperty("result", message);
        return err;
    }

    private String toolSetCombatDefense(Minecraft client, JsonObject args) {
        BaritonePlusConfig cfg = BaritonePlusConfig.get();
        if (!cfg.aiBridge.allowCombatOverride) {
            throw new IllegalStateException("allowCombatOverride is disabled in config.");
        }
        if (args.has("enabled") && !args.get("enabled").isJsonNull()) {
            cfg.combat.enabled = args.get("enabled").getAsBoolean();
        }
        if (args.has("auto_shield") && !args.get("auto_shield").isJsonNull()) {
            cfg.combat.autoShield = args.get("auto_shield").getAsBoolean();
        }
        if (args.has("auto_eat") && !args.get("auto_eat").isJsonNull()) {
            cfg.combat.autoEat = args.get("auto_eat").getAsBoolean();
        }
        if (args.has("target_hostiles") && !args.get("target_hostiles").isJsonNull()) {
            cfg.combat.targetHostiles = args.get("target_hostiles").getAsBoolean();
        }
        if (args.has("target_players") && !args.get("target_players").isJsonNull()) {
            cfg.combat.targetPlayers = args.get("target_players").getAsBoolean();
        }
        BaritonePlusConfig.save();
        AutoCrafter.log(client, "§d[AI] §7Combat/Defense updated: enabled=" + cfg.combat.enabled
                + ", autoShield=" + cfg.combat.autoShield + ", autoEat=" + cfg.combat.autoEat);
        return "Combat/Defense config updated and saved.";
    }

    private String toolInteractNearby(Minecraft client, JsonObject args) {
        if (client.player == null || client.level == null || client.gameMode == null) {
            throw new IllegalStateException("Player is not in game.");
        }
        String targetType = getStringArg(args, "target_type");
        String query = getStringArg(args, "query");
        if (targetType == null || query == null || query.isBlank()) {
            throw new IllegalArgumentException("Missing 'target_type' or 'query'.");
        }
        String q = query.trim().toLowerCase();

        if ("entity".equalsIgnoreCase(targetType)) {
            Entity best = null;
            double bestDistSq = 4.5 * 4.5;
            for (Entity e : client.level.entitiesForRendering()) {
                if (e == client.player || !e.isAlive()) continue;
                double dSq = client.player.distanceToSqr(e);
                if (dSq > bestDistSq) continue;
                String typeName = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().toLowerCase();
                String displayName = e.getName().getString().toLowerCase();
                if (typeName.contains(q) || displayName.contains(q)) {
                    bestDistSq = dSq;
                    best = e;
                }
            }
            if (best == null) {
                throw new IllegalStateException("No matching entity within 4.5 blocks for: " + query);
            }
            RotationManager.rotateTowards(client, best.getBoundingBox().getCenter(), 180.0);
            client.gameMode.interact(client.player, best, new EntityHitResult(best), InteractionHand.MAIN_HAND);
            AutoCrafter.swingMainHand(client);
            return "Interacted with entity: " + best.getName().getString() + " (" + GameStateCollector.collect(client) + ")";
        }

        if ("block".equalsIgnoreCase(targetType)) {
            BlockPos playerPos = client.player.blockPosition();
            BlockPos bestPos = null;
            double bestDistSq = 4.5 * 4.5;
            for (int dx = -4; dx <= 4; dx++) {
                for (int dy = -3; dy <= 4; dy++) {
                    for (int dz = -4; dz <= 4; dz++) {
                        BlockPos pos = playerPos.offset(dx, dy, dz);
                        BlockState bs = client.level.getBlockState(pos);
                        if (bs.isAir()) continue;
                        String blockId = BuiltInRegistries.BLOCK.getKey(bs.getBlock()).getPath().toLowerCase();
                        if (blockId.contains(q)) {
                            double dSq = client.player.getEyePosition().distanceToSqr(
                                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                            if (dSq <= bestDistSq) {
                                bestDistSq = dSq;
                                bestPos = pos;
                            }
                        }
                    }
                }
            }
            if (bestPos == null) {
                throw new IllegalStateException("No matching block within 4.5 blocks for: " + query);
            }
            Vec3 hitVec = new Vec3(bestPos.getX() + 0.5, bestPos.getY() + 0.5, bestPos.getZ() + 0.5);
            RotationManager.rotateTowards(client, hitVec, 180.0);
            BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, bestPos, false);
            client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
            AutoCrafter.swingMainHand(client);
            return "Interacted with block at " + bestPos.toShortString();
        }

        throw new IllegalArgumentException("target_type must be 'block' or 'entity'.");
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

    private String toolCloseGui(Minecraft client) {
        if (client.player != null) {
            client.player.closeContainer();
        }
        client.gui.setScreen(null);
        return "Closed active GUI/container.";
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

    private boolean isBaritoneBusy() {
        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        return baritone != null && (
                baritone.getPathingBehavior().isPathing()
                        || baritone.getMineProcess().isActive()
                        || baritone.getFollowProcess().isActive()
                        || baritone.getCustomGoalProcess().isActive()
                        || baritone.getBuilderProcess().isActive()
                        || baritone.getExploreProcess().isActive()
                        || baritone.getFarmProcess().isActive()
        );
    }

    private static String getStringArg(JsonObject args, String key) {
        return args != null && args.has(key) && !args.get(key).isJsonNull()
                ? args.get(key).getAsString()
                : null;
    }

    private static JsonArray buildTools() {
        JsonArray tools = new JsonArray();
        tools.add(tool("get_game_state",
                "Reads current player position, health, inventory, nearby entities, recent chat, scoreboard, and open GUI slots.",
                new JsonObject(), List.of()));

        JsonObject craftProps = new JsonObject();
        craftProps.add("item", prop("string", "Minecraft item name, e.g. iron_pickaxe or oak_planks"));
        craftProps.add("amount", prop("integer", "Amount to craft (1-64)"));
        tools.add(tool("craft_item",
                "Starts Baritone+ autonomous crafting and waits until it finishes, then returns the updated game state.",
                craftProps, List.of("item")));

        JsonObject baritoneProps = new JsonObject();
        baritoneProps.add("command", prop("string", "Baritone command without #, e.g. 'mine 8 diamond_ore', 'goto 100 64 -200', 'follow players', 'explore', 'stop'"));
        tools.add(tool("baritone_command",
                "Executes a Baritone command and waits until it finishes (except stop/pause), then returns the updated game state.",
                baritoneProps, List.of("command")));

        JsonObject combatProps = new JsonObject();
        combatProps.add("enabled", prop("boolean", "Enable/disable built-in Meteor-inspired combat & defense controller"));
        combatProps.add("auto_shield", prop("boolean", "Auto-block incoming arrows/creepers/melee with offhand shield"));
        combatProps.add("auto_eat", prop("boolean", "Auto-eat food from hotbar when hungry or low HP"));
        combatProps.add("target_hostiles", prop("boolean", "Attack nearby hostile monsters"));
        combatProps.add("target_players", prop("boolean", "Attack nearby players"));
        tools.add(tool("set_combat_defense",
                "Configures the persistent combat & defense controller (stays active between tasks).",
                combatProps, List.of()));

        JsonObject interactProps = new JsonObject();
        interactProps.add("target_type", prop("string", "'block' or 'entity'"));
        interactProps.add("query", prop("string", "Partial block or entity name, e.g. 'chest', 'villager', 'crafting_table'"));
        tools.add(tool("interact_nearby",
                "Right-clicks the nearest matching block or entity within 4.5 blocks (e.g. open chest or talk to NPC).",
                interactProps, List.of("target_type", "query")));

        JsonObject guiProps = new JsonObject();
        guiProps.add("slot", prop("integer", "Container slot index"));
        guiProps.add("button", prop("integer", "Mouse button (0=left, 1=right)"));
        guiProps.add("quick_move", prop("boolean", "True for shift-click quick move"));
        tools.add(tool("click_gui_slot",
                "Clicks a slot in the currently open chest or server GUI menu.",
                guiProps, List.of("slot")));

        tools.add(tool("close_gui",
                "Closes the currently open chest or server GUI menu.",
                new JsonObject(), List.of()));

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
