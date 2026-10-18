package com.sinrok.chronotas.mixin;

import com.sinrok.chronotas.ChronoTASClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Render-only smoothing for TAS playback. It changes the camera transform only;
 * player position, recorded inputs and tick timing remain untouched.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Unique private static boolean chronotas$hasPrevious;
    @Unique private static double chronotas$x, chronotas$y, chronotas$z;
    @Unique private static float chronotas$yaw, chronotas$pitch;

    @Inject(method = "update", at = @At("TAIL"))
    private void chronotas$smoothReplayCamera(CallbackInfo ci) {
        Camera camera = (Camera)(Object)this;
        if (!ChronoTASClient.playing) {
            chronotas$hasPrevious = false;
            return;
        }

        Vec3d target = camera.getPos();
        float targetYaw = camera.getYaw();
        float targetPitch = camera.getPitch();

        if (!chronotas$hasPrevious
                || target.squaredDistanceTo(new Vec3d(chronotas$x, chronotas$y, chronotas$z)) > 16.0) {
            chronotas$x = target.x;
            chronotas$y = target.y;
            chronotas$z = target.z;
            chronotas$yaw = targetYaw;
            chronotas$pitch = targetPitch;
            chronotas$hasPrevious = true;
            return;
        }

        // Exponential smoothing on render frames, independent of TAS simulation ticks.
        final double positionFactor = 0.38;
        final float rotationFactor = 0.42f;
        chronotas$x += (target.x - chronotas$x) * positionFactor;
        chronotas$y += (target.y - chronotas$y) * positionFactor;
        chronotas$z += (target.z - chronotas$z) * positionFactor;
        chronotas$yaw += MathHelper.wrapDegrees(targetYaw - chronotas$yaw) * rotationFactor;
        chronotas$pitch += (targetPitch - chronotas$pitch) * rotationFactor;

        camera.setPos(chronotas$x, chronotas$y, chronotas$z);
        camera.setRotation(chronotas$yaw, chronotas$pitch);
    }
}
