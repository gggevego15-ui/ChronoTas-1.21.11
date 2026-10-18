package com.sinrok.chronotas.mixin;

import com.sinrok.chronotas.ChronoTASClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow private Vec3d pos;
    @Shadow protected abstract void setPos(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yaw, float pitch);

    @Unique private boolean chronotas$hasPrevious;
    @Unique private double chronotas$x, chronotas$y, chronotas$z;
    @Unique private float chronotas$yaw, chronotas$pitch;

    @Inject(method = "update", at = @At("TAIL"))
    private void chronotas$smoothReplayCamera(CallbackInfo ci) {
        if (!ChronoTASClient.playing || pos == null) {
            chronotas$hasPrevious = false;
            return;
        }

        Vec3d target = pos;
        Camera camera = (Camera)(Object)this;
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

        // Smooth only the rendered camera; never modify the TAS player transform.
        final double positionFactor = 0.38;
        final float rotationFactor = 0.42f;
        chronotas$x += (target.x - chronotas$x) * positionFactor;
        chronotas$y += (target.y - chronotas$y) * positionFactor;
        chronotas$z += (target.z - chronotas$z) * positionFactor;
        chronotas$yaw += MathHelper.wrapDegrees(targetYaw - chronotas$yaw) * rotationFactor;
        chronotas$pitch += (targetPitch - chronotas$pitch) * rotationFactor;
        setPos(chronotas$x, chronotas$y, chronotas$z);
        setRotation(chronotas$yaw, chronotas$pitch);
    }
}
