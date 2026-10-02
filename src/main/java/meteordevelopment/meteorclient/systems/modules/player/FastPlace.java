/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;

/**
 * Removes the pause the client puts between right clicks.
 *
 * <p>Every use of an item parks the next one for four ticks, which is what caps how fast blocks go down
 * or projectiles go out. The delay is client side only - the server never asked for it - so shortening
 * it costs nothing but lets you place and throw as fast as you can click.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class FastPlace extends Module {
    /** Ticks the client parks the next use for when left alone. */
    public static final int VANILLA_DELAY = 4;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks to wait between uses. 0 is as fast as the client can go, 4 is vanilla.")
        .defaultValue(0)
        .min(0)
        .max(VANILLA_DELAY)
        .sliderRange(0, VANILLA_DELAY)
        .build()
    );

    public FastPlace() {
        super(Categories.Player, "fast-place", "Removes the four tick pause the client puts between right clicks.");
    }

    public int delay() {
        return delay.get();
    }
}
