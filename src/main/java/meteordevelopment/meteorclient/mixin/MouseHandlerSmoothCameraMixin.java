/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.SmoothCamera;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * The single call at the end of the mouse handler that hands a movement to the player. Everything
 * before it - sensitivity, the vanilla smoothing option, the scoping slowdown - has already been
 * applied, so this scales the finished movement rather than fighting with any of it.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerSmoothCameraMixin {
    @ModifyArg(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), index = 0)
    private double smoothYaw(double yaw) {
        SmoothCamera module = Modules.get().get(SmoothCamera.class);

        return module.isActive() ? module.yaw(yaw) : yaw;
    }

    @ModifyArg(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), index = 1)
    private double smoothPitch(double pitch) {
        SmoothCamera module = Modules.get().get(SmoothCamera.class);

        return module.isActive() ? module.pitch(pitch) : pitch;
    }
}
