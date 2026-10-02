/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.phys.Vec3;

/**
 * Hops in place a set number of times and then switches itself off.
 *
 * <p>Checks that count suspicion against a player usually bleed it back off while the player is doing
 * something ordinary. A short run of plain, obviously legitimate hops gives them something ordinary to
 * watch, which is cheaper than waiting it out standing still.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class ResetVL extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> times = sgGeneral.add(new IntSetting.Builder()
        .name("times")
        .description("How many hops to make before switching off.")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 50)
        .build()
    );

    private int done;
    private boolean rising;

    public ResetVL() {
        super(Categories.Player, "reset-vl", "Hops in place a set number of times, then turns itself off.");
    }

    @Override
    public void onActivate() {
        done = 0;
        rising = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (rising) {
            // Come back down under our own steam rather than waiting for gravity, so a hop is two
            // ticks and the whole run stays short.
            mc.player.setDeltaMovement(new Vec3(0, -0.1, 0));
            rising = false;

            if (++done >= times.get()) {
                info("Done, %d hops.", done);
                toggle();
            }

            return;
        }

        if (!mc.player.onGround()) return;

        mc.player.setDeltaMovement(new Vec3(0, 0.1, 0));
        rising = true;
    }

    @Override
    public String getInfoString() {
        return done + "/" + times.get();
    }
}
