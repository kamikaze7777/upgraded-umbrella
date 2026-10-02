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
 * Spreads each mouse movement over the following frames instead of applying all of it at once.
 *
 * <p>Rotation is what a server sees most of, and a human turning a mouse produces a curve while a
 * snap produces a step. Holding back part of every movement and paying it out over the next few frames
 * turns steps into curves, at the cost of the view arriving slightly after the hand.
 *
 * <p>The debt is always paid in full - nothing is dropped - so the total rotation still matches what
 * the mouse actually did, and the aim ends up exactly where it was pointed.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class SmoothCamera extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> smoothness = sgGeneral.add(new DoubleSetting.Builder()
        .name("smoothness")
        .description("How much of each movement to hold back. Higher is smoother and further behind.")
        .defaultValue(0.5)
        .min(0)
        .max(0.95)
        .sliderRange(0, 0.95)
        .build()
    );

    /** Movement held back from earlier frames, waiting to be paid out. */
    private double owedYaw, owedPitch;

    public SmoothCamera() {
        super(Categories.Render, "smooth-camera", "Spreads mouse movement over several frames instead of applying it at once.");
    }

    @Override
    public void onActivate() {
        owedYaw = owedPitch = 0;
    }

    /** Yaw to apply this frame, given what the mouse actually moved. */
    public double yaw(double raw) {
        owedYaw += raw;

        double apply = owedYaw * (1 - smoothness.get());
        owedYaw -= apply;

        return apply;
    }

    /** Pitch to apply this frame, given what the mouse actually moved. */
    public double pitch(double raw) {
        owedPitch += raw;

        double apply = owedPitch * (1 - smoothness.get());
        owedPitch -= apply;

        return apply;
    }
}
