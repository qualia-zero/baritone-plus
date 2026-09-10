package baritone.plus.task;

import baritone.plus.BaritonePlusClient;
import baritone.plus.util.AutoCrafter;
import baritone.api.IBaritone;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

public class BuildPortalAction implements Action {

    private BlockPos cornerPos = null;
    private boolean xAligned = true;

    private static class BlockPosInfo {
        final int dx;
        final int dy;
        final boolean isObsidian;
        final int sdx;
        final int sdy;

        BlockPosInfo(int dx, int dy, boolean isObsidian, int sdx, int sdy) {
            this.dx = dx;
            this.dy = dy;
            this.isObsidian = isObsidian;
            this.sdx = sdx;
            this.sdy = sdy;
        }
    }

    private final List<BlockPosInfo> blocks = new ArrayList<>();

    public BuildPortalAction() {
        // Define placement order and support blocks in local coordinates (Z=0)
        // 1. Bottom-left corner (temp)
        blocks.add(new BlockPosInfo(0, 0, false, 0, -1));
        // 2. Bottom-right corner (temp)
        blocks.add(new BlockPosInfo(3, 0, false, 3, -1));
        // 3. Bottom 1 (obsidian)
        blocks.add(new BlockPosInfo(1, 0, true, 1, -1));
        // 4. Bottom 2 (obsidian)
        blocks.add(new BlockPosInfo(2, 0, true, 2, -1));
        
        // 5. Left 1 (obsidian)
        blocks.add(new BlockPosInfo(0, 1, true, 0, 0));
        // 6. Left 2 (obsidian)
        blocks.add(new BlockPosInfo(0, 2, true, 0, 1));
        // 7. Left 3 (obsidian)
        blocks.add(new BlockPosInfo(0, 3, true, 0, 2));
        // 8. Top-left corner (temp)
        blocks.add(new BlockPosInfo(0, 4, false, 0, 3));
        
        // 9. Right 1 (obsidian)
        blocks.add(new BlockPosInfo(3, 1, true, 3, 0));
        // 10. Right 2 (obsidian)
        blocks.add(new BlockPosInfo(3, 2, true, 3, 1));
        // 11. Right 3 (obsidian)
        blocks.add(new BlockPosInfo(3, 3, true, 3, 2));
        // 12. Top-right corner (temp)
        blocks.add(new BlockPosInfo(3, 4, false, 3, 3));
        
        // 13. Top 1 (obsidian)
        blocks.add(new BlockPosInfo(1, 4, true, 0, 4));
        // 14. Top 2 (obsidian)
        blocks.add(new BlockPosInfo(2, 4, true, 3, 4));
    }

