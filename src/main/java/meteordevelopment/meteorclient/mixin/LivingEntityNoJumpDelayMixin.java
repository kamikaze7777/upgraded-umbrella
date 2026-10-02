/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.NoJumpDelay;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Kept apart from the shared entity mixins so a target that stops matching costs this module alone.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityNoJumpDelayMixin {
    @Shadow
    private int noJumpDelay;

    /**
     * Landing arms a countdown that a jump refuses to start until it reaches zero. Clearing it before
     * the tick reads it leaves the jump free, and is safer than rewriting the constant that arms it -
     * that value is not unique within this method.
     */
    @Inject(method = "aiStep", at = @At("HEAD"))
    private void clearJumpDelay(CallbackInfo ci) {
        if ((Object) this != mc.player) return;
        if (!Modules.get().isActive(NoJumpDelay.class)) return;

        noJumpDelay = 0;
    }
}
