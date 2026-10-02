/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.CrystalView;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * The two numbers a crystal is drawn from: the scale pushed onto the matrix before the model is
 * submitted, and the animation clock the model reads its bob and spin out of.
 */
@Mixin(EndCrystalRenderer.class)
public abstract class EndCrystalRendererCrystalViewMixin {
    /**
     * Only the first of the three scale arguments is modified - they are always the same value here, and
     * modifying all three would triple-apply the setting.
     */
    @ModifyArg(method = "submit", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"), index = 0)
    private float scaleX(float original) {
        return Modules.get().get(CrystalView.class).scale(original);
    }

    @ModifyArg(method = "submit", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"), index = 1)
    private float scaleY(float original) {
        return Modules.get().get(CrystalView.class).scale(original);
    }

    @ModifyArg(method = "submit", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"), index = 2)
    private float scaleZ(float original) {
        return Modules.get().get(CrystalView.class).scale(original);
    }

    /**
     * Slowed where the state is built rather than where it is read, so every use of it - the model's own
     * spin as well as the beam's height - stays consistent with a single change.
     */
    @ModifyExpressionValue(method = "extractRenderState(Lnet/minecraft/world/entity/boss/enderdragon/EndCrystal;Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;F)V", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/boss/enderdragon/EndCrystal;time:I", opcode = Opcodes.GETFIELD))
    private int slowAnimation(int original) {
        return (int) Modules.get().get(CrystalView.class).age(original);
    }
}
