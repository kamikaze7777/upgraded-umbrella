/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.NoSlowBreak;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Kept apart from {@link PlayerMixin} so a target that stops matching costs this module alone.
 *
 * <p>Each penalty is gated by a single call inside {@code getDestroySpeed}, so answering that call
 * differently skips the multiplier without touching the arithmetic around it.
 */
@Mixin(Player.class)
public abstract class PlayerNoSlowBreakMixin {
    @ModifyExpressionValue(method = "getDestroySpeed", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;hasEffect(Lnet/minecraft/core/Holder;)Z"))
    private boolean skipMiningFatigue(boolean original) {
        if ((Object) this != mc.player) return original;

        return original && !Modules.get().get(NoSlowBreak.class).ignoreMiningFatigue();
    }

    @ModifyExpressionValue(method = "getDestroySpeed", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean skipSubmerged(boolean original) {
        if ((Object) this != mc.player) return original;

        return original && !Modules.get().get(NoSlowBreak.class).ignoreUnderwater();
    }

    /** The one that matters: not being on the ground divides mining speed by five. */
    @ModifyExpressionValue(method = "getDestroySpeed", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;onGround()Z"))
    private boolean skipAirPenalty(boolean original) {
        if ((Object) this != mc.player) return original;

        return original || Modules.get().get(NoSlowBreak.class).ignoreInAir();
    }
}
