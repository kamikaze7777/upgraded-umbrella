/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.player.PlayerModelPart;

import java.util.EnumSet;
import java.util.Set;

/**
 * Flickers the optional layers of your skin.
 *
 * <p>Which layers you show is a client setting that is sent to the server and passed on to everyone
 * else, so toggling it is visible to other players - unlike most render options, this one is not
 * only local.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class SkinDerp extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks between flickers.")
        .defaultValue(2)
        .min(1)
        .sliderRange(1, 20)
        .build()
    );

    private final Setting<Boolean> together = sgGeneral.add(new BoolSetting.Builder()
        .name("together")
        .description("Flip every part at once instead of choosing at random.")
        .defaultValue(false)
        .build()
    );

    /** What was showing before, so switching off puts the skin back rather than leaving it stripped. */
    private final Set<PlayerModelPart> original = EnumSet.noneOf(PlayerModelPart.class);
    private int timer;

    public SkinDerp() {
        super(Categories.Misc, "skin-derp", "Flickers the optional layers of your skin.");
    }

    @Override
    public void onActivate() {
        original.clear();
        timer = 0;

        for (PlayerModelPart part : PlayerModelPart.values()) {
            if (mc.options.isModelPartEnabled(part)) original.add(part);
        }
    }

    @Override
    public void onDeactivate() {
        for (PlayerModelPart part : PlayerModelPart.values()) {
            mc.options.setModelPart(part, original.contains(part));
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (++timer < delay.get()) return;
        timer = 0;

        for (PlayerModelPart part : PlayerModelPart.values()) {
            if (part == PlayerModelPart.CAPE) continue;

            mc.options.setModelPart(part, together.get() ? !mc.options.isModelPartEnabled(part) : Math.random() < 0.5);
        }
    }
}
