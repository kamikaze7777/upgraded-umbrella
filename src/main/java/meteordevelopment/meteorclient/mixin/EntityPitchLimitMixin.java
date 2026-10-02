/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.NoPitchLimit;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Kept apart from {@link EntityMixin} so a target that stops matching costs this module alone.
 */
@Mixin(Entity.class)
public abstract class EntityPitchLimitMixin {
    /**
     * Turning clamps the pitch to a quarter turn either way, in two places - the live value and the one
     * kept for interpolation. Both go through the same call, so widening it here covers them together
     * and keeps them in step with each other.
     */
    @WrapOperation(method = "turn", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(FFF)F"))
    private float widenPitchLimit(float value, float min, float max, Operation<Float> original) {
        if ((Object) this != mc.player) return original.call(value, min, max);

        float limit = Modules.get().get(NoPitchLimit.class).limit();
        if (limit <= NoPitchLimit.VANILLA_LIMIT) return original.call(value, min, max);

        return Mth.clamp(value, -limit, limit);
    }
}
