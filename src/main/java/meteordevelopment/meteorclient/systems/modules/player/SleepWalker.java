/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

/**
 * Gets you up and moving while the server still has you asleep.
 *
 * <p>Sleeping is two separate pieces of state. The server keeps you in the bed and treats you as
 * immobile; the client keeps you laid down and holds the sleep screen open. Standing the client back
 * up without telling the server leaves the two disagreeing - you walk around while, as far as the
 * server is concerned, you are still lying in bed and cannot be moved.
 *
 * <p>Anything that legitimately wakes you resyncs it, so this lasts as long as the night does.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class SleepWalker extends Module {
    public SleepWalker() {
        super(Categories.Player, "sleep-walker", "Lets you move while the server still thinks you are in bed.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!mc.player.isSleeping()) return;

        // Stand the client back up and drop the sleep screen. Nothing here is sent, so the server's
        // idea of where we are sleeping is left exactly as it was.
        mc.player.stopSleeping();
        mc.gui.setScreen(null);
    }
}
