/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.NoPose;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Standalone so a target that stops matching costs this module alone.
 *
 * <p>{@code getDimensions} is final on LivingEntity, so the box has to be swapped one level down at
 * the per-pose lookup that feeds it.
 */
@Mixin(Avatar.class)
public abstract class AvatarNoPoseMixin {
    @ModifyReturnValue(method = "getDefaultDimensions", at = @At("RETURN"))
    private EntityDimensions swapCrouchBox(EntityDimensions original, Pose pose) {
        if ((Object) this != mc.player || pose != Pose.CROUCHING) return original;

        EntityDimensions replacement = Modules.get().get(NoPose.class).crouchingDimensions();

        return replacement != null ? replacement : original;
    }
}
