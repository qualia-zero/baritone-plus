package baritone.plus.network;

import baritone.plus.config.BaritonePlusConfig;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Buffers recent incoming game/chat messages for the optional Gemini orchestrator.
 */
public class ChatTracker {

    private static final Deque<String> HISTORY = new ArrayDeque<>();

    public static void register() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) {
                record(message);
            }
        });
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            record(message);
        });
    }

    private static synchronized void record(Component message) {
        if (message == null) return;
        String text = message.getString();
        if (text == null || text.isBlank()) return;
        // Avoid echo-looping our own Baritone+ / AI status messages back to Gemini
        if (text.startsWith("[Baritone+]")) return;

        int max = Math.max(10, BaritonePlusConfig.get().chat.maxHistorySize);
        while (HISTORY.size() >= max) {
            HISTORY.pollFirst();
        }
        HISTORY.addLast(text);
    }

    public static synchronized List<String> getRecent(int limit) {
        if (HISTORY.isEmpty() || limit <= 0) {
            return List.of();
        }
        List<String> all = new ArrayList<>(HISTORY);
        int from = Math.max(0, all.size() - limit);
        return new ArrayList<>(all.subList(from, all.size()));
    }
}
