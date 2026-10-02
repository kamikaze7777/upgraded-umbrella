/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.render.GetFovEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

/**
 * Takes the field of view off whatever the game is doing with it.
 *
 * <p>Sprinting, drinking, flying, and being slowed all pull the view angle around, and the pull is
 * both a distraction and a tell about your state. Pinning it removes both. The softer option keeps
 * the change but scales it down, for when the movement itself is a useful cue.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class NoFov extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> fixed = sgGeneral.add(new BoolSetting.Builder()
        .name("fixed")
        .description("Hold one angle no matter what, instead of damping the changes.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> fov = sgGeneral.add(new IntSetting.Builder()
        .name("fov")
        .description("The angle to hold, in degrees.")
        .defaultValue(90)
        .min(1)
        .max(179)
        .sliderRange(30, 150)
        .visible(fixed::get)
        .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("How much of the game's own change to keep. Zero is as good as fixed.")
        .defaultValue(0.25)
        .min(0)
        .sliderRange(0, 1)
        .visible(() -> !fixed.get())
        .build()
    );

    /** The angle the game settles on when nothing is modifying it, which is what a damped value bends back towards. */
    private float base = 70;

    public NoFov() {
        super(Categories.Render, "no-fov", "Stops sprinting and item use pulling your field of view around.");
    }

    /**
     * Runs after Meteor's own zoom, so a zoom still works - this pins the effects the game applies,
     * not the angle the user asked for.
     */
    @EventHandler
    private void onGetFov(GetFovEvent event) {
        if (fixed.get()) {
            event.fov = fov.get();
            return;
        }

        // Track the resting angle from whatever the game hands over, so a changed FOV slider is
        // followed rather than fought.
        if (mc.options != null) base = mc.options.fov().get();

        event.fov = (float) (base + (event.fov - base) * scale.get());
    }
}
