/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.ExtendedFirework;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Kept apart from {@link FireworkRocketEntityMixin} so a target that stops matching costs this module
 * alone rather than Meteor's own elytra boost handling.
 *
 * <p>The rocket writes the glider's new movement exactly once per tick, and that write is the only
 * place the boost exists as a value - which makes it the one place to scale it.
 */
@Mixin(FireworkRocketEntity.class)
public abstract class FireworkRocketExtendedFireworkMixin {
    @Shadow
    private @Nullable LivingEntity attachedToEntity;

    @ModifyArg(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"))
    private Vec3 extendBoost(Vec3 boosted) {
        if (attachedToEntity != mc.player) return boosted;

        ExtendedFirework module = Modules.get().get(ExtendedFirework.class);
        if (!module.isActive()) return boosted;

        // The write has not happened yet, so what the player still holds is the movement from before the
        // boost. The difference between the two is the boost itself, which is the part to scale.
        Vec3 boost = boosted.subtract(attachedToEntity.getDeltaMovement());
        Vector3d multiplier = module.multiplier();

        return boosted.add(boost.x * multiplier.x, boost.y * multiplier.y, boost.z * multiplier.z);
    }
}
