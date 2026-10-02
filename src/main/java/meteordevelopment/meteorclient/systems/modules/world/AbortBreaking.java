/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.world;

import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;

/**
 * Never tells the server you gave up on a block.
 *
 * <p>Letting go of the button, looking away or switching item all send an abort, and the progress made
 * so far is thrown out with it. Withholding that abort leaves the break standing at the server, so it
 * can be picked back up instead of started again.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class AbortBreaking extends Module {
    public AbortBreaking() {
        super(Categories.World, "abort-breaking", "Withholds the packet that tells the server you stopped breaking a block.");
    }
}
