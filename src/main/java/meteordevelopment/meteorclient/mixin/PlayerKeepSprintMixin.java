/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.KeepSprint;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Kept apart from {@link PlayerMixin} on purpose: both injections here are aimed at calls inside a
 * single vanilla method, and a target that stops matching should cost this module alone rather than
 * take the rest of Meteor's player hooks down with it.
 */
@Mixin(Player.class)
public abstract class PlayerKeepSprintMixin {
    /**
     * The attack scales the attacker's horizontal movement down to a fraction of what it was. That is
     * the speed a sprint hit costs you.
     */
    @ModifyArgs(method = "causeExtraKnockback", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;multiply(DDD)Lnet/minecraft/world/phys/Vec3;"))
    private void keepMomentum(Args args) {
        if ((Object) this != mc.player) return;

        double momentum = Modules.get().get(KeepSprint.class).momentum();

        // Only the horizontal factors; the vertical one is already 1 and is not ours to touch.
        args.set(0, momentum);
        args.set(2, momentum);
    }

    /** The attack also drops the attacker out of sprint, on top of the slowdown above. */
    @WrapWithCondition(method = "causeExtraKnockback", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setSprinting(Z)V"))
    private boolean keepSprinting(Player self, boolean sprinting) {
        if ((Object) this != mc.player) return true;

        return !Modules.get().get(KeepSprint.class).keepSprinting();
    }
}
