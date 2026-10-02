/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;

/**
 * Hides the arm swing, from you or from everyone else.
 *
 * <p>These are two separate things and the module keeps them apart. The animation you see is played
 * locally the moment you click. What other players see comes from a swing packet you send, which the
 * server relays; withholding it hides the swing from them while your own hits still land, because
 * nothing about a hit or a block break depends on that packet.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class NoSwing extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> hideFromSelf = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-from-self")
        .description("Do not play the swing animation on your own screen.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> hideFromServer = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-from-server")
        .description("Withhold the swing packet, so nobody else sees you swing.")
        .defaultValue(false)
        .build()
    );

    public NoSwing() {
        super(Categories.Render, "no-swing", "Hides the arm swing animation from you, from others, or both.");
    }

    public boolean hideFromSelf() {
        return isActive() && hideFromSelf.get();
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (hideFromServer.get() && event.packet instanceof ServerboundSwingPacket) event.cancel();
    }
}
