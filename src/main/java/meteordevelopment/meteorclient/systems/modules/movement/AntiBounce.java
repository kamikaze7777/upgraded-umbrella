/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;

/**
 * Stops slime and bed blocks throwing you back into the air.
 *
 * <p>Bounce is a restitution factor the entity picks up from the block it lands on, and the whole
 * lookup is skipped for an entity that is "suppressing bounce" - which vanilla only ever means as
 * "is sneaking". Claiming that unconditionally lands you flat without holding sneak, and since the
 * decision is made and applied entirely client side, the server sees only the resulting positions.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class AntiBounce extends Module {
    public AntiBounce() {
        super(Categories.Movement, "anti-bounce", "Stops you bouncing off slime blocks and beds.");
    }
}
