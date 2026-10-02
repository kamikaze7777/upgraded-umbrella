/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;

/**
 * Carries four extra stacks around in the crafting grid.
 *
 * <p>Closing your inventory is what tells the server to give back whatever is sitting in the crafting
 * slots. Never sending that, the server keeps thinking the screen is open and leaves them where they
 * are, so the grid becomes four slots of storage that survive walking away.
 *
 * <p>They come back the moment anything else closes the container for you - dying, changing world, or
 * the server closing it itself - so this is a pocket, not a vault.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class MoreCarry extends Module {
    public MoreCarry() {
        super(Categories.Player, "more-carry", "Keeps items in the crafting grid by never telling the server you closed it.");
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (event.packet instanceof ServerboundContainerClosePacket) event.cancel();
    }
}
