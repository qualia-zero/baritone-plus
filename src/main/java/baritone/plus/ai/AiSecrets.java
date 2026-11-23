package baritone.plus.ai;

import baritone.plus.BaritonePlusClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Manages the optional Gemini API key outside of baritone-plus.json.
 * Checks GEMINI_API_KEY env var first, then config/baritone-plus-secrets.txt.
 */
public class AiSecrets {

    private static final Path SECRETS_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("baritone-plus-secrets.txt");

    public static String getApiKey() {
        String envKey = System.getenv("GEMINI_API_KEY");
        if (envKey != null && !envKey.isBlank()) {
            return envKey.trim();
        }
        if (Files.exists(SECRETS_PATH)) {
            try {
                String fileKey = Files.readString(SECRETS_PATH).trim();
                if (!fileKey.isEmpty()) {
                    return fileKey;
                }
            } catch (IOException e) {
                BaritonePlusClient.LOGGER.warn("Failed to read baritone-plus-secrets.txt.", e);
            }
        }
        return null;
    }

    public static void saveApiKey(String key) {
        try {
            Files.createDirectories(SECRETS_PATH.getParent());
            Files.writeString(SECRETS_PATH, key == null ? "" : key.trim());
        } catch (IOException e) {
            BaritonePlusClient.LOGGER.warn("Failed to save baritone-plus-secrets.txt.", e);
        }
    }

    public static boolean hasApiKey() {
        return getApiKey() != null;
    }
}
