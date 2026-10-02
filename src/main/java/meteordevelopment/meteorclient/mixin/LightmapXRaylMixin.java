/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.XRayl;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Kept apart from {@link LightmapMixin} so a target that stops matching costs this module alone.
 *
 * <p>Clearing the lightmap to white is what full brightness means - every block is then drawn at its
 * own colour rather than at whatever light reached it, which underground is nothing.
 */
@Mixin(Lightmap.class)
public abstract class LightmapXRaylMixin {
    @Shadow
    private GpuTexture texture;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void fullBrightForXray(LightmapRenderState renderState, CallbackInfo ci) {
        if (!Modules.get().get(XRayl.class).fullBright()) return;

        RenderSystem.getDevice().createCommandEncoder().clearColorTexture(texture, new Vector4f(1));
        ci.cancel();
    }
}
