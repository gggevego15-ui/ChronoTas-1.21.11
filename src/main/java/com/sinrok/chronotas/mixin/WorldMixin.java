package com.sinrok.chronotas.mixin;

import com.sinrok.chronotas.ChronoTASWorldState;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public abstract class WorldMixin {
    @Inject(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Z", at = @At("HEAD"))
    private void chronotas$recordBlockChange(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<Boolean> cir) {
        ChronoTASWorldState.recordBlockBeforeChange((World)(Object)this, pos);
    }
    @Inject(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Z", at = @At("RETURN"))
    private void chronotas$captureBlockResult(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<Boolean> cir) {
        ChronoTASWorldState.recordBlockAfterChange((World)(Object)this, pos, cir.getReturnValueZ());
    }

}
