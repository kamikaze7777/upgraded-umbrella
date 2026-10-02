/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.AntiReducedDebugInfo;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Kept apart from {@link LocalPlayerMixin} so a target that stops matching costs this module alone.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerAntiReducedDebugInfoMixin {
    /**
     * Answered as false on the way in, so the rebuild that follows draws the full list. Refusing the
     * call outright instead would leave the debug list stale when the flag arrives.
     */
    @ModifyVariable(method = "setReducedDebugInfo", at = @At("HEAD"), argsOnly = true)
    private boolean keepFullDebugInfo(boolean reduced) {
        return reduced && !Modules.get().isActive(AntiReducedDebugInfo.class);
    }
}
