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
 * Shrinks end crystals and slows or stops their animation.
 *
 * <p>A crystal is drawn at twice the size of its own hitbox and rides an animation that carries it up
 * and down half a block. In a crystal fight that means the thing you are clicking is not where it is
 * drawn, and a wall of them hides everything behind. Shrinking the model towards the size of the box,
 * and holding it still, makes what you see line up with what you can hit.
 *
 * <p>The bob and the spin come from the same animation clock, so they are one setting rather than two -
 * there is no hook that separates them.
 *
 * <p>Only the model is touched. The hitbox, the damage, and everything the server knows are unchanged;
 * this makes the drawing honest rather than making the crystal easier to hit.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class CrystalView extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Size of the model relative to vanilla. Half is about the size of the hitbox.")
        .defaultValue(0.5)
        .min(0.05)
        .sliderRange(0.05, 1.5)
        .build()
    );

    private final Setting<Double> animation = sgGeneral.add(new DoubleSetting.Builder()
        .name("animation")
        .description("How much of the bob and spin to keep. Zero holds the crystal still.")
        .defaultValue(0)
        .min(0)
        .max(1)
        .sliderRange(0, 1)
        .build()
    );

    public CrystalView() {
        super(Categories.Render, "crystal-view", "Shrinks end crystals and slows their animation.");
    }

    public float scale(float original) {
        return isActive() ? (float) (original * scale.get()) : original;
    }

    /** The animation clock the model is driven from; both the bob and the spin follow it. */
    public float age(float original) {
        return isActive() ? (float) (original * animation.get()) : original;
    }
}
