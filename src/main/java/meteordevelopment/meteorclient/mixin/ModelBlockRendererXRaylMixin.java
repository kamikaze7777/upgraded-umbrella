/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.XRayl;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Kept apart from {@link ModelBlockRendererMixin} so a target that stops matching costs this module
 * alone rather than Meteor's own xray.
 *
 * <p>This one call decides whether a face is worth drawing, and every block goes through it, so
 * answering it is the whole of hiding the world.
 */
@Mixin(ModelBlockRenderer.class)
public abstract class ModelBlockRendererXRaylMixin {
    @ModifyReturnValue(method = "shouldRenderFace", at = @At("RETURN"))
    private static boolean shouldRenderFace(boolean original, BlockAndTintGetter level, BlockState state, Direction direction, BlockPos neighborPos) {
        XRayl xray = Modules.get().get(XRayl.class);
        if (!xray.isActive()) return original;

        // The position handed in is the neighbour the face looks towards, so the block being drawn is
        // one step back the other way.
        return xray.shouldRender(state, neighborPos.relative(direction.getOpposite()));
    }
}
