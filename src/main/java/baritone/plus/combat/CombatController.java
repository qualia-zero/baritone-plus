package baritone.plus.combat;

import baritone.plus.config.BaritonePlusConfig;
import baritone.plus.util.AutoCrafter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Built-in Meteor-inspired combat and survival defense controller.
 * Runs continuously on client ticks (including between AI tasks) when enabled.
 */
public class CombatController {

    private boolean isEating = false;
    private int preEatSlot = -1;
    private boolean isBlocking = false;

    public void tick(Minecraft client) {
        if (client == null || client.player == null || client.level == null || client.gameMode == null) {
            releaseUseKey(client);
            return;
        }

        BaritonePlusConfig.CombatConfig cfg = BaritonePlusConfig.get().combat;
        if (!cfg.enabled) {
            releaseUseKey(client);
            return;
        }

        // Anti-cheat safety: never eat, block, or attack while a container GUI is open
        if (client.gui.screen() instanceof AbstractContainerScreen<?>) {
            releaseUseKey(client);
            return;
        }

        if (handleAutoEat(client, cfg)) {
            return;
        }

        if (handleAutoShield(client, cfg)) {
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

    private void releaseUseKey(Minecraft client) {
        if (isEating) {
            if (client != null && client.options != null) {
                client.options.keyUse.setDown(false);
            }
            if (client != null && client.player != null && preEatSlot >= 0 && preEatSlot < 9) {
                client.player.getInventory().setSelectedSlot(preEatSlot);
            }
            isEating = false;
            preEatSlot = -1;
        }
        if (isBlocking) {
            if (client != null && client.options != null) {
                client.options.keyUse.setDown(false);
            }
            isBlocking = false;
        }
    }

    private boolean handleAutoEat(Minecraft client, BaritonePlusConfig.CombatConfig cfg) {
        if (!cfg.autoEat) {
            if (isEating) releaseUseKey(client);
            return false;
        }

        var player = client.player;
        boolean needsFood = player.getFoodData().getFoodLevel() <= 16
                || player.getHealth() < player.getMaxHealth() - 4.0f;

        if (!isEating && !needsFood) {
            return false;
        }

        if (isEating && player.getFoodData().getFoodLevel() >= 19) {
            releaseUseKey(client);
            return false;
        }

        int foodSlot = findBestFoodHotbarSlot(player);
        if (foodSlot == -1) {
            if (isEating) releaseUseKey(client);
            return false;
        }

        if (!isEating) {
            preEatSlot = player.getInventory().getSelectedSlot();
            isEating = true;
            if (isBlocking) {
                isBlocking = false;
            }
        }

        player.getInventory().setSelectedSlot(foodSlot);
        client.options.keyUse.setDown(true);
        return true;
    }

    private int findBestFoodHotbarSlot(Player player) {
        int fallback = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            FoodProperties food = stack.get(DataComponents.FOOD);
            if (food == null) continue;
            if (stack.is(Items.ROTTEN_FLESH) || stack.is(Items.SPIDER_EYE) || stack.is(Items.PUFFERFISH)) {
                if (fallback == -1) fallback = i;
                continue;
            }
            return i;
        }
        return fallback;
    }

    private boolean handleAutoShield(Minecraft client, BaritonePlusConfig.CombatConfig cfg) {
        if (!cfg.autoShield || !client.player.getOffhandItem().is(Items.SHIELD)) {
            if (isBlocking) {
                client.options.keyUse.setDown(false);
                isBlocking = false;
            }
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
                    && player.getAttackStrengthScale(0.5f) < 0.85f) {
                threat = true;
                break;
            }
        }

        if (threat) {
            client.options.keyUse.setDown(true);
            isBlocking = true;
            return true;
        }

        if (isBlocking) {
            client.options.keyUse.setDown(false);
            isBlocking = false;
        }
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
