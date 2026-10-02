/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;

/**
 * Removes the pause the client puts on you for swinging at nothing.
 *
 * <p>A swing that hits neither block nor entity locks attacking for ten ticks. That penalty is purely
 * client side - the server never asked for it - so dropping it means a missed swing no longer costs
 * you the next half second of hits.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class NoMissCooldown extends Module {
    public NoMissCooldown() {
        super(Categories.Combat, "no-miss-cooldown", "Removes the ten tick pause the client applies when you swing at nothing.");
    }
}
