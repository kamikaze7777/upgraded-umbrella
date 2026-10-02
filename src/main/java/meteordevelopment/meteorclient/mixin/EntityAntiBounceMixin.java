/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.AntiBounce;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Kept apart from {@link EntityMixin} so a target that stops matching costs this module alone.
 */
@Mixin(Entity.class)
public abstract class EntityAntiBounceMixin {
    /**
     * The one gate on picking up a block's restitution. Vanilla answers it with "is sneaking", and the
     * bounce path reads it twice - once for the walls and once for the floor - so answering it here
     * covers both without touching the restitution maths.
     */
    @Inject(method = "isSuppressingBounce", at = @At("HEAD"), cancellable = true)
    private void suppressBounce(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this != mc.player) return;
        if (!Modules.get().isActive(AntiBounce.class)) return;

        cir.setReturnValue(true);
    }
}
