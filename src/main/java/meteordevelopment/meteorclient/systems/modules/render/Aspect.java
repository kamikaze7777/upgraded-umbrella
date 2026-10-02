/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;

/**
 * Stretches or squeezes the world's aspect ratio without touching the interface.
 *
 * <p>The projection matrix is built from the window's own ratio; overriding that number widens what
 * fits on screen at the cost of the shape of everything in it. The interface is drawn separately and
 * is left alone, so the HUD stays square while the world stretches.
 *
 * <p>It changes what you can see, not what you can reach. Aim still comes from the camera direction,
 * so something visible only because of the stretch is at exactly the angle it always was.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class Aspect extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> ratio = sgGeneral.add(new DoubleSetting.Builder()
        .name("ratio")
        .description("Multiplier on the window's own aspect ratio. Above one is wider.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 3)
        .build()
    );

    public Aspect() {
        super(Categories.Render, "aspect", "Stretches the world's aspect ratio without touching the HUD.");
    }

    /** The ratio to build the projection from, given the one the window reports. */
    public float ratio(float original) {
        return isActive() ? (float) (original * ratio.get()) : original;
    }
}
