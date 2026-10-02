/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.NoPose;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Kept apart from {@link EntityMixin} so a target that stops matching costs this module alone.
 */
@Mixin(Entity.class)
public abstract class EntityNoPoseMixin {
    /**
     * Refused before it is stored, so nothing downstream - the box, the eye height, or the animation -
     * ever sees the swimming pose. The pose is recomputed from scratch every tick, so a refusal here
     * does not stick once the module is off.
     */
    @Inject(method = "setPose", at = @At("HEAD"), cancellable = true)
    private void refuseSwimPose(Pose pose, CallbackInfo ci) {
        if ((Object) this != mc.player || pose != Pose.SWIMMING) return;
        if (!Modules.get().get(NoPose.class).shouldCancelSwim()) return;

        ci.cancel();
    }
}
