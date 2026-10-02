/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.InteractionHand;

import java.util.ArrayList;
import java.util.List;

/**
 * Does something a person might do, at intervals a person might do it, to avoid an idle kick.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code anti-afk}, kept alongside it
 * under the {@code l} suffix the rest of this port uses for name collisions. Meteor's runs each
 * behaviour on its own schedule; this one picks one at random each time and waits a random interval
 * before the next.
 *
 * <p>That is the whole difference and it is the point of the module. An idle check that only measures
 * whether packets arrived is satisfied by either; one that looks at the <i>pattern</i> is not, because
 * a jump every four seconds on the dot is a shape no person produces. Choosing what and when at
 * random has no shape to find.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class AntiAFKl extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> turn = sgGeneral.add(new BoolSetting.Builder()
        .name("turn")
        .description("Look somewhere else.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
        .name("swing")
        .description("Swing your arm.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> jump = sgGeneral.add(new BoolSetting.Builder()
        .name("jump")
        .description("Jump on the spot.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> changeSlot = sgGeneral.add(new BoolSetting.Builder()
        .name("change-slot")
        .description("Switch to another hotbar slot.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> minDelay = sgGeneral.add(new IntSetting.Builder()
        .name("min-delay")
        .description("Fewest ticks between actions.")
        .defaultValue(40)
        .min(1)
        .sliderRange(1, 400)
        .build()
    );

    private final Setting<Integer> maxDelay = sgGeneral.add(new IntSetting.Builder()
        .name("max-delay")
        .description("Most ticks between actions. The gap to the minimum is what removes the pattern.")
        .defaultValue(140)
        .min(1)
        .sliderRange(1, 400)
        .build()
    );

    /** Rebuilt whenever an action is picked, so toggling a setting takes effect immediately. */
    private final List<Runnable> actions = new ArrayList<>();

    private int timer;

    public AntiAFKl() {
        super(Categories.Player, "anti-afkl", "Does a random thing at random intervals to avoid an idle kick.");
    }

    @Override
    public void onActivate() {
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (--timer > 0) return;

        actions.clear();

        if (turn.get()) actions.add(() -> {
            mc.player.setYRot(mc.player.getYRot() + (float) (Math.random() * 180 - 90));
            mc.player.setXRot((float) (Math.random() * 60 - 30));
        });

        if (swing.get()) actions.add(() -> mc.player.swing(InteractionHand.MAIN_HAND));

        if (jump.get()) actions.add(() -> {
            if (mc.player.onGround()) mc.player.jumpFromGround();
        });

        if (changeSlot.get()) actions.add(() -> mc.player.getInventory().setSelectedSlot((int) (Math.random() * 9)));

        if (actions.isEmpty()) return;

        actions.get((int) (Math.random() * actions.size())).run();

        int min = Math.min(minDelay.get(), maxDelay.get());
        int max = Math.max(minDelay.get(), maxDelay.get());

        timer = min + (int) (Math.random() * (max - min + 1));
    }
}
