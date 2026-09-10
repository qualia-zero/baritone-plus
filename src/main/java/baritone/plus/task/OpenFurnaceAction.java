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

public class OpenFurnaceAction implements Action {

    @Override
    public void execute(Minecraft client, IBaritone baritone) {
        if (client.player == null || client.level == null) return;

        if (AutoCrafter.isFurnaceScreenOpen(client)) {
            return; // Already open!
        }

        BlockPos furnacePos = AutoCrafter.findNearbyBlock(client, Blocks.FURNACE, 5);
        if (furnacePos == null) {
            AutoCrafter.log(client, "§cError: No furnace found within 5 blocks!");
            BaritonePlusClient.TASK_MANAGER.failTask();
            return;
        }

        // Right click the furnace
        Vec3 hitVec = new Vec3(furnacePos.getX() + 0.5, furnacePos.getY() + 0.5, furnacePos.getZ() + 0.5);
        
        // Look at the furnace before right-clicking
        AutoCrafter.lookAt(client, hitVec);
        
        BlockHitResult hitResult = new BlockHitResult(
            hitVec,
            Direction.UP,
            furnacePos,
            false
        );

        client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hitResult);
        client.player.swing(InteractionHand.MAIN_HAND);
    }

    @Override
    public String getDescription() {
        return "Opening furnace GUI";
    }
}
