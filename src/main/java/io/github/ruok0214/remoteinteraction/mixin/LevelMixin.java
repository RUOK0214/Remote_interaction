package io.github.ruok0214.remoteinteraction.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.ruok0214.remoteinteraction.ReceiverData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Level.class)
public abstract class LevelMixin {
    @WrapMethod(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z")
    private boolean remoteInteraction$invalidate(BlockPos pos, BlockState state, int flags, int recursion, Operation<Boolean> original) {
        var level = (Level) (Object) this;
        boolean changedType = level instanceof ServerLevel && level.hasChunkAt(pos)
                && level.getBlockState(pos).getBlock() != state.getBlock();
        boolean changed = original.call(pos, state, flags, recursion);
        if (changed && changedType && level instanceof ServerLevel serverLevel) {
            ReceiverData.get(serverLevel.getServer()).targetRemoved(level.dimension().identifier(), pos);
        }
        return changed;
    }
}
