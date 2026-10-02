/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.NoBob;
import meteordevelopment.meteorclient.systems.modules.render.NoHurtCam;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Kept apart from {@link GameRendererMixin} so a target that stops matching costs these two modules
 * alone rather than the rest of Meteor's rendering hooks.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererBobMixin {
    /** The lurch applied to the view while the hurt animation runs. */
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void skipHurtBob(CallbackInfo ci) {
        if (Modules.get().isActive(NoHurtCam.class)) ci.cancel();
    }

    /** The sway applied to the view in time with your steps. */
    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void skipViewBob(CallbackInfo ci) {
        if (Modules.get().isActive(NoBob.class)) ci.cancel();
    }
}
