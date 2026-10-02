/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.FastPlace;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Kept apart from {@link MinecraftMixin} so a target that stops matching costs this module alone.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftFastPlaceMixin {
    @Shadow
    private int rightClickDelay;

    /**
     * Using an item parks the next use for a few ticks. Overwriting the delay on the way out covers
     * however it was set, and taking the smaller of the two means this only ever shortens the wait.
     */
    @Inject(method = "startUseItem", at = @At("RETURN"))
    private void shortenUseDelay(CallbackInfo ci) {
        FastPlace fastPlace = Modules.get().get(FastPlace.class);
        if (!fastPlace.isActive()) return;

        rightClickDelay = Math.min(rightClickDelay, fastPlace.delay());
    }
}
