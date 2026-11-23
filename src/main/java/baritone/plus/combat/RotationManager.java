package baritone.plus.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Smooth, turn-speed-limited rotation helper for anti-cheat-friendly aiming.
 */
public class RotationManager {

    private RotationManager() {}

    /**
     * Rotates the player smoothly toward {@code target} at up to {@code maxDegPerTick} degrees per tick.
     *
     * @return true if already within 5 degrees of the target angle
     */
    public static boolean rotateTowards(Minecraft client, Vec3 target, double maxDegPerTick) {
        if (client == null || client.player == null || target == null) {
            return false;
        }

        Vec3 eyePos = client.player.getEyePosition();
        double dx = target.x - eyePos.x;
        double dy = target.y - eyePos.y;
        double dz = target.z - eyePos.z;
        double horizDist = Math.sqrt(dx * dx + dz * dz);

        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float targetPitch = (float) (-Math.toDegrees(Math.atan2(dy, horizDist)));

        float currentYaw = client.player.getYRot();
        float currentPitch = client.player.getXRot();

        float deltaYaw = Mth.wrapDegrees(targetYaw - currentYaw);
        float deltaPitch = Mth.wrapDegrees(targetPitch - currentPitch);

        float maxStep = (float) Math.max(1.0, maxDegPerTick);
        float stepYaw = Mth.clamp(deltaYaw, -maxStep, maxStep);
        float stepPitch = Mth.clamp(deltaPitch, -maxStep, maxStep);

        if (client.options != null) {
            double sens = client.options.sensitivity().get() * 0.6 + 0.2;
            double gcd = sens * sens * sens * 1.2;
            if (gcd > 1e-6) {
                stepYaw = (float) (Math.round(stepYaw / gcd) * gcd);
                stepPitch = (float) (Math.round(stepPitch / gcd) * gcd);
            }
        }

        client.player.setYRot(currentYaw + stepYaw);
        client.player.setXRot(Mth.clamp(currentPitch + stepPitch, -90.0f, 90.0f));

        return Math.abs(deltaYaw) <= 5.0f && Math.abs(deltaPitch) <= 5.0f;
    }
}
