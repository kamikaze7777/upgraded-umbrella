/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;

/**
 * Lets a mount jump that the client has decided cannot.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code entity-control}, kept alongside
 * it under the {@code l} suffix the rest of this port uses for name collisions. Meteor's already
 * covers the saddle half - {@code MobMixin} answers {@code isSaddled} for it - so what is here is the
 * other half of LiquidBounce's module, which Meteor's does not have.
 *
 * <p>A horse decides for itself whether it is in a state to jump, and the client asks it before it
 * will begin charging one. That question is never asked of the server: it only ever sees the charge
 * that comes out. So answering it differently lets a mount that has refused - untamed, or a variety
 * with no jump at all - still be jumped.
 *
 * <p>How high it goes is still the mount's own jump strength attribute, which the server holds. This
 * removes the refusal, not the ceiling.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class EntityControll extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> untamed = sgGeneral.add(new BoolSetting.Builder()
        .name("untamed")
        .description("Let a mount that has not been tamed jump as well.")
        .defaultValue(true)
        .build()
    );

    public EntityControll() {
        super(Categories.Movement, "entity-controll", "Lets a mount jump that the client says cannot.");
    }

    /** The answer to a mount's own "can I jump" question. */
    public boolean canJump(boolean original) {
        if (!isActive()) return original;

        return original || untamed.get();
    }
}
