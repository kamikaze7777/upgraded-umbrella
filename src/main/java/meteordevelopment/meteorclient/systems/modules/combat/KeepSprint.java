/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;

/**
 * Keeps the speed a knockback hit would otherwise take off you.
 *
 * <p>Landing a sprint hit costs you most of your momentum: the attack scales your horizontal movement
 * down and drops you out of sprint, so the target is knocked away while you stand still. Holding onto
 * that momentum is what lets you stay on someone you are chasing.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class KeepSprint extends Module {
    /** What the attack scales your horizontal movement by when left alone. */
    public static final double VANILLA_SLOWDOWN = 0.6;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> momentum = sgGeneral.add(new DoubleSetting.Builder()
        .name("momentum")
        .description("How much of your speed to keep through a knockback hit. 1 keeps all of it, 0.6 is vanilla.")
        .defaultValue(1)
        .min(0)
        .max(1)
        .sliderRange(0, 1)
        .build()
    );

    private final Setting<Double> hurtMomentum = sgGeneral.add(new DoubleSetting.Builder()
        .name("momentum-when-hurt")
        .description("The same, for hits you land while taking damage yourself.")
        .defaultValue(1)
        .min(0)
        .max(1)
        .sliderRange(0, 1)
        .build()
    );

    private final Setting<Integer> maxHurtTime = sgGeneral.add(new IntSetting.Builder()
        .name("hurt-time")
        .description("How long after taking damage still counts as hurt. Hurt time runs from 10 down to 0.")
        .defaultValue(10)
        .min(1)
        .max(10)
        .sliderRange(1, 10)
        .build()
    );

    private final Setting<Boolean> keepSprinting = sgGeneral.add(new BoolSetting.Builder()
        .name("keep-sprinting")
        .description("Also stay sprinting through the hit, instead of dropping out of it for a tick.")
        .defaultValue(true)
        .build()
    );

    public KeepSprint() {
        super(Categories.Combat, "keep-sprint", "Keeps the speed a knockback hit would otherwise take off you.");
    }

    /** The factor the attack should scale horizontal movement by. Vanilla when we are not running. */
    public double momentum() {
        if (!isActive() || mc.player == null) return VANILLA_SLOWDOWN;

        // Hurt time counts down from 10, so a larger setting covers more of the window after a hit.
        if (mc.player.hurtTime > 0 && mc.player.hurtTime <= maxHurtTime.get()) return hurtMomentum.get();

        return momentum.get();
    }

    public boolean keepSprinting() {
        return isActive() && keepSprinting.get();
    }
}
