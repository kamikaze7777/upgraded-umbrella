/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;

/**
 * Lets you jump again the moment you land instead of waiting out the cooldown.
 *
 * <p>Landing arms a ten tick timer that has to run down before another jump is allowed, which is what
 * stops a held jump key from bouncing at full rate. Keeping that timer at zero means every landing can
 * turn straight back into a jump.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class NoJumpDelay extends Module {
    public NoJumpDelay() {
        super(Categories.Movement, "no-jump-delay", "Removes the cooldown between jumps so you can bounce on landing.");
    }
}
