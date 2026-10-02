/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.world;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;

/**
 * Runs the game clock fast, slow, or alternating between the two.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code timer}, kept alongside it under
 * the {@code l} suffix the rest of this port uses for name collisions. Meteor's holds one multiplier;
 * what this adds is the two shapes that make a fast timer survivable.
 *
 * <p>A timer above one is time the server has not accounted for, and it adds up until a movement check
 * notices. <b>Pulse</b> alternates fast and slow so the average comes back to normal, and <b>Boost</b>
 * spends a fixed budget and then stops. Both are ways of using a timer for longer than holding it
 * flat allows.
 *
 * <p>It drives Meteor's own timer override rather than keeping a second clock, so the two cannot end
 * up fighting - whichever was set last wins, and switching this off restores normal speed.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class Timerl extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("How to shape the speed over time.")
        .defaultValue(Mode.Classic)
        .build()
    );

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("The fast speed. One is normal.")
        .defaultValue(2)
        .min(0.1)
        .sliderRange(0.1, 10)
        .build()
    );

    private final Setting<Integer> fastTicks = sgGeneral.add(new IntSetting.Builder()
        .name("fast-ticks")
        .description("Ticks spent fast per cycle.")
        .defaultValue(20)
        .min(1)
        .sliderRange(1, 200)
        .visible(() -> mode.get() == Mode.Pulse)
        .build()
    );

    private final Setting<Double> slowSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("slow-speed")
        .description("The slow speed, which is what pays for the fast one.")
        .defaultValue(0.5)
        .min(0.1)
        .max(1)
        .sliderRange(0.1, 1)
        .visible(() -> mode.get() == Mode.Pulse)
        .build()
    );

    private final Setting<Integer> slowTicks = sgGeneral.add(new IntSetting.Builder()
        .name("slow-ticks")
        .description("Ticks spent slow per cycle.")
        .defaultValue(20)
        .min(1)
        .sliderRange(1, 200)
        .visible(() -> mode.get() == Mode.Pulse)
        .build()
    );

    private final Setting<Integer> budget = sgGeneral.add(new IntSetting.Builder()
        .name("budget")
        .description("Ticks of fast time to spend before stopping.")
        .defaultValue(40)
        .min(1)
        .sliderRange(1, 200)
        .visible(() -> mode.get() == Mode.Boost)
        .build()
    );

    private final Setting<Boolean> onlyWhenMoving = sgGeneral.add(new BoolSetting.Builder()
        .name("only-when-moving")
        .description("Hold normal speed while standing still, where fast time buys nothing.")
        .defaultValue(false)
        .build()
    );

    private int phase;
    private int spent;

    public Timerl() {
        super(Categories.World, "timerl", "Runs the game clock fast, slow, or alternating. LiquidBounce's timer.");
    }

    @Override
    public void onActivate() {
        phase = 0;
        spent = 0;
    }

    @Override
    public void onDeactivate() {
        Modules.get().get(Timer.class).setOverride(Timer.OFF);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        Timer timer = Modules.get().get(Timer.class);

        if (onlyWhenMoving.get() && mc.player.getDeltaMovement().horizontalDistanceSqr() < 1.0E-4) {
            timer.setOverride(Timer.OFF);
            return;
        }

        timer.setOverride(switch (mode.get()) {
            case Classic -> speed.get();
            case Pulse -> pulse();
            case Boost -> spent++ < budget.get() ? speed.get() : Timer.OFF;
        });
    }

    /** Alternates between the two speeds, spending as long in each as its own setting says. */
    private double pulse() {
        phase++;

        int cycle = fastTicks.get() + slowTicks.get();
        int position = phase % cycle;

        return position < fastTicks.get() ? speed.get() : slowSpeed.get();
    }

    @Override
    public String getInfoString() {
        return mode.get() == Mode.Boost ? Math.max(0, budget.get() - spent) + " left" : null;
    }

    public enum Mode {
        /** One speed, held. */
        Classic,
        /** Alternates fast and slow, so the average comes back towards normal. */
        Pulse,
        /** Spends a fixed budget of fast ticks and then stops. */
        Boost
    }
}
