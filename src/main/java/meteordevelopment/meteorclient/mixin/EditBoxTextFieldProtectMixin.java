/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.TextFieldProtect;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Masks only the copy of the text that is about to be drawn.
 *
 * <p>The field is read three times while building the render state: once for the text itself, and
 * twice more for cursor and selection maths. Only the first is replaced, and the replacement is the
 * same length as the original, so the cursor still lands between the right two characters.
 */
@Mixin(EditBox.class)
public abstract class EditBoxTextFieldProtectMixin {
    @ModifyExpressionValue(method = "extractWidgetRenderState", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/components/EditBox;value:Ljava/lang/String;", ordinal = 0))
    private String maskDrawnText(String original) {
        return Modules.get().get(TextFieldProtect.class).mask(original);
    }
}
