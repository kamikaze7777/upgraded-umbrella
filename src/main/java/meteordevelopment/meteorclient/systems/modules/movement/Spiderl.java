/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

/**
 * Climbs walls by claiming upward movement while pressed against one.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's own {@code spider}, kept alongside it
 * under the {@code l} suffix the rest of this port uses for name collisions. The difference is in how
 * the climb is paced: rather than one fixed speed, this offers the two shapes that get past the
 * common movement checks - a steady rate that looks like a ladder, and a stutter that spends every
 * other tick doing nothing so the average stays under what a check will measure.
 *
 * <p>The whole thing rests on horizontal collision being a client-side fact the server does not
 * verify: it validates where you end up, and a wall it agrees you are touching makes upward movement
 * look like ordinary climbing.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class Spiderl extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("How to pace the climb.")
        .defaultValue(Mode.Steady)
        .build()
    );

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Blocks per tick to climb at.")
        .defaultValue(0.2)
        .min(0.01)
        .sliderRange(0.01, 0.5)
        .build()
    );

    /** Flipped each tick, so the stutter mode spends half its ticks not climbing. */
    private boolean beat;

    public Spiderl() {
        super(Categories.Movement, "spiderl", "Climbs walls. LiquidBounce's spider, alongside Meteor's own.", "wall-climb");
    }

    @Override
    public void onActivate() {
        beat = false;
    }

    @EventHandler
    private void onMove(PlayerMoveEvent event) {
        // Only worth anything against a wall - climbing in open air is a flight, and a very visible one.
        if (!mc.player.horizontalCollision) return;

        beat = !beat;

        if (mode.get() == Mode.Stutter && !beat) {
            ((IVec3) event.movement).meteor$setY(0);
            return;
        }

        ((IVec3) event.movement).meteor$setY(speed.get());
    }

    public enum Mode {
        /** Climbs every tick. Fast, and the most obvious. */
        Steady,
        /** Climbs every other tick, halving the average rate. */
        Stutter
    }
}
