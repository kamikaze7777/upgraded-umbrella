/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.EntityControll;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The single question the client asks before it will begin charging a jump on a mount. The server is
 * never asked it - it only sees the charge that comes out - so answering it differently is the whole
 * of the module.
 */
@Mixin(AbstractHorse.class)
public abstract class AbstractHorseEntityControlMixin {
    @ModifyReturnValue(method = "canJump", at = @At("RETURN"))
    private boolean allowJump(boolean original) {
        return Modules.get().get(EntityControll.class).canJump(original);
    }
}
