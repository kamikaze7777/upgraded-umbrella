/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;

/**
 * Stops the server moving your held slot out from under you.
 *
 * <p>The server can tell the client which hotbar slot is selected, and the client obeys without asking.
 * Anything mid-swap - a swap back, an aura picking a weapon - can be left holding the wrong item by a
 * packet that arrives at the wrong moment. Ignoring it keeps the slot where you and your modules put it.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class NoSlotSet extends Module {
    public NoSlotSet() {
        super(Categories.Player, "no-slot-set", "Ignores the server telling the client which hotbar slot to hold.");
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (event.packet instanceof ClientboundSetHeldSlotPacket) event.cancel();
    }
}
