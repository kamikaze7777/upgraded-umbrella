/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;

/**
 * Holds and releases sneak on a fixed cycle.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class Twerk extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks spent in each half of the cycle.")
        .defaultValue(2)
        .min(1)
        .sliderRange(1, 20)
        .build()
    );

    public Twerk() {
        super(Categories.Misc, "twerk", "Sneaks and unsneaks on a cycle.");
    }

    /** Whether sneak should be held this tick, driven off the player's own tick counter. */
    public boolean shouldSneak() {
        if (!isActive() || mc.player == null) return false;

        int period = delay.get() * 2;

        return mc.player.tickCount % period < delay.get();
    }
}
