package com.sinrok.chronotas.mixin;

import com.sinrok.chronotas.ChronoTASClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.client.MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Shadow private Window window;
    private boolean chronotas$f8WasDown;
    private boolean chronotas$f9WasDown;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void chronotas$pauseAndStep(CallbackInfo ci) {
        if (window == null) return;
        boolean f8 = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_F8);
        boolean f9 = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_F9);
        if (f8 && !chronotas$f8WasDown) {
            ChronoTASClient.paused = !ChronoTASClient.paused;
            ChronoTASClient.stepTicks = 0;
        }
        if (f9 && !chronotas$f9WasDown && ChronoTASClient.paused) ChronoTASClient.stepTicks++;
        chronotas$f8WasDown = f8;
        chronotas$f9WasDown = f9;
        if (ChronoTASClient.paused) {
            if (ChronoTASClient.stepTicks > 0) ChronoTASClient.stepTicks--;
            else ci.cancel();
        }
    }
}
