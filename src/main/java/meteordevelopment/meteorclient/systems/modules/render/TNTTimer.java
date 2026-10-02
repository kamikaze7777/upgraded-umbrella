/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.phys.AABB;

/**
 * Draws primed TNT with a box that reddens as its fuse runs down.
 *
 * <p>Primed TNT is an entity, and the fuse left on it is state the server sends, so the time to the
 * blast is known exactly rather than estimated. The colour carries it: the box is yellow on a full
 * fuse and fully red as it goes off, which reads at a glance from further away than a number would.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class TNTTimer extends Module {
    /** The fuse TNT is primed with, and so what the colour ramp is measured against. */
    private static final int DEFAULT_FUSE = 80;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far away to keep drawing.")
        .defaultValue(64)
        .min(1)
        .sliderRange(8, 256)
        .build()
    );

    private final Setting<Boolean> blastRadius = sgGeneral.add(new BoolSetting.Builder()
        .name("blast-radius")
        .description("Draw the area the blast reaches instead of the entity's own box.")
        .defaultValue(false)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<Integer> opacity = sgGeneral.add(new IntSetting.Builder()
        .name("opacity")
        .description("Line opacity. The hue is not a setting - it is the fuse.")
        .defaultValue(200)
        .range(0, 255)
        .sliderRange(0, 255)
        .build()
    );

    /** Reused so the per-frame colour does not allocate once per entity. */
    private final Color side = new Color();
    private final Color line = new Color();

    public TNTTimer() {
        super(Categories.Render, "tnt-timer", "Draws primed TNT with a box that reddens as the fuse runs down.");
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        double rangeSq = range.get() * range.get();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof PrimedTnt tnt)) continue;
            if (mc.player.distanceToSqr(tnt) > rangeSq) continue;

            // A full fuse is yellow and none left is red, so the green channel is simply what remains.
            int green = Math.clamp(255 * tnt.getFuse() / DEFAULT_FUSE, 0, 255);

            line.set(255, green, 0, opacity.get());
            side.set(255, green, 0, opacity.get() / 3);

            AABB box = blastRadius.get() ? tnt.getBoundingBox().inflate(4) : tnt.getBoundingBox();

            event.renderer.box(box, side, line, shapeMode.get(), 0);
        }
    }
}
