/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.NoInterpolation;
import net.minecraft.world.entity.InterpolationHandler;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Answers the step count differently rather than writing to the field, so nothing is left changed
 * once the module is switched off - each entity's handler keeps whatever it was built with.
 */
@Mixin(InterpolationHandler.class)
public abstract class InterpolationHandlerMixin {
    @ModifyExpressionValue(method = "interpolateTo", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/InterpolationHandler;interpolationSteps:I", opcode = Opcodes.GETFIELD))
    private int shortenSmoothing(int original) {
        return Modules.get().get(NoInterpolation.class).steps(original);
    }
}
