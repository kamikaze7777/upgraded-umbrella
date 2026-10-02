/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.mixininterface.IServerboundMovePlayerPacket;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;

/**
 * Makes your hits critical, and stops sprinting so they stay that way.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code criticals}, kept alongside it
 * under the {@code l} suffix the rest of this port uses for name collisions. The hop itself is the
 * same trick both use - a tiny rise and fall claimed before the attack, so the server has you off the
 * ground with fall distance when the hit lands.
 *
 * <p>What this adds is the sprint. A critical hit needs {@code fallDistance > 0}, {@code !onGround}
 * <b>and</b> {@code !isSprinting}, so a sprint hit is never critical no matter how good the hop is.
 * Sending a stop-sprint immediately before the attack buys the 1.5x multiplier; the sprint knockback
 * is what it costs, which is why it is a switch rather than always on.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class Criticalsl extends Module {
    /** Kept under the threshold at which a claimed step counts as movement rather than as a jitter. */
    private static final double HOP_HEIGHT = 0.0625;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("How to get off the ground for the hit.")
        .defaultValue(Mode.Packet)
        .build()
    );

    private final Setting<Boolean> stopSprinting = sgGeneral.add(new BoolSetting.Builder()
        .name("stop-sprinting")
        .description("Stop sprinting for the hit. A sprint hit can never be critical, whatever the hop does.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> resumeSprinting = sgGeneral.add(new BoolSetting.Builder()
        .name("resume-sprinting")
        .description("Start sprinting again straight after, so only the one hit loses the knockback.")
        .defaultValue(true)
        .visible(stopSprinting::get)
        .build()
    );

    public Criticalsl() {
        super(Categories.Combat, "criticalsl", "Crits every hit, and stops sprinting so the crit counts.");
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (!(event.packet instanceof ServerboundAttackPacket)) return;
        if (mc.player.isFallFlying() || mc.player.isInWater() || mc.player.onClimbable()) return;

        // Sprint first. The server reads it from the last command packet it saw, so it has to be in
        // front of the attack rather than merely before the hop.
        boolean wasSprinting = mc.player.isSprinting();

        if (stopSprinting.get() && wasSprinting) {
            event.sendSilently(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
        }

        if (mc.player.onGround()) hop(event);

        if (stopSprinting.get() && wasSprinting && resumeSprinting.get()) {
            // Queued behind the attack, so the hit is resolved before the sprint is back.
            mc.execute(() -> mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_SPRINTING)));
        }
    }

    /**
     * A rise and a fall, both too small to count as movement, which is all the server needs to have you
     * airborne with fall distance when the attack arrives.
     */
    private void hop(PacketEvent.Send event) {
        double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();

        switch (mode.get()) {
            case Packet -> {
                send(event, x, y + HOP_HEIGHT, z);
                send(event, x, y, z);
            }
            case Jump -> mc.player.jumpFromGround();
            case MiniJump -> {
                send(event, x, y + HOP_HEIGHT * 2, z);
                send(event, x, y + HOP_HEIGHT, z);
                send(event, x, y, z);
            }
        }
    }

    private void send(PacketEvent.Send event, double x, double y, double z) {
        ServerboundMovePlayerPacket packet = new ServerboundMovePlayerPacket.Pos(x, y, z, false, mc.player.horizontalCollision);

        // NoFall rewrites the ground flag of anything it does not recognise, and an on-ground packet
        // would wipe the fall distance the crit depends on.
        ((IServerboundMovePlayerPacket) packet).meteor$setTag(1337);

        event.sendSilently(packet);
    }

    public enum Mode {
        /** Two packets: up a fraction, then back down. */
        Packet,
        /** A real jump. Obvious, and it works everywhere. */
        Jump,
        /** Three packets, so the descent has a step in it. */
        MiniJump
    }
}
