/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.Sprintl;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Kept apart from {@link LocalPlayerMixin} so a target that stops matching costs this module alone.
 *
 * <p>Both refusals live in {@code isSprintingPossible} as one call each, and neither is asked of the
 * server - it reads the sprint state out of the command packet the client sends and takes it as given.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerSprintlMixin {
    @ModifyExpressionValue(method = "isSprintingPossible", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isMobilityRestricted()Z"))
    private boolean ignoreBlindness(boolean original) {
        return original && !Modules.get().get(Sprintl.class).ignoreBlindness();
    }

    @ModifyExpressionValue(method = "isSprintingPossible", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;hasEnoughFoodToDoExhaustiveManoeuvres()Z"))
    private boolean ignoreHunger(boolean original) {
        return original || Modules.get().get(Sprintl.class).ignoreHunger();
    }
}
