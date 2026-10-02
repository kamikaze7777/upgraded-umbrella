/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;

/**
 * Lets your pitch go past straight up and straight down.
 *
 * <p>Looking is clamped to a quarter turn either way, which is the whole range a person needs but not
 * the whole range the protocol carries. Widening it lets rotations point somewhere the vanilla client
 * cannot reach, which is what modules that aim behind you are asking for.
 *
 * <p>The clamp is client side. Nothing rejects a pitch outside it, so raising the limit costs nothing
 * beyond looking strange.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class NoPitchLimit extends Module {
    /** How far vanilla lets the pitch travel from level, in degrees. */
    public static final float VANILLA_LIMIT = 90;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> limit = sgGeneral.add(new DoubleSetting.Builder()
        .name("limit")
        .description("How far from level the pitch may go. 90 is vanilla, 180 is a full turn either way.")
        .defaultValue(180)
        .min(VANILLA_LIMIT)
        .max(180)
        .sliderRange(VANILLA_LIMIT, 180)
        .build()
    );

    public NoPitchLimit() {
        super(Categories.Player, "no-pitch-limit", "Lets your pitch travel past straight up and straight down.");
    }

    /** The bound to apply instead of vanilla's, kept at vanilla while we are not running. */
    public float limit() {
        return isActive() ? limit.get().floatValue() : VANILLA_LIMIT;
    }
}
