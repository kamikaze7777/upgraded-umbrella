/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Answers the server's latency probes late, so the number next to your name is one you chose.
 *
 * <p>Latency is measured by a keepalive: the server sends one and times how long the reply takes.
 * Holding the keepalive before the client answers it inflates exactly that measurement and nothing
 * else - your movement, your hits, and everything else go out at the usual moment.
 *
 * <p>This is a display, not an advantage. It does not give you the reach or the lag compensation a
 * genuinely high-latency player gets, because the server derives those from when packets actually
 * arrive rather than from the number it shows.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class PingSpoof extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> ping = sgGeneral.add(new IntSetting.Builder()
        .name("ping")
        .description("Milliseconds to add to what the server measures.")
        .defaultValue(500)
        .min(0)
        .sliderRange(0, 5000)
        .build()
    );

    /** Held probes in arrival order, written from the network thread and drained from the main one. */
    private final Deque<Held> queue = new ArrayDeque<>();

    public PingSpoof() {
        super(Categories.Misc, "ping-spoof", "Answers the server's latency probes late, to show a chosen ping.");
    }

    @Override
    public void onDeactivate() {
        // Anything still held is answered at once. Leaving a keepalive unanswered is what gets a client
        // timed out, and the timeout is measured from when the server sent it, not from now.
        releaseAll();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        synchronized (queue) {
            queue.clear();
        }
    }

    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        if (!(event.packet instanceof ClientboundKeepAlivePacket) && !(event.packet instanceof ClientboundPingPacket)) return;

        event.cancel();

        synchronized (queue) {
            queue.addLast(new Held(event.packet, System.currentTimeMillis()));
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.getConnection() == null) return;

        // Kept well inside the thirty seconds a server waits before dropping a silent client, so a large
        // setting cannot get you disconnected.
        long cutoff = System.currentTimeMillis() - Math.min(ping.get(), 15000);

        while (true) {
            Held held;

            synchronized (queue) {
                held = queue.peekFirst();
                if (held == null || held.at > cutoff) break;
                queue.removeFirst();
            }

            handle(held.packet);
        }
    }

    private void releaseAll() {
        if (mc.getConnection() == null) {
            synchronized (queue) {
                queue.clear();
            }
            return;
        }

        while (true) {
            Held held;

            synchronized (queue) {
                held = queue.pollFirst();
            }
            if (held == null) break;

            handle(held.packet);
        }
    }

    @SuppressWarnings("unchecked")
    private void handle(Packet<?> packet) {
        ((Packet<ClientGamePacketListener>) packet).handle(mc.getConnection());
    }

    @Override
    public String getInfoString() {
        return ping.get() + "ms";
    }

    private record Held(Packet<?> packet, long at) {
    }
}
