package baritone.plus.ai;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.plus.BaritonePlusClient;
import baritone.plus.combat.RotationManager;
import baritone.plus.config.BaritonePlusConfig;
import baritone.plus.util.AutoCrafter;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Optional Gemini-powered atomic orchestrator embedded directly in Baritone+.
 * Uses pure atomic primitives (Baritone commands, craft_grid, place_block, equip_item,
 * interact_nearby, click_gui_slot, send_chat, set_combat_defense) instead of a fixed planner.
 */
public class AiAgentManager {

    private enum State {
        IDLE,
        WAITING_API,
        WAITING_ACTION
    }

    private static final String SYSTEM_INSTRUCTION =
            "You are the autonomous atomic AI orchestrator embedded inside Baritone+ for Minecraft (Fabric).\n"
                    + "Rules:\n"
                    + "1. LANGUAGE: Always reply in the exact same language as the user's instruction "
                    + "(e.g. German prompt -> German replies, English prompt -> English replies).\n"
                    + "2. ATOMIC ORCHESTRATION: You replace a fixed orchestrator. There is no high-level autocraft shortcut. "
                    + "Plan and execute every step atomically using: baritone_command (mine, goto, explore, farm, follow), "
                    + "craft_grid (any 2x2 inventory or 3x3 crafting table recipe), place_block, interact_nearby, "
                    + "click_gui_slot, close_gui, equip_item, set_combat_defense, send_chat, and stop_all.\n"
                    + "3. STEP-BY-STEP VERIFICATION: After every tool call, inspect the returned live game state "
                    + "(inventory, position, openScreen, nearbyEntities, recentChat, health, combat) before choosing "
                    + "the next step. Never assume a step succeeded — verify from the new state and recover if needed.\n"
                    + "4. PERSISTENT DEFENSE: Self-preservation (autoEat, autoShield, combat) runs independently of AI. "
                    + "Keep defensive functions active between tasks unless the user explicitly asks to disable them.\n"
                    + "5. OUTPUT: Keep chat messages concise. Only finish when the user's goal is complete or blocked.";

    private static final JsonArray TOOLS = buildTools();
    private static final int ACTION_TIMEOUT_TICKS = 2400; // 2 minutes at 20 TPS

    private final GeminiClient geminiClient = new GeminiClient();
    private volatile String lastInteractionId = null;
    private volatile State state = State.IDLE;

    private String activeApiKey = null;
    private int stepsInCurrentRun = 0;
    private long lastChatSentMs = 0L;

    // Pending long-running Baritone action state
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

