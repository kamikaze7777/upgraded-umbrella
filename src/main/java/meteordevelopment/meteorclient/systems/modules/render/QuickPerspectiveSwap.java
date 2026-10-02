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
import net.minecraft.client.CameraType;

/**
 * Third person for as long as the bind is held, first person again the moment it is let go.
 *
 * <p>Cycling with F5 costs two presses to get back and leaves you facing the wrong way in between.
 * Holding a key instead makes a look behind something you can do mid-fight.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class QuickPerspectiveSwap extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> rearView = sgGeneral.add(new BoolSetting.Builder()
        .name("rear-view")
        .description("Face the camera at you instead of away, for a look behind.")
        .defaultValue(false)
        .build()
    );

    private CameraType previous;

    public QuickPerspectiveSwap() {
        super(Categories.Render, "quick-perspective-swap", "Switches to third person while the bind is held.");

        // Held rather than toggled - the whole point is that letting go puts the view back.
        toggleOnBindRelease = true;
    }

    @Override
    public void onActivate() {
        previous = mc.options.getCameraType();
        mc.options.setCameraType(rearView.get() ? CameraType.THIRD_PERSON_FRONT : CameraType.THIRD_PERSON_BACK);
    }

    @Override
    public void onDeactivate() {
        if (previous != null) mc.options.setCameraType(previous);
        previous = null;
    }
}
