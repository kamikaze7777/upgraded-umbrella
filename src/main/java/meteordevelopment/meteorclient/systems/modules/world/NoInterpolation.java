/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.world;

import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;

/**
 * Shortens the smoothing the client applies to other entities' positions.
 *
 * <p>A position update is not applied at once. It is spread over three ticks, so what is drawn trails
 * where the server has actually put the entity - most of a block behind at a sprint. Shortening the
 * spread puts the drawn entity closer to the one the server will test an attack against, at the cost
 * of visible stepping when an entity turns.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class NoInterpolation extends Module {
    /** What vanilla spreads a position update over. */
    public static final int DEFAULT_STEPS = 3;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> reduceBy = sgGeneral.add(new IntSetting.Builder()
        .name("reduce-by")
        .description("Ticks to take off the smoothing. At three there is none left and entities snap.")
        .defaultValue(3)
        .min(1)
        .max(DEFAULT_STEPS)
        .sliderRange(1, DEFAULT_STEPS)
        .build()
    );

    public NoInterpolation() {
        super(Categories.World, "no-interpolation", "Shortens how long entity movement is smoothed over.");
    }

    /** The step count to use in place of whatever the handler was built with. */
    public int steps(int original) {
        return isActive() ? Math.max(0, original - reduceBy.get()) : original;
    }
}
