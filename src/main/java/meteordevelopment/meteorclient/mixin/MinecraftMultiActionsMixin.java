/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.MultiActions;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Kept apart from {@link MinecraftMixin} so a target that stops matching costs this module alone.
 *
 * <p>Both gates are a single call each, guarding the whole body of their method, so answering the call
 * differently is the entire change.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMultiActionsMixin {
    @ModifyExpressionValue(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;isDestroying()Z"))
    private boolean allowUseWhileBreaking(boolean original) {
        return original && !Modules.get().get(MultiActions.class).mayPlaceWhileBreaking();
    }

    @ModifyExpressionValue(method = "continueAttack", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"))
    private boolean allowBreakingWhileUsing(boolean original) {
        return original && !Modules.get().get(MultiActions.class).mayBreakWhileUsing();
    }
}
