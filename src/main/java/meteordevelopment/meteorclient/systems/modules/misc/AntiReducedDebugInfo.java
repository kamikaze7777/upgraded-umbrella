/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;

/**
 * Keeps the full debug screen on a server that asked for the cut-down one.
 *
 * <p>Reduced debug info is a flag the server sets on you, and the client obeys it by hiding
 * coordinates, the chunk section, and the block you are looking at. Nothing about it is enforced -
 * the client already has every value it stops showing, because it needs them to draw the world.
 * Refusing the flag only stops the client hiding what it already knows.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class AntiReducedDebugInfo extends Module {
    public AntiReducedDebugInfo() {
        super(Categories.Misc, "anti-reduced-debug-info", "Keeps the full debug screen on servers that ask for the reduced one.");
    }

    @Override
    public void onActivate() {
        // The flag is normally set once on join, so a module switched on later has to clear what is
        // already there rather than wait for another packet.
        if (mc.player != null) mc.player.setReducedDebugInfo(false);
    }
}
