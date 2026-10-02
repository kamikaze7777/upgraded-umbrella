/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.NoSwing;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Kept apart from Meteor's entity mixins so a target that stops matching costs this module alone.
 *
 * <p>Refusing the animation here leaves the swing packet alone - it is sent by the caller after this
 * returns, so hiding the swing locally does not hide it from anyone else.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityNoSwingMixin {
    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At("HEAD"), cancellable = true)
    private void skipSwingAnimation(InteractionHand hand, boolean sendToSwingingEntity, CallbackInfo ci) {
        if ((Object) this != mc.player) return;
        if (!Modules.get().get(NoSwing.class).hideFromSelf()) return;

        ci.cancel();
    }
}
