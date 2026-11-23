package baritone.plus.config;

import baritone.plus.BaritonePlusClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Central JSON-backed configuration for Baritone+.
 */
public class BaritonePlusConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("baritone-plus.json");
    private static final Path LEGACY_BETA_PATH = FabricLoader.getInstance().getConfigDir().resolve("baritone-plus-beta.txt");

    private static BaritonePlusConfig instance = new BaritonePlusConfig();

    public enum AntiCheatProfile {
        VANILLA,
        STRICT
    }

    public enum RotationMode {
        CLIENT,
        SERVER_SILENT
    }

    public GeneralConfig general = new GeneralConfig();
    public CombatConfig combat = new CombatConfig();
    public ContainerConfig container = new ContainerConfig();
    public ChatConfig chat = new ChatConfig();
    public AiBridgeConfig aiBridge = new AiBridgeConfig();

    public static class GeneralConfig {
        public boolean betaFeatures = false;
        public boolean debugLogging = false;
        public AntiCheatProfile antiCheatProfile = AntiCheatProfile.VANILLA;
    }

    public static class CombatConfig {
        public boolean enabled = false;
        public boolean autoShield = true;
        public boolean autoEat = true;
        public double fleeHealthThreshold = 6.0;
        public double attackRange = 3.0;
        public RotationMode rotationMode = RotationMode.SERVER_SILENT;
        public double maxTurnSpeedDegPerTick = 45.0;
        public boolean respectAttackCooldown = true;
        public boolean targetHostiles = true;
        public boolean targetPlayers = false;
    }

    public static class ContainerConfig {
        public int clickDelayTicks = 2;
        public List<String> protectedItems = new ArrayList<>(List.of(
                "diamond_pickaxe", "netherite_pickaxe", "shield", "totem_of_undying"
        ));
    }

    public static class ChatConfig {
        public int minSendIntervalMs = 1200;
        public int maxHistorySize = 50;
    }

    public static class AiBridgeConfig {
        public boolean enabled = false;
        public String host = "127.0.0.1";
        public int port = 8765;
        public int snapshotIntervalTicks = 20;
        public boolean allowChatSend = false;
        public boolean allowGuiInteraction = true;
        public boolean allowCombatOverride = true;
    }

    public static BaritonePlusConfig get() {
        return instance;
    }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                BaritonePlusConfig loaded = GSON.fromJson(reader, BaritonePlusConfig.class);
                if (loaded != null) {
                    loaded.ensureDefaults();
                    instance = loaded;
                    return;
                }
            } catch (Exception e) {
                BaritonePlusClient.LOGGER.warn("Failed to read baritone-plus.json, falling back to defaults.", e);
            }
        }

        instance = new BaritonePlusConfig();
        migrateLegacyBetaFlag();
        save();
    }

    public static void save() {
        instance.ensureDefaults();
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException e) {
            BaritonePlusClient.LOGGER.warn("Failed to save baritone-plus.json.", e);
        }
    }

    public static void resetDefaults() {
        instance = new BaritonePlusConfig();
        save();
    }

    public void applyProfile(AntiCheatProfile profile) {
        if (profile == null) return;
        general.antiCheatProfile = profile;
        switch (profile) {
            case VANILLA -> {
                combat.rotationMode = RotationMode.SERVER_SILENT;
                combat.maxTurnSpeedDegPerTick = 90.0;
                combat.attackRange = 3.0;
                container.clickDelayTicks = 1;
            }
            case STRICT -> {
                combat.rotationMode = RotationMode.SERVER_SILENT;
                combat.maxTurnSpeedDegPerTick = 30.0;
                combat.attackRange = 2.85;
                combat.respectAttackCooldown = true;
                container.clickDelayTicks = 3;
            }
        }
        save();
    }

    private void ensureDefaults() {
        if (general == null) general = new GeneralConfig();
        if (general.antiCheatProfile == null) general.antiCheatProfile = AntiCheatProfile.VANILLA;

        if (combat == null) combat = new CombatConfig();
        if (combat.rotationMode == null) combat.rotationMode = RotationMode.SERVER_SILENT;

        if (container == null) container = new ContainerConfig();
        if (container.protectedItems == null) {
            container.protectedItems = new ArrayList<>(List.of(
                    "diamond_pickaxe", "netherite_pickaxe", "shield", "totem_of_undying"
            ));
        }

        if (chat == null) chat = new ChatConfig();
        if (aiBridge == null) aiBridge = new AiBridgeConfig();
        if (aiBridge.host == null || aiBridge.host.isBlank()) aiBridge.host = "127.0.0.1";
    }

    private static void migrateLegacyBetaFlag() {
        if (!Files.exists(LEGACY_BETA_PATH)) {
            return;
        }
        try {
            String raw = Files.readString(LEGACY_BETA_PATH).trim();
            if (raw.equalsIgnoreCase("true")) {
                instance.general.betaFeatures = true;
            }
        } catch (IOException ignored) {
            // Ignore legacy read errors
        }
    }
}
