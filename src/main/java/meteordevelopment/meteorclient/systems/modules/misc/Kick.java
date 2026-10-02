/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.chat.Component;

/**
 * Gets you disconnected from the server on purpose, the moment you press the bind.
 *
 * <p>Leaving through the menu takes several seconds and a mouse. When the reason to leave is that
 * something is about to kill you, that is too slow, so this ends the connection on one key.
 *
 * <p>Every mode here acts only on your own connection: a clean quit, or a packet the server will
 * refuse and drop you for. Nothing is aimed at anybody else.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class Kick extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("How to break the connection.")
        .defaultValue(Mode.Quit)
        .build()
    );

    public Kick() {
        super(Categories.Misc, "kick", "Disconnects you from the server immediately.");
    }

    @Override
    public void onActivate() {
        if (mc.getConnection() == null) {
            toggle();
            return;
        }

        if (mc.hasSingleplayerServer() && mode.get() != Mode.Quit) {
            // The malformed modes go through an integrated server, which handles them by crashing the
            // game rather than by disconnecting.
            error("Only the quit mode is safe in singleplayer.");
            toggle();
            return;
        }

        switch (mode.get()) {
            case Quit -> mc.getConnection().getConnection().disconnect(Component.empty());

            // A position the server cannot parse. It rejects the packet and closes the connection.
            case InvalidPosition -> mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(
                Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, false, false
            ));

            // Attacking yourself is not a thing the server has a rule for, so it faults instead.
            case SelfHurt -> mc.getConnection().send(new ServerboundAttackPacket(mc.player.getId()));

            // A hotbar slot outside the nine that exist.
            case InvalidSlot -> mc.getConnection().send(new ServerboundSetCarriedItemPacket(-1));
        }

        toggle();
    }

    public enum Mode {
        Quit,
        InvalidPosition,
        SelfHurt,
        InvalidSlot
    }
}
