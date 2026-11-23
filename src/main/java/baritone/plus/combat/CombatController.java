package baritone.plus.combat;

import baritone.plus.BaritonePlusClient;
import baritone.plus.config.BaritonePlusConfig;
import baritone.plus.util.AutoCrafter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Items;

/**
 * Built-in Meteor-inspired combat and shield defense controller.
 * Auto-Shield and Combat run independently of AI (and Auto-Shield works even when Combat attacking is off).
 */
public class CombatController {

    private boolean isBlocking = false;

    public void tick(Minecraft client) {
        if (client == null || client.player == null || client.level == null || client.gameMode == null) {
            releaseShield(client);
            return;
        }

        // Anti-cheat safety: never block or attack while a container GUI is open
        if (client.gui.screen() instanceof AbstractContainerScreen<?>) {
            releaseShield(client);
            return;
        }

        // Pause shield/combat while AutoEat is actively eating
        if (BaritonePlusClient.AUTO_EAT.isEating() && BaritonePlusConfig.get().autoEat.pauseCombat) {
            isBlocking = false;
            return;
        }

        BaritonePlusConfig.CombatConfig cfg = BaritonePlusConfig.get().combat;

        // Auto-Shield runs independently of combat.enabled
        if (handleAutoShield(client, cfg)) {
            return;
        }

        if (!cfg.enabled) {
            return;
        }

        handleCombat(client, cfg);
    }

    public boolean isInDanger(Minecraft client) {
        if (client == null || client.player == null) {
            return false;
        }
        return client.player.getHealth() <= BaritonePlusConfig.get().combat.fleeHealthThreshold;
    }

    private void releaseShield(Minecraft client) {
        if (isBlocking) {
            if (client != null && client.options != null) {
                client.options.keyUse.setDown(false);
            }
            isBlocking = false;
        }
    }

    private boolean handleAutoShield(Minecraft client, BaritonePlusConfig.CombatConfig cfg) {
        if (!cfg.autoShield || !client.player.getOffhandItem().is(Items.SHIELD)) {
            releaseShield(client);
            return false;
        }

        boolean threat = false;
        var player = client.player;

        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == player || !entity.isAlive()) continue;

            double distSq = player.distanceToSqr(entity);
            if (entity instanceof AbstractArrow && distSq <= 12.0 * 12.0) {
                threat = true;
                break;
            }
            if (entity instanceof Creeper && distSq <= 5.0 * 5.0) {
                threat = true;
                break;
            }
            if (isValidTarget(player, entity, cfg) && distSq <= 4.5 * 4.5
                    && (!cfg.enabled || player.getAttackStrengthScale(0.5f) < 0.85f)) {
                threat = true;
                break;
            }
        }

        if (threat) {
            client.options.keyUse.setDown(true);
            isBlocking = true;
            return true;
        }

        releaseShield(client);
        return false;
    }

    private void handleCombat(Minecraft client, BaritonePlusConfig.CombatConfig cfg) {
        var player = client.player;
        double maxDistSq = cfg.attackRange * cfg.attackRange;
        LivingEntity bestTarget = null;
        double bestDistSq = maxDistSq;

        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || entity == player || !living.isAlive()) {
                continue;
            }
            if (!isValidTarget(player, living, cfg)) {
                continue;
            }
            double distSq = player.distanceToSqr(living);
            if (distSq <= bestDistSq && player.hasLineOfSight(living)) {
                bestDistSq = distSq;
                bestTarget = living;
            }
        }

        if (bestTarget == null) {
            return;
        }

        boolean aimed = RotationManager.rotateTowards(
                client,
                bestTarget.getBoundingBox().getCenter(),
                cfg.maxTurnSpeedDegPerTick
        );

        if (!aimed) {
            return;
        }

        if (!cfg.respectAttackCooldown || player.getAttackStrengthScale(0.5f) >= 1.0f) {
            client.gameMode.attack(player, bestTarget);
            AutoCrafter.swingMainHand(client);
        }
    }

    private boolean isValidTarget(Player player, Entity entity, BaritonePlusConfig.CombatConfig cfg) {
        if (entity == player || !entity.isAlive()) return false;
        if (cfg.targetHostiles && entity instanceof Monster) return true;
        return cfg.targetPlayers && entity instanceof Player;
    }
}
