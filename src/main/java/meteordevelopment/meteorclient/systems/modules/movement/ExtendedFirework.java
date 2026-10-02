/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Vector3dSetting;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import org.joml.Vector3d;

/**
 * Scales the push a firework gives you while gliding.
 *
 * <p>A firework accelerates you towards where you are looking by a fixed amount each tick, and the
 * client is what applies it - the server only knows the rocket exists and where you end up. Scaling
 * the three axes separately is what makes this useful rather than just fast: most of the gain is worth
 * having upwards, where the extra height converts into distance, and very little of it sideways, where
 * it only makes the flight harder to steer.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class ExtendedFirework extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Vector3d> multiplier = sgGeneral.add(new Vector3dSetting.Builder()
        .name("multiplier")
        .description("Extra push per axis, on top of the normal one.")
        .defaultValue(0.1, 1.65, 0.5)
        .build()
    );

    public ExtendedFirework() {
        super(Categories.Movement, "extended-firework", "Scales the boost a firework gives you while gliding.");
    }

    public Vector3d multiplier() {
        return multiplier.get();
    }
}
