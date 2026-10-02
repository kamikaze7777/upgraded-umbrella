/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.AbortBreaking;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Kept apart from {@link MultiPlayerGameModeMixin} so a target that stops matching costs this module
 * alone. That one already cancels the same method, but only while Meteor's own block helpers are
 * mid-break; this is the user-facing switch and holds regardless.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeAbortBreakingMixin {
    @Inject(method = "stopDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void keepBreaking(CallbackInfo ci) {
        if (Modules.get().isActive(AbortBreaking.class)) ci.cancel();
    }
}
