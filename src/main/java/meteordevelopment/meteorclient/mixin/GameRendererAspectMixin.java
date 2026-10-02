/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Aspect;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Kept apart from Meteor's own renderer mixins so a target that stops matching costs this module
 * alone.
 *
 * <p>The world's projection is uploaded exactly once per frame, from a copy the renderer has just
 * made for itself. Scaling that copy on its way to the buffer therefore stretches the world and
 * nothing else - the interface has its own projection and is uploaded separately.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererAspectMixin {
    @ModifyArg(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"))
    private Matrix4f stretch(Matrix4f projection) {
        Aspect aspect = Modules.get().get(Aspect.class);
        if (!aspect.isActive()) return projection;

        float ratio = aspect.ratio(1);
        if (ratio == 1) return projection;

        // Applied on the left, so it scales the finished clip-space x rather than being folded into the
        // camera's own transform - a wider ratio has to shrink x to fit more of the world across it.
        return projection.scaleLocal(1 / ratio, 1, 1);
    }
}
