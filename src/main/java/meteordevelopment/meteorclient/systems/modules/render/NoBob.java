/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;

/**
 * Holds the camera still while you walk.
 *
 * <p>Walking sways the view in time with your steps. The vanilla setting for it also stops the held
 * item swaying, which most people would rather keep; this drops only the camera sway.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class NoBob extends Module {
    public NoBob() {
        super(Categories.Render, "no-bob", "Stops the camera swaying as you walk, without touching hand sway.");
    }
}
