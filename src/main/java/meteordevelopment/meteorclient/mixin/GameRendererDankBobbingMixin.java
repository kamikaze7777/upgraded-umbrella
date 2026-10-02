/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.DankBobbing;
import net.minecraft.client.renderer.GameRenderer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Kept apart from {@link GameRendererBobMixin} so a target that stops matching costs this module
 * alone.
 *
 * <p>The sway amount is read once and then used for all four of the translations and rotations that
 * make up the effect, so scaling that single read scales the whole thing consistently.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererDankBobbingMixin {
    @ModifyExpressionValue(method = "bobView", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/state/level/CameraEntityRenderState;bob:F", opcode = Opcodes.GETFIELD))
    private float exaggerateBob(float original) {
        return Modules.get().get(DankBobbing.class).scale(original);
    }
}
