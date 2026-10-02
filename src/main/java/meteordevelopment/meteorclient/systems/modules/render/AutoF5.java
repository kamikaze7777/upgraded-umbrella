/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * Drops into third person while a container is open, and back out when it closes.
 *
 * <p>Standing still in a chest with the camera locked to your face is the moment you are least able
 * to see someone walking up behind you. Swapping the view for as long as the screen is open costs
 * nothing, because a container screen already stops you moving.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class AutoF5 extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> rearView = sgGeneral.add(new BoolSetting.Builder()
        .name("rear-view")
        .description("Face the camera at you instead of away.")
        .defaultValue(false)
        .build()
    );

    /** Null while first person, which is also how "we have nothing to put back" is represented. */
    private CameraType previous;

    public AutoF5() {
        super(Categories.Render, "auto-f5", "Switches to third person while a container screen is open.");
    }

    @Override
    public void onDeactivate() {
        restore();
    }

    /**
     * Driven off a tick rather than the screen opening, so a screen closed by any route - the server,
     * a key, another module - is noticed just the same.
     */
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.gui.screen() instanceof AbstractContainerScreen<?>) {
            if (previous != null) return;

            previous = mc.options.getCameraType();
            mc.options.setCameraType(rearView.get() ? CameraType.THIRD_PERSON_FRONT : CameraType.THIRD_PERSON_BACK);
        } else {
            restore();
        }
    }

    private void restore() {
        if (previous == null) return;

        mc.options.setCameraType(previous);
        previous = null;
    }
}
