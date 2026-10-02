/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Holds the server's updates for a fraction of a second, so a target you are fighting stays where you
 * last saw them instead of where they now are.
 *
 * <p>Nothing about your own attack is faked. An attack names an entity by id and the server checks its
 * own copy of where that entity is, against a reach allowance of about six blocks measured eyes to
 * hitbox. Holding the incoming stream means your client - and therefore your crosshair - is still
 * looking at a position from a moment ago, and the hit lands anyway because the real target has not yet
 * left that generous allowance.
 *
 * <p>That is also the limit of it: past the allowance the hold is working against you, hiding a target
 * that has already moved somewhere you could have hit. So the delay wants to stay short.
 *
 * <p>The stream is released untouched on anything that must not arrive late - a teleport, a
 * disconnect, your own death - and the keepalives that latency is measured with are never held at all.
 * Releases always happen on the main thread and always in arrival order; replaying an old position
 * after a newer one would put the target somewhere neither side believes it is.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class Backtrack extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("How long to hold updates, in milliseconds. This is added to your real latency.")
        .defaultValue(120)
        .min(0)
        .sliderRange(0, 400)
        .build()
    );

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Whether to hold only around your own hits, or whenever a target is in range.")
        .defaultValue(Mode.Attack)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far away a target may be for the hold to run.")
        .defaultValue(4)
        .min(0)
        .sliderRange(0, 8)
        .build()
    );

    private final Setting<Integer> attackWindow = sgGeneral.add(new IntSetting.Builder()
        .name("attack-window")
        .description("Milliseconds after your last hit that the hold stays active.")
        .defaultValue(1000)
        .min(0)
        .sliderRange(0, 5000)
        .visible(() -> mode.get() == Mode.Attack)
        .build()
    );

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Outline the target while the hold is running.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .defaultValue(ShapeMode.Lines)
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .defaultValue(new SettingColor(255, 160, 0, 40))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .defaultValue(new SettingColor(255, 160, 0, 180))
        .visible(render::get)
        .build()
    );

    /**
     * Held packets in arrival order, written from the network thread and drained from the main one.
     */
    private final Deque<Held> queue = new ArrayDeque<>();

    private volatile Player target;
    private volatile long lastAttack;

    public Backtrack() {
        super(Categories.Combat, "backtrack", "Holds server updates briefly so a target stays where you last saw them.");
    }

    @Override
    public void onDeactivate() {
        releaseAll();
        target = null;
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        // There is nothing left to hand them to, so these are dropped rather than replayed.
        synchronized (queue) {
            queue.clear();
        }
        target = null;
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (mode.get() != Mode.Attack) return;
        if (!(event.entity instanceof Player player) || !Friends.get().shouldAttack(player)) return;

        lastAttack = System.currentTimeMillis();
        target = player;
    }

    /**
     * Runs on the network thread. It only ever reads state and moves packets onto the queue; every
     * decision that touches the client is deferred to the main thread.
     */
    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        Packet<?> packet = event.packet;

        // Latency is measured with these, and delaying chat gains nothing.
        if (packet instanceof ClientboundKeepAlivePacket
            || packet instanceof ClientboundPingPacket
            || packet instanceof ClientboundSystemChatPacket) return;

        boolean urgent = packet instanceof ClientboundPlayerPositionPacket
            || packet instanceof ClientboundDisconnectPacket
            || (packet instanceof ClientboundSetHealthPacket health && health.getHealth() <= 0);

        if (!urgent && shouldHold()) {
            event.cancel();
            enqueue(packet);
            return;
        }

        // Either something urgent arrived or the hold has ended. Either way what is already queued has
        // to go out first, so this packet joins the back of it rather than overtaking it.
        if (isEmpty()) return;

        event.cancel();
        enqueue(packet);
        mc.execute(this::releaseAll);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mode.get() == Mode.Range) target = findTarget();

        if (!shouldHold()) {
            releaseAll();
            return;
        }

        long cutoff = System.currentTimeMillis() - delay.get();

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

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || target == null || !isHolding()) return;

        event.renderer.box(target.getBoundingBox(), sideColor.get(), lineColor.get(), shapeMode.get(), 0);
    }

    /** Whether there is a live reason to be holding the stream right now. */
    private boolean shouldHold() {
        Player current = target;

        if (mc.player == null || mc.getConnection() == null || current == null) return false;
        if (!current.isAlive() || mc.player.tickCount < 20) return false;
        if (mc.player.distanceTo(current) > range.get()) return false;

        return mode.get() != Mode.Attack || System.currentTimeMillis() - lastAttack <= attackWindow.get();
    }

    public boolean isHolding() {
        return isActive() && !isEmpty();
    }

    private void enqueue(Packet<?> packet) {
        synchronized (queue) {
            queue.addLast(new Held(packet, System.currentTimeMillis()));
        }
    }

    private boolean isEmpty() {
        synchronized (queue) {
            return queue.isEmpty();
        }
    }

    private Player findTarget() {
        Player best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Player player : mc.level.players()) {
            if (player == mc.player || !player.isAlive() || !Friends.get().shouldAttack(player)) continue;

            double distance = mc.player.distanceTo(player);
            if (distance > range.get() || distance >= bestDistance) continue;

            best = player;
            bestDistance = distance;
        }

        return best;
    }

    /** Hands everything held back to the client at once, in order. */
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

    /**
     * Replayed packets do not pass back through the connection, so this cannot see its own output and
     * no re-entrancy guard is needed.
     */
    @SuppressWarnings("unchecked")
    private void handle(Packet<?> packet) {
        ((Packet<ClientGamePacketListener>) packet).handle(mc.getConnection());
    }

    private record Held(Packet<?> packet, long at) {
    }

    public enum Mode {
        /** Hold only in the moments around your own hits. */
        Attack,
        /** Hold whenever anyone worth hitting is in range. */
        Range
    }
}
