/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.world;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;

/**
 * Drops the penalties that make mining slow when you are not standing on the ground.
 *
 * <p>Three separate multipliers land on mining speed before the block ever sees it: mining fatigue
 * scales it to as little as a thousandth, being underwater applies the submerged attribute, and being
 * airborne divides it by five. Refusing each is a client-side change to how fast the local break
 * animation fills.
 *
 * <p>The server keeps its own clock on the block, so this only ever helps where the client's estimate
 * is what holds you up - it does not make a server accept a break it would otherwise refuse.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class NoSlowBreak extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> miningFatigue = sgGeneral.add(new BoolSetting.Builder()
        .name("mining-fatigue")
        .description("Ignore the mining fatigue effect.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> inAir = sgGeneral.add(new BoolSetting.Builder()
        .name("in-air")
        .description("Ignore the fivefold penalty for not being on the ground.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> underwater = sgGeneral.add(new BoolSetting.Builder()
        .name("underwater")
        .description("Ignore the penalty for having your head in water.")
        .defaultValue(false)
        .build()
    );

    public NoSlowBreak() {
        super(Categories.World, "no-slow-break", "Removes the mining speed penalties for fatigue, air, and water.");
    }

    public boolean ignoreMiningFatigue() {
        return isActive() && miningFatigue.get();
    }

    public boolean ignoreInAir() {
        return isActive() && inAir.get();
    }

    public boolean ignoreUnderwater() {
        return isActive() && underwater.get();
    }
}
