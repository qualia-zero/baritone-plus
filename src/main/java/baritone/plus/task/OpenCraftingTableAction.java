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

public class OpenCraftingTableAction implements Action {

    @Override
    public void execute(Minecraft client, IBaritone baritone) {
        if (client.player == null || client.level == null) return;

        if (AutoCrafter.isCraftingScreenOpen(client)) {
            return; // Already open!
        }

        BlockPos tablePos = AutoCrafter.findNearbyBlock(client, Blocks.CRAFTING_TABLE, 5);
        if (tablePos == null) {
            AutoCrafter.log(client, "§cError: No crafting table found within 5 blocks!");
            BaritonePlusClient.TASK_MANAGER.failTask();
            return;
        }

        // Right click the crafting table
        Vec3 hitVec = new Vec3(tablePos.getX() + 0.5, tablePos.getY() + 0.5, tablePos.getZ() + 0.5);
        
        // Look at the table before right-clicking
        AutoCrafter.lookAt(client, hitVec);
        
        BlockHitResult hitResult = new BlockHitResult(
            hitVec,
            Direction.UP,
            tablePos,
            false
        );

        client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hitResult);
        client.player.swing(InteractionHand.MAIN_HAND);
    }

    @Override
    public String getDescription() {
        return "Opening crafting table GUI";
    }
}
