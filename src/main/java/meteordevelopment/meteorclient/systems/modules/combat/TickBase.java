/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
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
 * Saves up ticks while nothing is happening and spends them all the moment a fight starts.
 *
 * <p>The server accepts movement at a rate rather than at an exact time, so a client that ticks slower
 * than real time for a while builds a credit it can then spend by ticking faster - arriving several
 * ticks' worth of movement into a fight in the space of one. That burst is the module: it is what puts
 * you in range before the other side has had a tick to react.
 *
 * <p>The credit is real and finite. Every tick spent above normal speed has to have been earned below
 * it first, and a position correction means the server has already stopped believing the surplus, so
 * the balance is thrown away rather than spent.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class TickBase extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> maxBalance = sgGeneral.add(new IntSetting.Builder()
        .name("max-balance")
        .description("Ticks that may be saved up at once. Larger bursts are also larger tells.")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 40)
        .build()
    );

    private final Setting<Double> chargeSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("charge-speed")
        .description("Timer speed while saving. Below one is what earns the balance.")
        .defaultValue(0.88)
        .min(0.1)
        .max(1)
        .sliderRange(0.1, 1)
        .build()
    );

    private final Setting<Double> burstSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("burst-speed")
        .description("Timer speed while spending.")
        .defaultValue(3)
        .min(1)
        .sliderRange(1, 10)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Start spending when a target comes within this far.")
        .defaultValue(6)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Boolean> onlyOnGround = sgGeneral.add(new BoolSetting.Builder()
        .name("only-on-ground")
        .description("Only charge while on the ground, where a slow timer is least visible.")
        .defaultValue(true)
        .build()
    );

    /** Ticks currently saved up. Earned below normal speed, spent above it. */
    private double balance;

    public TickBase() {
        super(Categories.Combat, "tick-base", "Saves up ticks while idle and spends them entering a fight.");
    }

    @Override
    public void onActivate() {
        balance = 0;
    }

    @Override
    public void onDeactivate() {
        Modules.get().get(Timer.class).setOverride(Timer.OFF);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        Timer timer = Modules.get().get(Timer.class);

        boolean targetNear = nearestTarget() != null;
        double speed;

        if (targetNear && balance >= 1) {
            speed = burstSpeed.get();
        } else if (!targetNear && (!onlyOnGround.get() || mc.player.onGround())) {
            speed = chargeSpeed.get();
        } else {
            speed = Timer.OFF;
        }

        timer.setOverride(speed);

        // A tick at speed n costs n-1 ticks of credit, and one below normal earns the difference back,
        // so the balance is exactly the time the server has not been told about.
        balance = Math.clamp(balance - (speed - 1), 0, maxBalance.get());
    }

    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        // The server has stopped believing the surplus, so spending it now would only make it worse.
        if (event.packet instanceof ClientboundPlayerPositionPacket) balance = 0;
    }

    private Player nearestTarget() {
        for (Player player : mc.level.players()) {
            if (player == mc.player || !player.isAlive() || !Friends.get().shouldAttack(player)) continue;
            if (mc.player.distanceTo(player) <= range.get()) return player;
        }

        return null;
    }

    @Override
    public String getInfoString() {
        return String.format("%.1f", balance);
    }
}
