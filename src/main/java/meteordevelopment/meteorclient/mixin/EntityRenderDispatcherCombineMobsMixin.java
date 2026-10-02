/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import meteordevelopment.meteorclient.mixininterface.IEntityRenderState;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.CombineMobs;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Kept apart from {@link EntityRenderDispatcherMixin} so a target that stops matching costs this
 * module alone rather than Meteor's fake player handling.
 *
 * <p>Refused at the dispatcher rather than inside a renderer, so the whole cost of the entity - model,
 * features, nametag - is skipped rather than only part of it. That is the entire point of the module.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherCombineMobsMixin {
    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private <S extends EntityRenderState> void skipCrammedDuplicates(S renderState, CameraRenderState camera, double x, double y, double z, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CallbackInfo ci) {
        Entity entity = ((IEntityRenderState) renderState).meteor$getEntity();
        if (entity == null) return;

        if (!Modules.get().get(CombineMobs.class).shouldRender(entity)) ci.cancel();
    }
}
