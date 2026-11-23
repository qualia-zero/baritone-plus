package baritone.plus.ai;

import baritone.plus.config.BaritonePlusConfig;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Lightweight async REST client for the Gemini v1 Interactions API.
 * Uses Java's built-in HttpClient and Minecraft's bundled Gson (zero extra dependencies).
 */
public class GeminiClient {

    private static final String INTERACTIONS_URL =
            "https://generativelanguage.googleapis.com/v1/interactions";

    private static final Gson GSON = new Gson();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public record FunctionCall(String id, String name, JsonObject arguments) {}

    public record InteractionResponse(
            String id,
            String status,
            String outputText,
            List<FunctionCall> functionCalls,
            String error
    ) {}

    public CompletableFuture<InteractionResponse> createInteraction(
            String apiKey,
            String model,
            BaritonePlusConfig.ThinkingLevel thinkingLevel,
            String systemInstruction,
            JsonElement input,
            String previousInteractionId,
            JsonArray tools
    ) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        if (systemInstruction != null && !systemInstruction.isBlank()) {
            body.addProperty("system_instruction", systemInstruction);
        }
        body.add("input", input);
        if (tools != null && !tools.isEmpty()) {
            body.add("tools", tools);
        }
        if (previousInteractionId != null && !previousInteractionId.isBlank()) {
            body.addProperty("previous_interaction_id", previousInteractionId);
        }

        JsonObject genConfig = new JsonObject();
        int maxTokens = (thinkingLevel == BaritonePlusConfig.ThinkingLevel.MEDIUM
                || thinkingLevel == BaritonePlusConfig.ThinkingLevel.HIGH) ? 4096 : 1536;
        genConfig.addProperty("max_output_tokens", maxTokens);
        if (thinkingLevel != null && thinkingLevel != BaritonePlusConfig.ThinkingLevel.DEFAULT) {
            genConfig.addProperty("thinking_level", thinkingLevel.name().toLowerCase());
        }
        body.add("generation_config", genConfig);

        body.addProperty("store", true);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(INTERACTIONS_URL))
                .timeout(Duration.ofSeconds(75))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::parseResponse)
                .exceptionally(ex -> new InteractionResponse(null, "error", null, List.of(), ex.getMessage()));
    }

    private InteractionResponse parseResponse(HttpResponse<String> response) {
        int code = response.statusCode();
        String raw = response.body() == null ? "" : response.body();
        if (code < 200 || code >= 300) {
            String snippet = raw.length() > 300 ? raw.substring(0, 300) + "..." : raw;
            return new InteractionResponse(null, "error", null, List.of(), "HTTP " + code + ": " + snippet);
        }

        try {
            JsonObject json = JsonParser.parseString(raw).getAsJsonObject();
            String id = json.has("id") && !json.get("id").isJsonNull() ? json.get("id").getAsString() : null;
            String status = json.has("status") && !json.get("status").isJsonNull() ? json.get("status").getAsString() : "";

            StringBuilder textOut = new StringBuilder();
            List<FunctionCall> calls = new ArrayList<>();

            if (json.has("steps") && json.get("steps").isJsonArray()) {
                for (JsonElement el : json.getAsJsonArray("steps")) {
                    if (!el.isJsonObject()) continue;
                    JsonObject step = el.getAsJsonObject();
                    String type = step.has("type") && !step.get("type").isJsonNull()
                            ? step.get("type").getAsString() : "";

                    if ("function_call".equals(type)) {
                        String callId = step.has("id") ? step.get("id").getAsString() : "";
                        String name = step.has("name") ? step.get("name").getAsString() : "";
                        JsonObject args = step.has("arguments") && step.get("arguments").isJsonObject()
                                ? step.getAsJsonObject("arguments") : new JsonObject();
                        calls.add(new FunctionCall(callId, name, args));
                    } else if ("model_output".equals(type) && step.has("content") && step.get("content").isJsonArray()) {
                        for (JsonElement cEl : step.getAsJsonArray("content")) {
                            if (!cEl.isJsonObject()) continue;
                            JsonObject cObj = cEl.getAsJsonObject();
                            String cType = cObj.has("type") ? cObj.get("type").getAsString() : "";
                            if ("text".equals(cType) && cObj.has("text")) {
                                if (!textOut.isEmpty()) textOut.append("\n");
                                textOut.append(cObj.get("text").getAsString());
                            }
                        }
                    } else if ("text".equals(type) && step.has("text")) {
                        if (!textOut.isEmpty()) textOut.append("\n");
                        textOut.append(step.get("text").getAsString());
                    }
                }
            }

            return new InteractionResponse(id, status, textOut.toString().trim(), calls, null);
        } catch (Exception e) {
            return new InteractionResponse(null, "error", null, List.of(), "Parse error: " + e.getMessage());
        }
    }
}
