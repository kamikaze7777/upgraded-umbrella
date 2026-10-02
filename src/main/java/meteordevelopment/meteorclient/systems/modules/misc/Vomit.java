/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Throws your inventory out one slot at a time, in no particular order.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class Vomit extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks between throws. Too low and the server's own rate limit will cut it off.")
        .defaultValue(2)
        .min(1)
        .sliderRange(1, 20)
        .build()
    );

    private final List<Integer> slots = new ArrayList<>();
    private int timer;

    public Vomit() {
        super(Categories.Misc, "vomit", "Throws your inventory out one slot at a time.");
    }

    @Override
    public void onActivate() {
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || ++timer < delay.get()) return;
        timer = 0;

        // Rebuilt each time rather than kept, because a throw invalidates the previous list anyway and
        // the server may have moved things underneath us.
        slots.clear();
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            if (!mc.player.getInventory().getItem(i).isEmpty()) slots.add(i);
        }

        if (slots.isEmpty()) {
            toggle();
            return;
        }

        InvUtils.drop().slot(slots.get((int) (Math.random() * slots.size())));
    }
}
