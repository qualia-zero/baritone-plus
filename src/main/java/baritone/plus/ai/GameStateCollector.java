package baritone.plus.ai;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.plus.BaritonePlusClient;
import baritone.plus.config.BaritonePlusConfig;
import baritone.plus.network.ChatTracker;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects a compact JSON snapshot of the client's current state for Gemini.
 * Must be called on the Minecraft client thread.
 */
public class GameStateCollector {

    public static JsonObject collect(Minecraft client) {
        JsonObject root = new JsonObject();
        if (client == null || client.player == null || client.level == null) {
            root.addProperty("inGame", false);
            return root;
        }

        root.addProperty("inGame", true);
        root.addProperty("x", round1(client.player.getX()));
        root.addProperty("y", round1(client.player.getY()));
        root.addProperty("z", round1(client.player.getZ()));
        root.addProperty("health", round1(client.player.getHealth()));
        root.addProperty("maxHealth", round1(client.player.getMaxHealth()));
        root.addProperty("foodLevel", client.player.getFoodData().getFoodLevel());
        root.addProperty("dimension", client.level.dimension().identifier().toString());

        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        boolean baritoneBusy = baritone != null && (
                baritone.getPathingBehavior().isPathing()
                        || baritone.getMineProcess().isActive()
                        || baritone.getFollowProcess().isActive()
                        || baritone.getCustomGoalProcess().isActive()
                        || baritone.getBuilderProcess().isActive()
                        || baritone.getExploreProcess().isActive()
                        || baritone.getFarmProcess().isActive()
        );
        root.addProperty("baritoneBusy", baritoneBusy);
        root.addProperty("baritonePlusTask", BaritonePlusClient.TASK_MANAGER.getStatusSummary());

        // Combat & Defense state
        BaritonePlusConfig.CombatConfig combatCfg = BaritonePlusConfig.get().combat;
        BaritonePlusConfig.AutoEatConfig eatCfg = BaritonePlusConfig.get().autoEat;
        JsonObject combatJson = new JsonObject();
        combatJson.addProperty("combatEnabled", combatCfg.enabled);
        combatJson.addProperty("autoShield", combatCfg.autoShield);
        combatJson.addProperty("autoEatEnabled", eatCfg.enabled);
        combatJson.addProperty("isEating", BaritonePlusClient.AUTO_EAT.isEating());
        combatJson.addProperty("inDanger", BaritonePlusClient.COMBAT_CONTROLLER.isInDanger(client));
        root.add("combat", combatJson);

        // Compact inventory summary (item registry path -> total count)
        Map<String, Integer> invCounts = new LinkedHashMap<>();
        int size = client.player.getInventory().getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack stack = client.player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
                invCounts.merge(path, stack.getCount(), Integer::sum);
            }
        }
        JsonObject invJson = new JsonObject();
        invCounts.forEach(invJson::addProperty);
        root.add("inventory", invJson);

        // Nearby entities within 16 blocks (up to 15 closest)
        List<Entity> nearby = new ArrayList<>();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity != client.player && entity.isAlive() && client.player.distanceToSqr(entity) <= 16.0 * 16.0) {
                nearby.add(entity);
            }
        }
        nearby.sort(Comparator.comparingDouble(e -> client.player.distanceToSqr(e)));
        JsonArray entitiesArr = new JsonArray();
        int maxEntities = Math.min(15, nearby.size());
        for (int i = 0; i < maxEntities; i++) {
            Entity e = nearby.get(i);
            JsonObject eObj = new JsonObject();
            eObj.addProperty("type", BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath());
            eObj.addProperty("name", e.getName().getString());
            eObj.addProperty("distance", round1(client.player.distanceTo(e)));
            eObj.addProperty("hostile", e instanceof Monster);
            eObj.addProperty("isPlayer", e instanceof Player);
            if (e instanceof LivingEntity living) {
                eObj.addProperty("health", round1(living.getHealth()));
            }
            entitiesArr.add(eObj);
        }
        root.add("nearbyEntities", entitiesArr);

        // Recent chat messages (last 10)
        List<String> recentChat = ChatTracker.getRecent(10);
        if (!recentChat.isEmpty()) {
            JsonArray chatArr = new JsonArray();
            recentChat.forEach(chatArr::add);
            root.add("recentChat", chatArr);
        }

        // Sidebar scoreboard (useful for subserver/network context)
        try {
            Scoreboard scoreboard = client.level.getScoreboard();
            Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
            if (sidebar != null) {
                JsonObject sbJson = new JsonObject();
                sbJson.addProperty("title", sidebar.getDisplayName().getString());
                JsonArray lines = new JsonArray();
                int count = 0;
                for (PlayerScoreEntry entry : scoreboard.listPlayerScores(sidebar)) {
                    if (count++ >= 12) break;
                    String text = entry.display() != null
                            ? entry.display().getString()
                            : entry.owner();
                    lines.add(text);
                }
                sbJson.add("lines", lines);
                root.add("scoreboard", sbJson);
            }
        } catch (Exception ignored) {
            // Scoreboard structure is optional
        }

        // Open container / server GUI slots
        if (client.gui.screen() instanceof AbstractContainerScreen<?> screen && client.player.containerMenu != null) {
            JsonObject guiJson = new JsonObject();
            guiJson.addProperty("title", screen.getTitle().getString());
            JsonArray slotsArr = new JsonArray();
            int maxSlots = Math.min(54, client.player.containerMenu.slots.size());
            for (int i = 0; i < maxSlots; i++) {
                Slot slot = client.player.containerMenu.getSlot(i);
                ItemStack stack = slot.getItem();
                if (!stack.isEmpty()) {
                    JsonObject sObj = new JsonObject();
                    sObj.addProperty("slot", i);
                    sObj.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
                    sObj.addProperty("count", stack.getCount());
                    sObj.addProperty("name", stack.getHoverName().getString());
                    slotsArr.add(sObj);
                }
            }
            guiJson.add("slots", slotsArr);
            root.add("openScreen", guiJson);
        }

        return root;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
