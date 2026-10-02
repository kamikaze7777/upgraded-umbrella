/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.FallingBlockEntity;

/**
 * Boxes the entities a hide-and-seek game uses to disguise players as scenery.
 *
 * <p>A disguise is not a block. The server has to keep a real entity where the player is, and dresses
 * it up - normally a falling block or an invisible armour stand carrying a block on its head - because
 * a genuine block cannot move or be hit. That is the tell, and it holds regardless of which plugin the
 * server is running: an entity is standing where the map says there is only terrain.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class ProphuntESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far away to keep drawing.")
        .defaultValue(64)
        .min(1)
        .sliderRange(8, 256)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgGeneral.add(new ColorSetting.Builder()
        .name("side-color")
        .defaultValue(new SettingColor(255, 0, 255, 50))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgGeneral.add(new ColorSetting.Builder()
        .name("line-color")
        .defaultValue(new SettingColor(255, 0, 255, 200))
        .build()
    );

    public ProphuntESP() {
        super(Categories.Render, "prophunt-esp", "Boxes the entities used to disguise players as scenery.");
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        double rangeSq = range.get() * range.get();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (mc.player.distanceToSqr(entity) > rangeSq) continue;
            if (!isDisguise(entity)) continue;

            event.renderer.box(entity.getBoundingBox(), sideColor.get(), lineColor.get(), shapeMode.get(), 0);
        }
    }

    /**
     * A falling block that is not falling has been placed there to be looked at, and an invisible
     * armour stand exists only to carry something. Both are how a block-shaped disguise is built.
     */
    private boolean isDisguise(Entity entity) {
        if (entity instanceof FallingBlockEntity falling) return falling.getDeltaMovement().lengthSqr() < 1.0E-4;

        return entity instanceof ArmorStand stand && stand.isInvisible();
    }
}
