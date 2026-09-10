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

public class PlaceAction implements Action {

    private final String block;

    public PlaceAction(String block) {
        this.block = block;
    }

    @Override
    public void execute(Minecraft client, IBaritone baritone) {
        if (client.player == null || client.level == null) return;

        // Ensure the item is in hand
        if (!AutoCrafter.selectItemInHotbar(client, block)) {
            AutoCrafter.log(client, "§cError: Could not select " + block + " in hotbar!");
            BaritonePlusClient.TASK_MANAGER.failTask();
            return;
        }

        BlockPos targetPos = null;
        BlockPos playerPos = client.player.blockPosition();
        
        // Search in a 5x3x5 area around the player for a safe, non-colliding air block with a solid block underneath
        for (int x = -2; x <= 2; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -2; z <= 2; z++) {
                    BlockPos pos = playerPos.offset(x, y, z);
                    if (client.level.getBlockState(pos).isAir() 
                            && !client.level.getBlockState(pos.below()).isAir()
                            && !pos.equals(playerPos) 
                            && !pos.equals(playerPos.above())) {
                        
                        double distSq = client.player.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                        if (distSq > 0.8) { // Make sure the player is not directly standing inside it
                            targetPos = pos;
                            break;
                        }
                    }
                }
                if (targetPos != null) break;
            }
            if (targetPos != null) break;
        }
        
        if (targetPos == null) {
            targetPos = playerPos.relative(Direction.NORTH); // Fallback to a block in front of the player rather than directly under them
        }

        BlockPos supportPos = targetPos.below();
        Vec3 hitVec = new Vec3(supportPos.getX() + 0.5, supportPos.getY() + 1.0, supportPos.getZ() + 0.5);
        
        // Look at the block before placing
        AutoCrafter.lookAt(client, hitVec);
        
        BlockHitResult hitResult = new BlockHitResult(
            hitVec,
            Direction.UP,
            supportPos,
            false
        );

        client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hitResult);
        client.player.swing(InteractionHand.MAIN_HAND);
        AutoCrafter.log(client, "§aPlacing " + block + " at " + targetPos.toShortString());
    }

    @Override
    public boolean shouldLog() {
        return false;
    }

    @Override
    public String getDescription() {
        return "Placing " + block;
    }
}
