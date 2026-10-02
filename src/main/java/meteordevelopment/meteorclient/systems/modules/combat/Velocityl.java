/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

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
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Holds a knockback packet for a moment instead of cancelling it.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code velocity}, kept alongside it
 * under the {@code l} suffix the rest of this port uses for name collisions. Meteor's scales the
 * knockback down or away; the difference here is that the knockback still arrives, only late.
 *
 * <p>That matters because a knockback that never lands is a thing the server can notice - your
 * position after a hit is not where it put you. A knockback that lands a few ticks later leaves you
 * where the server expects by the time it looks, while giving you those ticks to swing back in. It is
 * the same idea as {@code backtrack}, applied to one packet instead of the whole stream.
 *
 * <p>The hold is dropped the moment the server corrects your position, which is it saying it has
 * already stopped believing you.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class Velocityl extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks to hold a knockback for before letting it through.")
        .defaultValue(6)
        .min(1)
        .sliderRange(1, 40)
        .build()
    );

    private final Setting<Boolean> explosions = sgGeneral.add(new BoolSetting.Builder()
        .name("explosions")
        .description("Hold the knockback from explosions too.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> pauseOnFlag = sgGeneral.add(new IntSetting.Builder()
        .name("pause-on-flag")
        .description("Ticks to stop holding for after the server corrects your position.")
        .defaultValue(20)
        .min(0)
        .sliderRange(0, 100)
        .build()
    );

    /** Held knockbacks with the tick they arrived on, written on the network thread. */
    private final Deque<Held> queue = new ArrayDeque<>();

    private volatile int paused;
    private volatile int tick;

    public Velocityl() {
        super(Categories.Combat, "velocityl", "Holds knockback packets briefly instead of cancelling them.", "anti-knockback-delay");
    }

    @Override
    public void onDeactivate() {
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
        if (mc.player == null) return;

        if (event.packet instanceof ClientboundPlayerPositionPacket) {
            paused = pauseOnFlag.get();
            mc.execute(this::releaseAll);
            return;
        }

        if (paused > 0) return;

        boolean isKnockback = event.packet instanceof ClientboundSetEntityMotionPacket motion && motion.id() == mc.player.getId();
        if (!isKnockback && !(explosions.get() && event.packet instanceof ClientboundExplodePacket)) return;

        event.cancel();

        synchronized (queue) {
            queue.addLast(new Held(event.packet, tick));
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        tick++;
        if (paused > 0) paused--;

        int cutoff = tick - delay.get();

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
        synchronized (queue) {
            return queue.isEmpty() ? null : queue.size() + " held";
        }
    }

    private record Held(Packet<?> packet, int at) {
    }
}
