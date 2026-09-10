package baritone.plus.util;

import net.fabricmc.loader.api.FabricLoader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class BetaConfig {
    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("baritone-plus-beta.txt").toFile();

    public static boolean isBetaEnabled() {
        if (!CONFIG_FILE.exists()) {
            return false;
        }
        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            char[] buf = new char[8];
            int read = reader.read(buf);
            if (read > 0) {
                return new String(buf, 0, read).trim().equalsIgnoreCase("true");
            }
        } catch (IOException e) {
            // Ignore
        }
        return false;
    }

    public static void setBetaEnabled(boolean enabled) {
        try {
            if (!CONFIG_FILE.getParentFile().exists()) {
                CONFIG_FILE.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                writer.write(enabled ? "true" : "false");
            }
        } catch (IOException e) {
            // Ignore
        }
    }
}
