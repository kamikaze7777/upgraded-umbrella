/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;

/**
 * Stops the camera lurching when you take a hit.
 *
 * <p>Damage tilts the view for the length of the hurt animation, which throws your aim off at exactly
 * the moment you need it. Nothing about the tilt is sent anywhere, so dropping it only steadies what
 * you see.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class NoHurtCam extends Module {
    public NoHurtCam() {
        super(Categories.Render, "no-hurt-cam", "Stops the camera tilting when you take damage.");
    }
}
