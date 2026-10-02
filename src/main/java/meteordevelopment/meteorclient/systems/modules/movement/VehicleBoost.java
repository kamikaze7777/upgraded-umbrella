/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.phys.Vec3;

/**
 * Throws you forward off whatever you were riding.
 *
 * <p>Leaving a vehicle normally drops you beside it with nothing carried over. The tick you step off is
 * a moment the server is already expecting your position to change sharply, so putting speed into it
 * launches you instead of stopping you.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class VehicleBoost extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> horizontal = sgGeneral.add(new DoubleSetting.Builder()
        .name("horizontal")
        .description("How hard to throw you along the way you are facing.")
        .defaultValue(2)
        .min(0)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<Double> vertical = sgGeneral.add(new DoubleSetting.Builder()
        .name("vertical")
        .description("How hard to throw you upwards.")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 5)
        .build()
    );

    private boolean wasRiding;

    public VehicleBoost() {
        super(Categories.Movement, "vehicle-boost", "Launches you forward as you step off a vehicle.");
    }

    @Override
    public void onActivate() {
        wasRiding = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        boolean riding = mc.player.isPassenger();

        // The edge, not the state: this fires on the single tick the vehicle is left behind.
        if (wasRiding && !riding) {
            double yaw = Math.toRadians(mc.player.getYRot());

            mc.player.setDeltaMovement(new Vec3(
                -Math.sin(yaw) * horizontal.get(),
                vertical.get(),
                Math.cos(yaw) * horizontal.get()
            ));
        }

        wasRiding = riding;
    }
}
