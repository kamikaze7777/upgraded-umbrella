/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.Vec3;

/**
 * Holds you exactly where you are while still answering the server, so nothing notices you stopped.
 *
 * <p>Simply not sending anything gets you timed out and, on most servers, rubber-banded back. What
 * this does instead is keep the conversation going with the same position over and over: movement is
 * refused locally, but a packet still goes out each tick saying you are standing still - which is a
 * thing players do.
 *
 * <p>It switches itself off the moment the server corrects your position, because past that point the
 * two views have already diverged and holding still only makes it worse.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class Freeze extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> keepAlive = sgGeneral.add(new BoolSetting.Builder()
        .name("keep-alive")
        .description("Keep sending a standing-still position, instead of going quiet.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> disableOnFlag = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-on-flag")
        .description("Switch off when the server corrects your position.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> disableOnDamage = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-on-damage")
        .description("Switch off when you take a hit, so this cannot hold you still while you die.")
        .defaultValue(true)
        .build()
    );

    /** Where to hold. Captured once, because reading it each tick would let drift accumulate. */
    private Vec3 anchor;
    private float health;

    public Freeze() {
        super(Categories.Movement, "freeze", "Holds you in place while still answering the server.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) {
            toggle();
            return;
        }

        anchor = mc.player.position();
        health = mc.player.getHealth();
    }

    @Override
    public void onDeactivate() {
        anchor = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (anchor == null) return;

        if (disableOnDamage.get() && mc.player.getHealth() < health) {
            toggle();
            return;
        }
        health = mc.player.getHealth();

        // Put back before anything reads it, so no movement accumulates while this is on.
        mc.player.setDeltaMovement(Vec3.ZERO);
        mc.player.absSnapTo(anchor.x, anchor.y, anchor.z);

        if (keepAlive.get()) {
            mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(anchor.x, anchor.y, anchor.z, true, false));
        }
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        // The client's own movement packets would report where it thinks it is going; only the held
        // position above should be leaving.
        if (event.packet instanceof ServerboundMovePlayerPacket && anchor != null) event.cancel();
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        // Receive fires on the network thread, so the toggle is handed to the main thread rather than
        // mutating the active module list from under whatever is iterating it.
        if (disableOnFlag.get() && event.packet instanceof ClientboundPlayerPositionPacket) mc.execute(this::disable);
    }
}
