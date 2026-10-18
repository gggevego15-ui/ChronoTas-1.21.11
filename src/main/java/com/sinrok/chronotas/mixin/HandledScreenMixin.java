package com.sinrok.chronotas.mixin;

import com.sinrok.chronotas.ChronoTASClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Records real container/crafting UI clicks so playback can repeat slot operations. */
@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {
    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void chronotas$recordContainerClick(double mouseX, double mouseY, int button,
                                                CallbackInfoReturnable<Boolean> cir) {
        ChronoTASClient.recordScreenClick(mouseX, mouseY, button);
    }
}
