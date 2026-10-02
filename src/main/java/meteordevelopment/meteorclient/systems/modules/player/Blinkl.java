/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Suspends the conversation with the server in either direction, then lets it all through at once.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code blink}, kept alongside it under
 * the {@code l} suffix the rest of this port uses for name collisions. Meteor's holds what you send;
 * this can also hold what you receive, and the two do quite different things.
 *
 * <p>Holding <b>outgoing</b> freezes you where the server last saw you while you carry on moving
 * locally, and releasing teleports you to where you actually are. Holding <b>incoming</b> freezes the
 * world instead: nobody moves, nothing hits you, and releasing replays it all in order. Holding both
 * stops time in both directions.
 *
 * <p>Two safety valves. <b>Ambush</b> releases the moment you attack, so a hit is never sent from a
 * position the server has not been told about. And the keepalives are never held in either direction,
 * because a connection that stops answering them is a connection that gets dropped.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class Blinkl extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> outgoing = sgGeneral.add(new BoolSetting.Builder()
        .name("outgoing")
        .description("Hold what you send, so the server keeps you where you were.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> incoming = sgGeneral.add(new BoolSetting.Builder()
        .name("incoming")
        .description("Hold what you receive, so the world stops around you.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> ambush = sgGeneral.add(new BoolSetting.Builder()
        .name("ambush")
        .description("Release everything the moment you attack, so a hit is never sent from nowhere.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> autoRelease = sgGeneral.add(new IntSetting.Builder()
        .name("auto-release")
        .description("Ticks to hold before releasing on its own. Zero holds until you switch it off.")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 200)
        .build()
    );

    /** Held in arrival order. Order is everything - a replay out of order is a desync. */
    private final Deque<Packet<?>> sent = new ArrayDeque<>();
    private final Deque<Packet<?>> received = new ArrayDeque<>();

    /** Set while draining, so a released packet is not caught again on its way back out. */
    private boolean releasing;

    private int held;

    public Blinkl() {
        super(Categories.Player, "blinkl", "Suspends packets in either direction, then releases them all at once.");
    }

    @Override
    public void onDeactivate() {
        release();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        // Nothing left to replay into, and nothing left to send.
        synchronized (sent) {
            sent.clear();
        }
        synchronized (received) {
            received.clear();
        }
        held = 0;
    }

    @EventHandler
    private void onSend(PacketEvent.Send event) {
        if (releasing || !outgoing.get()) return;

        // A hit from a position the server does not have is refused anyway, and it is the loudest
        // possible way to be noticed.
        if (ambush.get() && (event.packet instanceof ServerboundAttackPacket || event.packet instanceof ServerboundInteractPacket)) {
            mc.execute(this::release);
            return;
        }

        event.cancel();

        synchronized (sent) {
            sent.addLast(event.packet);
        }
    }

    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        if (releasing || !incoming.get()) return;

        // Latency is measured with these, and a disconnect or a teleport has to arrive when it arrives.
        if (event.packet instanceof ClientboundKeepAlivePacket
            || event.packet instanceof ClientboundPingPacket
            || event.packet instanceof ClientboundDisconnectPacket
            || event.packet instanceof ClientboundPlayerPositionPacket) return;

        event.cancel();

        synchronized (received) {
            received.addLast(event.packet);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        held++;

        if (autoRelease.get() > 0 && held >= autoRelease.get()) release();
    }

    /** Sends everything held, then replays everything received, both in order. */
    private void release() {
        held = 0;

        if (mc.getConnection() == null) {
            synchronized (sent) {
                sent.clear();
            }
            synchronized (received) {
                received.clear();
            }
            return;
        }

        releasing = true;
        try {
            while (true) {
                Packet<?> packet;
                synchronized (sent) {
                    packet = sent.pollFirst();
                }
                if (packet == null) break;

                mc.getConnection().send(packet);
            }

            while (true) {
                Packet<?> packet;
                synchronized (received) {
                    packet = received.pollFirst();
                }
                if (packet == null) break;

                handle(packet);
            }
        } finally {
            releasing = false;
        }
    }

    @SuppressWarnings("unchecked")
    private void handle(Packet<?> packet) {
        ((Packet<ClientGamePacketListener>) packet).handle(mc.getConnection());
    }

    @Override
    public String getInfoString() {
        synchronized (sent) {
            synchronized (received) {
                int total = sent.size() + received.size();
                return total == 0 ? null : total + " held";
            }
        }
    }
}
