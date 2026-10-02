/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.game.GameLeftEvent;
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
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Holds back your own movement packets for a moment, so the server's copy of you lags behind the real
 * one.
 *
 * <p>This is the mirror of Backtrack. There the enemy is held back on your screen; here you are held
 * back on theirs, which means their aura or their aim is working against a position you have already
 * left. Only movement is delayed - hits, item use and everything else go out at the usual time, so
 * your own attacks land from where you actually are.
 *
 * <p>The held packets are always sent, never dropped, and always in order. Dropping one would leave the
 * server with a position you never occupied, and a correction the moment it noticed.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class FakeLag extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("How long to hold movement, in milliseconds. Long holds end in a correction.")
        .defaultValue(150)
        .min(0)
        .sliderRange(0, 500)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Only hold while a target is within this far.")
        .defaultValue(6)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Boolean> alwaysOn = sgGeneral.add(new BoolSetting.Builder()
        .name("always")
        .description("Hold all the time instead of only near a target.")
        .defaultValue(false)
        .build()
    );

    /** Held movement in send order, written and drained from the main thread only. */
    private final Deque<Held> queue = new ArrayDeque<>();

    /** Set while the queue is being drained, so a released packet is not caught again on its way out. */
    private boolean releasing;

    public FakeLag() {
        super(Categories.Combat, "fake-lag", "Holds your movement packets briefly so the server's copy of you lags behind.");
    }

    @Override
    public void onDeactivate() {
        releaseAll();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        queue.clear();
    }

    @EventHandler
    private void onSend(PacketEvent.Send event) {
        if (releasing) return;
        if (!(event.packet instanceof ServerboundMovePlayerPacket)) return;

        if (!shouldHold()) {
            // The hold has ended. What is queued has to go first, so this packet joins the back of the
            // queue rather than overtaking it.
            if (queue.isEmpty()) return;

            event.cancel();
            queue.addLast(new Held(event.packet, System.currentTimeMillis()));
            releaseAll();
            return;
        }

        event.cancel();
        queue.addLast(new Held(event.packet, System.currentTimeMillis()));
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!shouldHold()) {
            releaseAll();
            return;
        }

        long cutoff = System.currentTimeMillis() - delay.get();

        releasing = true;
        try {
            while (!queue.isEmpty() && queue.peekFirst().at <= cutoff) {
                mc.player.connection.send(queue.removeFirst().packet);
            }
        } finally {
            releasing = false;
        }
    }

    private boolean shouldHold() {
        if (mc.player == null || mc.getConnection() == null) return false;
        if (alwaysOn.get()) return true;

        for (Player player : mc.level.players()) {
            if (player == mc.player || !player.isAlive() || !Friends.get().shouldAttack(player)) continue;
            if (mc.player.distanceTo(player) <= range.get()) return true;
        }

        return false;
    }

    private void releaseAll() {
        if (queue.isEmpty()) return;

        if (mc.player == null || mc.getConnection() == null) {
            queue.clear();
            return;
        }

        releasing = true;
        try {
            while (!queue.isEmpty()) mc.player.connection.send(queue.removeFirst().packet);
        } finally {
            releasing = false;
        }
    }

    @Override
    public String getInfoString() {
        return queue.isEmpty() ? null : queue.size() + " held";
    }

    private record Held(Packet<?> packet, long at) {
    }
}