    @Override
    public void execute(Minecraft client, IBaritone baritone) {
        if (client.player == null || client.level == null) return;

        if (cornerPos == null) {
            // Find a valid spot to build the portal
            BlockPos playerPos = client.player.blockPosition();
            BlockPos bestPos = null;
            boolean bestXAligned = true;

            outer:
            for (int y = -3; y <= 3; y++) {
                for (int x = -8; x <= 8; x++) {
                    for (int z = -8; z <= 8; z++) {
                        BlockPos candidate = playerPos.offset(x, y, z);
                        if (isValidLocation(client, candidate, true)) {
                            bestPos = candidate;
                            bestXAligned = true;
                            break outer;
                        }
                        if (isValidLocation(client, candidate, false)) {
                            bestPos = candidate;
                            bestXAligned = false;
                            break outer;
                        }
                    }
                }
            }

            if (bestPos == null) {
                AutoCrafter.log(client, "§cError: Could not find a suitable flat 4x5 space to build the Nether Portal!");
                BaritonePlusClient.TASK_MANAGER.failTask();
                return;
            }

            cornerPos = bestPos;
            xAligned = bestXAligned;
            AutoCrafter.log(client, "§aFound Nether Portal location at " + cornerPos.toShortString() + " (X-Aligned: " + xAligned + ")");
        }

        // Check if player needs to walk closer to a safe standing position
        double standX, standZ;
        if (xAligned) {
            standX = cornerPos.getX() + 1.5;
            double z1 = cornerPos.getZ() - 2.5;
            double z2 = cornerPos.getZ() + 2.5;
            double dist1 = client.player.distanceToSqr(standX, cornerPos.getY(), z1);
            double dist2 = client.player.distanceToSqr(standX, cornerPos.getY(), z2);
            standZ = (dist1 < dist2) ? z1 : z2;
        } else {
            standZ = cornerPos.getZ() + 1.5;
            double x1 = cornerPos.getX() - 2.5;
            double x2 = cornerPos.getX() + 2.5;
            double dist1 = client.player.distanceToSqr(x1, cornerPos.getY(), standZ);
            double dist2 = client.player.distanceToSqr(x2, cornerPos.getY(), standZ);
            standX = (dist1 < dist2) ? x1 : x2;
        }
        int standY = cornerPos.getY();

        double playerDistSq = client.player.distanceToSqr(standX, standY, standZ);
        if (playerDistSq > 3.0) { // If further than ~1.73 blocks away
            AutoCrafter.log(client, "§eMoving closer to portal build site...");
            baritone.getCommandManager().execute("goto " + (int)standX + " " + standY + " " + (int)standZ);
            BaritonePlusClient.TASK_MANAGER.prependAction(this);
            BaritonePlusClient.TASK_MANAGER.setTickDelay(20);
            return;
        }

        // Check if there is an unplaced block
        BlockPosInfo nextBlock = null;
        for (BlockPosInfo info : blocks) {
            BlockPos targetPos = getGlobalPos(cornerPos, info.dx, info.dy, xAligned);
            if (!isAlreadyPlaced(client, targetPos, info.isObsidian)) {
                nextBlock = info;
                break;
            }
        }

        if (nextBlock == null) {
            // All blocks are placed! Now light the portal!
            BlockPos portalBlockPos = getGlobalPos(cornerPos, 1, 1, xAligned);
            if (client.level.getBlockState(portalBlockPos).is(Blocks.NETHER_PORTAL)) {
                AutoCrafter.log(client, "§aNether Portal built and lit successfully!");
                return; // Finished!
            }

            // Light the portal (check if in hotbar first, otherwise swap and delay)
            if (!AutoCrafter.isItemInHotbar(client, "flint_and_steel")) {
                if (!AutoCrafter.selectItemInHotbar(client, "flint_and_steel")) {
                    AutoCrafter.log(client, "§cError: Could not select flint_and_steel in hotbar!");
                    BaritonePlusClient.TASK_MANAGER.failTask();
                    return;
                }
                BaritonePlusClient.TASK_MANAGER.prependAction(this);
                BaritonePlusClient.TASK_MANAGER.setTickDelay(5);
                return;
            }

            if (!AutoCrafter.selectItemInHotbar(client, "flint_and_steel")) {
                AutoCrafter.log(client, "§cError: Could not select flint_and_steel in hotbar!");
                BaritonePlusClient.TASK_MANAGER.failTask();
                return;
            }

            BlockPos obsidianPos = getGlobalPos(cornerPos, 1, 0, xAligned);
            Vec3 hitVec = new Vec3(obsidianPos.getX() + 0.5, obsidianPos.getY() + 1.0, obsidianPos.getZ() + 0.5);
            AutoCrafter.lookAt(client, hitVec);

            BlockHitResult hitResult = new BlockHitResult(
                hitVec,
                Direction.UP,
                obsidianPos,
                false
            );

            client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hitResult);
            client.player.swing(InteractionHand.MAIN_HAND);
            AutoCrafter.log(client, "§aLighting Nether Portal at " + obsidianPos.toShortString());

            BaritonePlusClient.TASK_MANAGER.prependAction(this);
            BaritonePlusClient.TASK_MANAGER.setTickDelay(20);
            return;
        }

        // Place the next block
        String itemToSelect = nextBlock.isObsidian ? "obsidian" : getTemporaryBlock(client);
        if (itemToSelect == null) {
            AutoCrafter.log(client, "§cError: No temporary block (cobblestone/dirt) found in inventory for portal corners!");
            BaritonePlusClient.TASK_MANAGER.failTask();
            return;
        }

        // Check if the item is in the hotbar (otherwise swap and delay to avoid race conditions)
        if (!AutoCrafter.isItemInHotbar(client, itemToSelect)) {
            if (!AutoCrafter.selectItemInHotbar(client, itemToSelect)) {
                AutoCrafter.log(client, "§cError: Could not select " + itemToSelect + " in hotbar!");
                BaritonePlusClient.TASK_MANAGER.failTask();
                return;
            }
            BaritonePlusClient.TASK_MANAGER.prependAction(this);
            BaritonePlusClient.TASK_MANAGER.setTickDelay(5);
            return;
        }

        if (!AutoCrafter.selectItemInHotbar(client, itemToSelect)) {
            AutoCrafter.log(client, "§cError: Could not select " + itemToSelect + " in hotbar!");
            BaritonePlusClient.TASK_MANAGER.failTask();
            return;
        }

