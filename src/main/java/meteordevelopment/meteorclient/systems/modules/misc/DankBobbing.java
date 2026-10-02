/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;

/**
 * Exaggerates the camera sway you get from walking.
 *
 * <p>Purely cosmetic, and purely local - the sway is applied to the view matrix after everything that
 * decides where you are, so nothing about it reaches the server.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class DankBobbing extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> multiplier = sgGeneral.add(new DoubleSetting.Builder()
        .name("multiplier")
        .description("How much to exaggerate the sway by.")
        .defaultValue(5)
        .min(0)
        .sliderRange(0, 50)
        .build()
    );

    public DankBobbing() {
        super(Categories.Misc, "dank-bobbing", "Exaggerates the camera sway from walking.");
    }

    /** The sway amount to use in place of the one the game computed. */
    public float scale(float original) {
        return isActive() ? (float) (original * multiplier.get()) : original;
    }
}
