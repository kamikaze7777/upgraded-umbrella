/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.player.Player;

/**
 * Spends a burst of timer to close the last few blocks onto someone, then pays it back.
 *
 * <p>Running the timer fast makes the client tick ahead of real time, which is how a gap is closed
 * faster than walking allows. It cannot be held: every tick spent above normal speed is a tick the
 * server has not accounted for, and a movement check adds them up. So this keeps a balance - it is
 * spent while closing, and earned back by running below normal speed the rest of the time.
 *
 * <p>The balance is emptied outright on a position correction, since that is the server saying it has
 * already noticed. What is left is a module that arrives a fraction of a second early into a fight and
 * spends the time between fights paying for it.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class TimerRange extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> boostSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("boost-speed")
        .description("Timer speed while closing the gap.")
        .defaultValue(2)
        .min(1)
        .sliderRange(1, 5)
        .build()
    );

    private final Setting<Double> recoverySpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("recovery-speed")
        .description("Timer speed while paying the balance back. Below one is what earns it.")
        .defaultValue(0.9)
        .min(0.1)
        .max(1)
        .sliderRange(0.1, 1)
        .build()
    );

    private final Setting<Double> boostDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("boost-distance")
        .description("Start spending when a target is within this far.")
        .defaultValue(6)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Double> stopDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("stop-distance")
        .description("Stop spending once a target is this close - the gap is already closed.")
        .defaultValue(3)
        .min(0)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<Double> balance = sgGeneral.add(new DoubleSetting.Builder()
        .name("balance")
        .description("Ticks of boost that may be owed at once. Higher closes further and is easier to catch.")
        .defaultValue(20)
        .min(1)
        .sliderRange(1, 60)
        .build()
    );

    private final Setting<Boolean> onlyOnGround = sgGeneral.add(new BoolSetting.Builder()
        .name("only-on-ground")
        .description("Do nothing while airborne.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> clearOnFlag = sgGeneral.add(new BoolSetting.Builder()
        .name("clear-on-flag")
        .description("Empty the balance when the server corrects your position.")
        .defaultValue(true)
        .build()
    );

    /** Ticks of boost still available. Spent above normal speed, earned back below it. */
    private double owed;

    public TimerRange() {
        super(Categories.Combat, "timer-range", "Spends timer to close onto a target, then pays it back.");
    }

    @Override
    public void onActivate() {
        owed = balance.get();
    }

    @Override
    public void onDeactivate() {
        Modules.get().get(Timer.class).setOverride(Timer.OFF);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        Timer timer = Modules.get().get(Timer.class);

        if (onlyOnGround.get() && !mc.player.onGround()) {
            timer.setOverride(Timer.OFF);
            return;
        }

        double speed = chooseSpeed();
        timer.setOverride(speed);

        // A tick at speed n costs n-1 ticks of balance, and a tick below normal earns the difference
        // back, so the account is exactly the time the server has not been told about.
        owed = Math.clamp(owed - (speed - 1), 0, balance.get());
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (clearOnFlag.get() && event.packet instanceof ClientboundPlayerPositionPacket) owed = 0;
    }

    private double chooseSpeed() {
        double nearest = nearestTargetDistance();

        // Already there, or nobody to close on - either way there is nothing to buy, so pay back.
        if (nearest <= stopDistance.get()) return Timer.OFF;
        if (nearest > boostDistance.get()) return owed < balance.get() ? recoverySpeed.get() : Timer.OFF;

        return owed > 0 ? boostSpeed.get() : recoverySpeed.get();
    }

    private double nearestTargetDistance() {
        double nearest = Double.MAX_VALUE;

        for (Player player : mc.level.players()) {
            if (player == mc.player || !player.isAlive() || !Friends.get().shouldAttack(player)) continue;

            nearest = Math.min(nearest, mc.player.distanceTo(player));
        }

        return nearest;
    }
}
