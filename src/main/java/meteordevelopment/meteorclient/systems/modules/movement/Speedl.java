/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;

/**
 * Moves you faster than walking, by one of two shapes that get past different checks.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code speed}, kept alongside it under
 * the {@code l} suffix the rest of this port uses for name collisions. Meteor's holds a raised speed
 * with a limit; the two modes here are about the shape of the movement over time rather than its size.
 *
 * <p><b>Bhop</b> jumps continuously and adds speed at the top of each hop. That is what a person
 * bunny-hopping actually produces, and a check comparing your speed against the ground friction sees a
 * player who is not touching the ground most of the time.
 *
 * <p><b>Burst</b> spends a short spell fast and then a longer one below normal, so the <i>average</i>
 * over any window a check measures comes back to walking pace. It covers less ground than a flat
 * setting and is far harder to catch.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class Speedl extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("The shape of the speed over time.")
        .defaultValue(Mode.Bhop)
        .build()
    );

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Blocks per tick to move at while fast. Walking is about 0.21.")
        .defaultValue(0.35)
        .min(0.05)
        .sliderRange(0.05, 1)
        .build()
    );

    private final Setting<Integer> fastTicks = sgGeneral.add(new meteordevelopment.meteorclient.settings.IntSetting.Builder()
        .name("fast-ticks")
        .description("Ticks spent fast per burst.")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 60)
        .visible(() -> mode.get() == Mode.Burst)
        .build()
    );

    private final Setting<Integer> slowTicks = sgGeneral.add(new meteordevelopment.meteorclient.settings.IntSetting.Builder()
        .name("slow-ticks")
        .description("Ticks spent below walking pace afterwards, which is what pays for the burst.")
        .defaultValue(20)
        .min(1)
        .sliderRange(1, 120)
        .visible(() -> mode.get() == Mode.Burst)
        .build()
    );

    private final Setting<Boolean> onlyOnGround = sgGeneral.add(new BoolSetting.Builder()
        .name("only-on-ground")
        .description("Do nothing while airborne, where speed is hardest to explain.")
        .defaultValue(false)
        .build()
    );

    private int phase;

    public Speedl() {
        super(Categories.Movement, "speedl", "Moves you faster, as a bunny hop or as bursts that average out.");
    }

    @Override
    public void onActivate() {
        phase = 0;
    }

    @Override
    public void onDeactivate() {
        Modules.get().get(Timer.class).setOverride(Timer.OFF);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        phase++;

        // Continuous jumping is what makes the speed explainable; without it a raised ground speed is
        // simply a number that does not match the friction under your feet.
        if (mode.get() == Mode.Bhop && mc.player.onGround() && isMoving()) mc.player.jumpFromGround();
    }

    @EventHandler
    private void onMove(PlayerMoveEvent event) {
        if (event.type != MoverType.SELF) return;
        if (onlyOnGround.get() && !mc.player.onGround()) return;
        if (!isMoving()) return;

        double target = currentSpeed();
        if (target <= 0) return;

        // The direction comes from the keys, the magnitude from the setting - so a turn does not throw
        // away the speed, which is what a naive multiplier does.
        float yaw = mc.player.getYRot() * Mth.DEG_TO_RAD;
        float sin = Mth.sin(yaw);
        float cos = Mth.cos(yaw);

        float forward = mc.player.zza;
        float left = mc.player.xxa;

        double length = Math.sqrt(forward * forward + left * left);
        if (length < 1.0E-4) return;

        double x = (left * cos - forward * sin) / length;
        double z = (forward * cos + left * sin) / length;

        ((IVec3) event.movement).meteor$setXZ(x * target, z * target);
    }

    /** The speed for this tick, which in burst mode is below walking pace most of the time. */
    private double currentSpeed() {
        if (mode.get() != Mode.Burst) return speed.get();

        int cycle = fastTicks.get() + slowTicks.get();

        if (phase % cycle < fastTicks.get()) return speed.get();

        // Paying it back. The slow spell is longer than the fast one, so the average lands under
        // walking pace however high the setting is.
        return 0.1;
    }

    private boolean isMoving() {
        return Math.abs(mc.player.zza) > 1.0E-5 || Math.abs(mc.player.xxa) > 1.0E-5;
    }

    public enum Mode {
        /** Jump continuously and carry the speed through the air. */
        Bhop,
        /** Alternate fast and slow so the average comes back to walking pace. */
        Burst
    }
}
