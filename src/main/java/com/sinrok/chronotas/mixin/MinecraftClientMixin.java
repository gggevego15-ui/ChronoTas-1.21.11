package com.sinrok.chronotas.mixin;

import org.spongepowered.asm.mixin.Mixin;

/**
 * Intentionally contains no tick-cancelling hooks.
 * Cancelling MinecraftClient.tick freezes input handling and can make the TAS menu appear hung.
 * TAS pause/step is handled safely by ChronoTASClient instead.
 */
@Mixin(net.minecraft.client.MinecraftClient.class)
public abstract class MinecraftClientMixin {
}
