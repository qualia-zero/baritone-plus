package baritone.plus.util;

import baritone.plus.config.BaritonePlusConfig;

/**
 * Legacy compatibility wrapper around {@link BaritonePlusConfig}.
 */
public class BetaConfig {

    public static boolean isBetaEnabled() {
        return BaritonePlusConfig.get().general.betaFeatures;
    }

    public static void setBetaEnabled(boolean enabled) {
        BaritonePlusConfig.get().general.betaFeatures = enabled;
        BaritonePlusConfig.save();
    }
}
