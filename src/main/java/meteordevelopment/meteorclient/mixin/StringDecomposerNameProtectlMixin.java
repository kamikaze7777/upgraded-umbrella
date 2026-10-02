/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.NameProtectl;
import net.minecraft.util.StringDecomposer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Kept apart from {@link StringDecomposerMixin} so a target that stops matching costs this module
 * alone rather than Meteor's own name protection.
 *
 * <p>Every piece of text the game draws passes through here, which is why one hook covers chat,
 * nametags, the player list and scoreboards at once. Both mixins modify the same argument; whichever
 * runs first hands its result to the other, and since they hide different names the order does not
 * matter.
 */
@Mixin(StringDecomposer.class)
public abstract class StringDecomposerNameProtectlMixin {
    @ModifyArg(at = @At(value = "INVOKE",
        target = "Lnet/minecraft/util/StringDecomposer;iterateFormatted(Ljava/lang/String;ILnet/minecraft/network/chat/Style;Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z",
        ordinal = 0),
        method = {"iterateFormatted(Ljava/lang/String;ILnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z"},
        index = 0)
    private static String hideOtherNames(String text) {
        // Runs before the module system exists during startup, which is why this is guarded rather
        // than assumed.
        if (Modules.get() == null) return text;

        return Modules.get().get(NameProtectl.class).replaceNames(text);
    }
}
