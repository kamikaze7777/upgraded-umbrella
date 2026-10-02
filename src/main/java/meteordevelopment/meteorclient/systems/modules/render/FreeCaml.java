/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;

/**
 * Lets the camera interact from where it is, instead of from where your body is.
 *
 * <p>This is the half of LiquidBounce's {@code FreeCam} that Meteor's own {@code freecam} does not
 * cover, kept alongside it under the {@code l} suffix the rest of this port uses for name collisions.
 * Meteor's already flies the camera, cancels on damage or death, and reloads chunks - all of which is
 * deliberately left to it rather than duplicated here. This module rides on top of whichever one is
 * flying the camera.
 *
 * <p>Normally a detached camera is for looking only: the reach check runs from your body, so a chest
 * the camera is floating in front of is still a wall away from you. Picking from the camera instead
 * lets you open it - which is the same thing {@code ghost-hand} does through a wall, arrived at from
 * the other direction.
 *
 * <p>The server still applies its own range check to whatever is clicked, so this reaches through
 * walls but not further than the server allows. What it cannot do is make an out-of-range interaction
 * succeed.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class FreeCaml extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> blocks = sgGeneral.add(new BoolSetting.Builder()
        .name("blocks")
        .description("Let the camera pick blocks, so containers and buttons can be reached.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> entities = sgGeneral.add(new BoolSetting.Builder()
        .name("entities")
        .description("Let the camera pick entities as well.")
        .defaultValue(false)
        .build()
    );

    public FreeCaml() {
        super(Categories.Render, "free-caml", "Interacts from the detached camera rather than from your body.");
    }

    /** Whether the pick should run from the camera this frame. Only meaningful while a freecam is flying. */
    public boolean shouldPickFromCamera(boolean entity) {
        if (!isActive()) return false;

        // Nothing to reach from unless the camera has actually left the body.
        if (!Modules.get().isActive(Freecam.class)) return false;

        return entity ? entities.get() : blocks.get();
    }
}
