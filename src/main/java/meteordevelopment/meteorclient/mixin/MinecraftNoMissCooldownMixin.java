/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.NoMissCooldown;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Kept apart from {@link MinecraftMixin} so a target that stops matching costs this module alone.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftNoMissCooldownMixin {
    /**
     * A swing that connects with nothing parks attacking for ten ticks. Both places that start that
     * pause ask the game mode whether there should be one first, so answering no covers them together
     * and leaves the countdown itself alone.
     */
    @ModifyExpressionValue(method = "startAttack", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;hasMissTime()Z"))
    private boolean skipMissTime(boolean original) {
        if (Modules.get().get(NoMissCooldown.class).isActive()) return false;

        return original;
    }
}