        BlockPos targetPos = getGlobalPos(cornerPos, nextBlock.dx, nextBlock.dy, xAligned);
        BlockPos supportPos = getGlobalPos(cornerPos, nextBlock.sdx, nextBlock.sdy, xAligned);

        int dx = targetPos.getX() - supportPos.getX();
        int dy = targetPos.getY() - supportPos.getY();
        int dz = targetPos.getZ() - supportPos.getZ();
        Direction face = getDirectionFromDelta(dx, dy, dz);

        Vec3 hitVec = getHitVec(supportPos, face);
        AutoCrafter.lookAt(client, hitVec);

        BlockHitResult hitResult = new BlockHitResult(
            hitVec,
            face,
            supportPos,
            false
        );

        client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hitResult);
        client.player.swing(InteractionHand.MAIN_HAND);
        AutoCrafter.log(client, "§aPlacing " + itemToSelect + " at " + targetPos.toShortString());

        BaritonePlusClient.TASK_MANAGER.prependAction(this);
        BaritonePlusClient.TASK_MANAGER.setTickDelay(15);
    }

    private boolean isValidLocation(Minecraft client, BlockPos pos, boolean xAligned) {
        var level = client.level;
        if (level == null || client.player == null) return false;

        // Check ground below the 4 blocks
        for (int d = 0; d < 4; d++) {
            BlockPos groundPos = xAligned ? pos.offset(d, -1, 0) : pos.offset(0, -1, d);
            var state = level.getBlockState(groundPos);
            if (state.isAir()) {
                return false;
            }
        }

        // Check the 4x5 space itself
        for (int d = 0; d < 4; d++) {
            for (int dy = 0; dy < 5; dy++) {
                BlockPos framePos = xAligned ? pos.offset(d, dy, 0) : pos.offset(0, dy, d);
                var state = level.getBlockState(framePos);
                if (!state.isAir() && !state.is(Blocks.OBSIDIAN)) {
                    return false;
                }
            }
        }

        // Make sure the player is not standing inside any of the portal frame blocks
        BlockPos playerPos = client.player.blockPosition();
        for (int d = 0; d < 4; d++) {
            for (int dy = 0; dy < 5; dy++) {
                BlockPos framePos = xAligned ? pos.offset(d, dy, 0) : pos.offset(0, dy, d);
                if (framePos.equals(playerPos) || framePos.equals(playerPos.above())) {
                    return false;
                }
            }
        }

        // Make sure it's at a reach-able distance
        double distSq = client.player.distanceToSqr(pos.getX() + 1.5, pos.getY() + 2, pos.getZ() + 0.5);
        if (distSq < 2.0 || distSq > 36.0) {
            return false;
        }

        return true;
    }

    private boolean isAlreadyPlaced(Minecraft client, BlockPos pos, boolean expectedObsidian) {
        if (client.level == null) return false;
        var state = client.level.getBlockState(pos);
        if (expectedObsidian) {
            return state.is(Blocks.OBSIDIAN);
        } else {
            return !state.isAir();
        }
    }

    private String getTemporaryBlock(Minecraft client) {
        String[] options = {"cobblestone", "dirt", "stone", "andesite", "diorite", "granite", "cobbled_deepslate", "deepslate"};
        for (String option : options) {
            if (AutoCrafter.getItemCount(client, option) > 0) {
                return option;
            }
        }
        return null;
    }

    private BlockPos getGlobalPos(BlockPos corner, int dx, int dy, boolean xAligned) {
        if (xAligned) {
            return corner.offset(dx, dy, 0);
        } else {
            return corner.offset(0, dy, dx);
        }
    }

    private Direction getDirectionFromDelta(int x, int y, int z) {
        if (x > 0) return Direction.EAST;
        if (x < 0) return Direction.WEST;
        if (y > 0) return Direction.UP;
        if (y < 0) return Direction.DOWN;
        if (z > 0) return Direction.SOUTH;
        if (z < 0) return Direction.NORTH;
        return Direction.UP;
    }

    private Vec3 getHitVec(BlockPos supportPos, Direction face) {
        double x = supportPos.getX() + 0.5;
        double y = supportPos.getY() + 0.5;
        double z = supportPos.getZ() + 0.5;

        if (face == Direction.UP) y += 0.5;
        else if (face == Direction.DOWN) y -= 0.5;
        else if (face == Direction.EAST) x += 0.5;
        else if (face == Direction.WEST) x -= 0.5;
        else if (face == Direction.SOUTH) z += 0.5;
        else if (face == Direction.NORTH) z -= 0.5;

        return new Vec3(x, y, z);
    }

    @Override
    public String getDescription() {
        return "Building Nether Portal";
    }
}
