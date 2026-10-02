/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * Draws a wireframe of an entity's actual box through walls, sliced to show its shape.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code chams}, kept alongside it under
 * the {@code l} suffix the rest of this port uses for name collisions. Meteor's re-renders the model
 * itself with a shader, which looks better and costs a custom render pipeline to do.
 *
 * <p>This draws the hitbox instead, in horizontal slices. The trade is deliberate: a model tells you
 * which way somebody is facing, and a sliced box tells you exactly where the thing you can hit is,
 * because it <i>is</i> the thing you can hit. In a crowd that is the more useful of the two, and it
 * needs no shader at all.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class Chamsl extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("What to draw.")
        .onlyAttackable()
        .defaultValue(EntityTypes.PLAYER)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far away to keep drawing.")
        .defaultValue(64)
        .min(1)
        .sliderRange(8, 256)
        .build()
    );

    private final Setting<Integer> slices = sgRender.add(new meteordevelopment.meteorclient.settings.IntSetting.Builder()
        .name("slices")
        .description("How many horizontal slices to cut the box into. One is a plain box.")
        .defaultValue(4)
        .min(1)
        .sliderRange(1, 16)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .defaultValue(ShapeMode.Lines)
        .build()
    );

    private final Setting<SettingColor> color = sgRender.add(new ColorSetting.Builder()
        .name("color")
        .defaultValue(new SettingColor(255, 255, 255, 160))
        .build()
    );

    private final Setting<SettingColor> friendColor = sgRender.add(new ColorSetting.Builder()
        .name("friend-color")
        .defaultValue(new SettingColor(0, 255, 100, 160))
        .build()
    );

    private final Setting<Boolean> ignoreSelf = sgRender.add(new BoolSetting.Builder()
        .name("ignore-self")
        .defaultValue(true)
        .build()
    );

    private final Color side = new Color();

    public Chamsl() {
        super(Categories.Render, "chamsl", "Draws entity hitboxes through walls, sliced to show their shape.");
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        double rangeSq = range.get() * range.get();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (ignoreSelf.get() && entity == mc.player) continue;
            if (!entities.get().contains(entity.getType())) continue;
            if (mc.player.distanceToSqr(entity) > rangeSq) continue;

            Color line = entity instanceof Player player && !Friends.get().shouldAttack(player) ? friendColor.get() : color.get();
            side.set(line.r, line.g, line.b, line.a / 4);

            // Interpolated, so the wireframe sits on the entity rather than a tick behind it.
            Vec3 at = entity.getPosition(event.tickDelta);
            AABB box = entity.getBoundingBox().move(at.subtract(entity.position()));

            draw(event, box, line);
        }
    }

    /**
     * Cuts the box into equal horizontal bands. The bands are what read as a shape - a single box at
     * this size is a rectangle, and several stacked read as a body at a glance.
     */
    private void draw(Render3DEvent event, AABB box, Color line) {
        int count = slices.get();
        double height = (box.maxY - box.minY) / count;

        for (int i = 0; i < count; i++) {
            double bottom = box.minY + height * i;

            event.renderer.box(box.minX, bottom, box.minZ, box.maxX, bottom + height, box.maxZ, side, line, shapeMode.get(), 0);
        }
    }
}
