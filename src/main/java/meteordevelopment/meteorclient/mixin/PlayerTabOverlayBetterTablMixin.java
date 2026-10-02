/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.BetterTabl;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * Kept apart from {@link PlayerTabOverlayMixin} so a target that stops matching costs this module
 * alone rather than Meteor's own tab list changes.
 *
 * <p>The list is built once and then drawn, so replacing it on the way out reorders and filters
 * everything downstream - the rows, the columns, and the width - with one change.
 */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayBetterTablMixin {
    @ModifyReturnValue(method = "getPlayerInfos", at = @At("RETURN"))
    private List<PlayerInfo> sortAndFilter(List<PlayerInfo> original) {
        return Modules.get().get(BetterTabl.class).apply(original);
    }
}
