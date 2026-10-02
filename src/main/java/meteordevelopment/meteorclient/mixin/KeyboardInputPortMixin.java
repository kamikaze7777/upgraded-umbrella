/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.Twerk;
import meteordevelopment.meteorclient.systems.modules.movement.Parkourl;
import meteordevelopment.meteorclient.systems.modules.movement.SnapTap;
import meteordevelopment.meteorclient.systems.modules.player.Eagle;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Kept apart from {@link KeyboardInputMixin} so a target that stops matching costs these two modules
 * alone rather than Meteor's own sneak and freecam handling.
 *
 * <p>Both modules rewrite the same record, so they share one injection and run in a fixed order:
 * SnapTap resolves the opposed pairs first, and Eagle then decides on sneak from the directions that
 * survived. Doing it the other way round would judge the edge by a direction that is about to be
 * thrown away.
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputPortMixin extends ClientInput {
    @Inject(method = "tick", at = @At("TAIL"))
    private void applyPortedInputModules(CallbackInfo ci) {
        SnapTap snapTap = Modules.get().get(SnapTap.class);
        Eagle eagle = Modules.get().get(Eagle.class);

        Input resolved = snapTap.isActive() ? snapTap.resolve(keyPresses) : keyPresses;

        if (eagle.shouldSneak(resolved) || Modules.get().get(Twerk.class).shouldSneak()) {
            resolved = new Input(
                resolved.forward(), resolved.backward(), resolved.left(), resolved.right(),
                resolved.jump(), true, resolved.sprint()
            );
        }

        // Last, because it reads the sneak the two above may just have set - a module holding sneak is
        // asking not to walk off the edge, which is the opposite of jumping over it.
        if (Modules.get().get(Parkourl.class).shouldJump(resolved)) {
            resolved = new Input(
                resolved.forward(), resolved.backward(), resolved.left(), resolved.right(),
                true, resolved.shift(), resolved.sprint()
            );
        }

        if (resolved == keyPresses) return;

        keyPresses = resolved;

        // The movement vector is derived from the keys a few lines above this injection, so it has to
        // be rebuilt or the rewritten keys would be reported without actually moving anything.
        moveVector = new Vec2(impulse(resolved.left(), resolved.right()), impulse(resolved.forward(), resolved.backward())).normalized();
    }

    private static float impulse(boolean positive, boolean negative) {
        if (positive == negative) return 0;
        return positive ? 1 : -1;
    }
}