        boolean busy = isBaritoneBusy();
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
                    longRunningSummary = startBaritoneCommand(client, fc.arguments());
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
        if (!"baritone_command".equals(fc.name())) {
            return false;
        }
        String cmd = getStringArg(fc.arguments(), "command");
        if (cmd == null) return false;
        String clean = cmd.trim().toLowerCase();
        if (clean.startsWith("#")) clean = clean.substring(1).trim();
        return !(clean.startsWith("stop") || clean.startsWith("pause"));
    }

    private String startBaritoneCommand(Minecraft client, JsonObject args) {
        String cmd = getStringArg(args, "command");
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
                case "baritone_command" -> startBaritoneCommand(client, fc.arguments());
                case "craft_grid" -> toolCraftGrid(client, fc.arguments());
                case "place_block" -> toolPlaceBlock(client, fc.arguments());
                case "equip_item" -> toolEquipItem(client, fc.arguments());
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

    private String toolCraftGrid(Minecraft client, JsonObject args) {
        if (client.player == null || client.gameMode == null) {
            throw new IllegalStateException("Player is not in game.");
        }
        if (!args.has("pattern") || !args.get("pattern").isJsonArray()) {
            throw new IllegalArgumentException("Missing 'pattern' array (4 or 9 strings).");
        }
        JsonArray pattern = args.getAsJsonArray("pattern");
        int gridSize = pattern.size();
        if (gridSize != 4 && gridSize != 9) {
            throw new IllegalArgumentException("'pattern' must have length 4 (2x2) or 9 (3x3), got " + gridSize);
        }
        int count = args.has("count") ? Math.max(1, Math.min(64, args.get("count").getAsInt())) : 1;

        boolean is3x3 = (gridSize == 9);
        boolean isCraftingScreen = AutoCrafter.isCraftingScreenOpen(client);
        if (is3x3 && !isCraftingScreen) {
            throw new IllegalStateException("3x3 recipe requires an open crafting_table GUI. Place and interact with a crafting_table first.");
        }
        if (!is3x3 && !isCraftingScreen && !(client.gui.screen() instanceof InventoryScreen)) {
            client.gui.setScreen(new InventoryScreen(client.player));
        }

        int searchStart = isCraftingScreen ? 10 : 9;
        int searchEnd = isCraftingScreen ? 46 : 45;
        int syncId = client.player.containerMenu.containerId;

        // Clear any leftover items in crafting input slots (1..gridSize)
        for (int s = 1; s <= gridSize; s++) {
            if (!client.player.containerMenu.getSlot(s).getItem().isEmpty()) {
                AutoCrafter.clickSlot(client, syncId, s, 0, ContainerInput.QUICK_MOVE);
            }
        }

        int completed = 0;
        for (int c = 0; c < count; c++) {
            for (int i = 0; i < gridSize; i++) {
                String itemName = pattern.get(i).isJsonNull() ? "" : pattern.get(i).getAsString().trim();
                if (!itemName.isEmpty()) {
                    boolean placed = AutoCrafter.moveItemToSlot(client, itemName, searchStart, searchEnd, i + 1);
                    if (!placed) {
                        // Return any partially placed items before reporting failure
                        for (int s = 1; s <= gridSize; s++) {
                            if (!client.player.containerMenu.getSlot(s).getItem().isEmpty()) {
                                AutoCrafter.clickSlot(client, syncId, s, 0, ContainerInput.QUICK_MOVE);
                            }
                        }
                        throw new IllegalStateException("Missing ingredient '" + itemName + "' after " + completed + " crafts.");
                    }
                }
            }
            AutoCrafter.takeResult(client, 0);
            completed++;
        }

        JsonObject res = new JsonObject();
        res.addProperty("craftedBatches", completed);
        res.add("state", GameStateCollector.collect(client));
        return res.toString();
    }

    private String toolPlaceBlock(Minecraft client, JsonObject args) {
        if (client.player == null || client.level == null || client.gameMode == null) {
            throw new IllegalStateException("Player is not in game.");
        }
        String itemName = getStringArg(args, "item");
        if (itemName == null || itemName.isBlank()) {
            throw new IllegalArgumentException("Missing 'item'.");
        }
        Item targetItem = AutoCrafter.getItem(itemName.trim());
        if (targetItem == null) {
            throw new IllegalArgumentException("Unknown item: " + itemName);
        }

        // Find in hotbar (0..8) or swap from main inventory (9..35)
        int hotbarSlot = -1;
        for (int i = 0; i < 9; i++) {
            if (client.player.getInventory().getItem(i).is(targetItem)) {
                hotbarSlot = i;
                break;
            }
        }
        if (hotbarSlot == -1) {
            int invSlot = -1;
            for (int i = 9; i < 36; i++) {
                if (client.player.getInventory().getItem(i).is(targetItem)) {
                    invSlot = i;
                    break;
                }
            }
            if (invSlot == -1) {
                throw new IllegalStateException("Item not found in inventory: " + itemName);
            }
            int targetHotbar = 8;
            for (int i = 0; i < 9; i++) {
                if (client.player.getInventory().getItem(i).isEmpty()) {
                    targetHotbar = i;
                    break;
                }
            }
            AutoCrafter.clickSlot(client, client.player.inventoryMenu.containerId, invSlot, targetHotbar, ContainerInput.SWAP);
            hotbarSlot = targetHotbar;
        }

        // Find an air block directly above a solid block within radius 3
        BlockPos playerPos = client.player.blockPosition();
        BlockPos supportPos = null;
        for (int r = 1; r <= 3 && supportPos == null; r++) {
            for (int dx = -r; dx <= r && supportPos == null; dx++) {
                for (int dz = -r; dz <= r && supportPos == null; dz++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos candidate = playerPos.offset(dx, dy, dz);
                        if (candidate.equals(playerPos) || candidate.equals(playerPos.above())) continue;
                        BlockPos below = candidate.below();
                        if (client.level.getBlockState(candidate).canBeReplaced()
                                && client.level.getBlockState(below).isSolid()) {
                            supportPos = below;
                            break;
                        }
                    }
                }
            }
        }
        if (supportPos == null) {
            throw new IllegalStateException("No suitable placement spot found near player.");
        }

        client.player.getInventory().setSelectedSlot(hotbarSlot);
        Vec3 hitVec = new Vec3(supportPos.getX() + 0.5, supportPos.getY() + 1.0, supportPos.getZ() + 0.5);
        RotationManager.rotateTowards(client, hitVec, 180.0);
        BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, supportPos, false);
        client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
        AutoCrafter.swingMainHand(client);
        return "Placed " + itemName + " at " + supportPos.above().toShortString();
    }

    private String toolEquipItem(Minecraft client, JsonObject args) {
        if (client.player == null || client.gameMode == null) {
            throw new IllegalStateException("Player is not in game.");
        }
        String itemName = getStringArg(args, "item");
        String slot = getStringArg(args, "slot");
        if (itemName == null || itemName.isBlank()) {
            throw new IllegalArgumentException("Missing 'item'.");
        }
        if (slot == null || slot.isBlank()) slot = "mainhand";

        Item targetItem = AutoCrafter.getItem(itemName.trim());
        if (targetItem == null) {
            throw new IllegalArgumentException("Unknown item: " + itemName);
        }

        int foundInvIdx = -1;
        for (int i = 0; i < 36; i++) {
            if (client.player.getInventory().getItem(i).is(targetItem)) {
                foundInvIdx = i;
                break;
            }
        }
        if (foundInvIdx == -1) {
            throw new IllegalStateException("Item not found in inventory: " + itemName);
        }

        int syncId = client.player.inventoryMenu.containerId;
        int menuSlot = (foundInvIdx < 9) ? (36 + foundInvIdx) : foundInvIdx;

        switch (slot.toLowerCase()) {
            case "mainhand" -> {
                if (foundInvIdx < 9) {
                    client.player.getInventory().setSelectedSlot(foundInvIdx);
                } else {
                    int activeHotbar = client.player.getInventory().getSelectedSlot();
                    AutoCrafter.clickSlot(client, syncId, menuSlot, activeHotbar, ContainerInput.SWAP);
                }
                return "Equipped " + itemName + " in mainhand.";
            }
            case "offhand" -> {
                AutoCrafter.clickSlot(client, syncId, menuSlot, 40, ContainerInput.SWAP);
                return "Equipped " + itemName + " in offhand.";
            }
            case "armor" -> {
                AutoCrafter.clickSlot(client, syncId, menuSlot, 0, ContainerInput.QUICK_MOVE);
                return "Equipped armor item: " + itemName;
            }
            default -> throw new IllegalArgumentException("slot must be 'mainhand', 'offhand', or 'armor'.");
        }
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
            cfg.autoEat.enabled = args.get("auto_eat").getAsBoolean();
        }
        if (args.has("target_hostiles") && !args.get("target_hostiles").isJsonNull()) {
            cfg.combat.targetHostiles = args.get("target_hostiles").getAsBoolean();
        }
        if (args.has("target_players") && !args.get("target_players").isJsonNull()) {
            cfg.combat.targetPlayers = args.get("target_players").getAsBoolean();
        }
        BaritonePlusConfig.save();
        AutoCrafter.log(client, "§d[AI] §7Defense updated: combat=" + cfg.combat.enabled
                + ", autoShield=" + cfg.combat.autoShield + ", autoEat=" + cfg.autoEat.enabled);
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

        JsonObject baritoneProps = new JsonObject();
        baritoneProps.add("command", prop("string", "Baritone command without #, e.g. 'mine 8 diamond_ore', 'goto 100 64 -200', 'follow players', 'explore', 'stop'"));
        tools.add(tool("baritone_command",
                "Executes a Baritone command and waits until it finishes (except stop/pause), then returns the updated game state.",
                baritoneProps, List.of("command")));

        JsonObject gridProps = new JsonObject();
        JsonObject patternProp = new JsonObject();
        patternProp.addProperty("type", "array");
        JsonObject itemType = new JsonObject();
        itemType.addProperty("type", "string");
        patternProp.add("items", itemType);
        patternProp.addProperty("description", "4 strings (2x2 inventory grid) or 9 strings (3x3 crafting table grid), row-major, '' for empty, e.g. ['oak_planks','','oak_planks','']");
        gridProps.add("pattern", patternProp);
        gridProps.add("count", prop("integer", "Number of craft batches to execute (1-64, default 1)"));
        tools.add(tool("craft_grid",
                "Crafts any 2x2 or 3x3 recipe directly in the crafting grid and returns the updated game state.",
                gridProps, List.of("pattern")));

        JsonObject placeProps = new JsonObject();
        placeProps.add("item", prop("string", "Block item name in inventory, e.g. 'crafting_table', 'furnace', 'chest'"));
        tools.add(tool("place_block",
                "Places a block from inventory onto a nearby solid surface.",
                placeProps, List.of("item")));

        JsonObject equipProps = new JsonObject();
        equipProps.add("item", prop("string", "Item name in inventory, e.g. 'iron_pickaxe', 'shield', 'iron_chestplate'"));
        equipProps.add("slot", prop("string", "'mainhand', 'offhand', or 'armor'"));
        tools.add(tool("equip_item",
                "Equips an item from inventory to mainhand, offhand, or armor slot.",
                equipProps, List.of("item")));

        JsonObject combatProps = new JsonObject();
        combatProps.add("enabled", prop("boolean", "Enable/disable melee combat attacker"));
        combatProps.add("auto_shield", prop("boolean", "Auto-block incoming arrows/creepers/melee with offhand shield"));
        combatProps.add("auto_eat", prop("boolean", "Enable/disable standalone Auto-Eat"));
        combatProps.add("target_hostiles", prop("boolean", "Attack nearby hostile monsters"));
        combatProps.add("target_players", prop("boolean", "Attack nearby players"));
        tools.add(tool("set_combat_defense",
                "Toggles persistent combat, Auto-Shield, and Auto-Eat settings (remain active between tasks).",
                combatProps, List.of()));

        JsonObject interactProps = new JsonObject();
        interactProps.add("target_type", prop("string", "'block' or 'entity'"));
        interactProps.add("query", prop("string", "Partial block or entity name, e.g. 'chest', 'villager', 'crafting_table'"));
        tools.add(tool("interact_nearby",
                "Right-clicks the nearest matching block or entity within 4.5 blocks (e.g. open crafting_table/furnace/chest or talk to NPC).",
                interactProps, List.of("target_type", "query")));

        JsonObject guiProps = new JsonObject();
        guiProps.add("slot", prop("integer", "Container slot index"));
        guiProps.add("button", prop("integer", "Mouse button (0=left, 1=right)"));
        guiProps.add("quick_move", prop("boolean", "True for shift-click quick move"));
        tools.add(tool("click_gui_slot",
                "Clicks a slot in the currently open chest, furnace, or server GUI menu.",
                guiProps, List.of("slot")));

        tools.add(tool("close_gui",
                "Closes the currently open chest, crafting table, or server GUI menu.",
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
