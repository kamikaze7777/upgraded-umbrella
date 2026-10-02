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
 * Boxes entities through walls, coloured by what is worth knowing about them.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code esp}, kept alongside it under the
 * {@code l} suffix the rest of this port uses for name collisions. Meteor's decides colour by
 * category; this one puts a number in the colour instead - distance or remaining health - so a wall of
 * boxes is readable rather than uniform.
 *
 * <p>Two states override the mode because they are worth more than either number: a target that has
 * just been hit flashes red, which is how you tell whose fight you are watching, and an invisible
 * entity gets its own colour, because the fact that it is invisible is the whole reason you are
 * looking at a box instead of a body.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class ESPl extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColor = settings.createGroup("Color");

    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("What to box.")
        .onlyAttackable()
        .defaultValue(EntityTypes.PLAYER)
        .build()
    );

    private final Setting<Double> maxDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-distance")
        .description("How far away to keep drawing.")
        .defaultValue(128)
        .min(1)
        .sliderRange(8, 512)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .defaultValue(ShapeMode.Lines)
        .build()
    );

    private final Setting<Boolean> ignoreSelf = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-self")
        .description("Skip your own body, which matters in third person and freecam.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ColorMode> colorMode = sgColor.add(new EnumSetting.Builder<ColorMode>()
        .name("color-mode")
        .description("What the box's colour is telling you.")
        .defaultValue(ColorMode.Health)
        .build()
    );

    private final Setting<SettingColor> staticColor = sgColor.add(new ColorSetting.Builder()
        .name("color")
        .defaultValue(new SettingColor(255, 255, 255, 200))
        .visible(() -> colorMode.get() == ColorMode.Static)
        .build()
    );

    private final Setting<SettingColor> hurtColor = sgColor.add(new ColorSetting.Builder()
        .name("hurt-color")
        .description("Flashed for the length of the hurt animation, whatever the mode says.")
        .defaultValue(new SettingColor(255, 25, 25, 255))
        .build()
    );

    private final Setting<SettingColor> invisibleColor = sgColor.add(new ColorSetting.Builder()
        .name("invisible-color")
        .defaultValue(new SettingColor(255, 160, 0, 255))
        .build()
    );

    private final Setting<SettingColor> friendColor = sgColor.add(new ColorSetting.Builder()
        .name("friend-color")
        .defaultValue(new SettingColor(0, 255, 100, 255))
        .build()
    );

    /** Reused so a frame full of entities does not allocate a colour each. */
    private final Color line = new Color();
    private final Color side = new Color();

    public ESPl() {
        super(Categories.Render, "espl", "Boxes entities through walls, coloured by distance or health.");
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        double maxSq = maxDistance.get() * maxDistance.get();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (ignoreSelf.get() && entity == mc.player) continue;
            if (!entities.get().contains(entity.getType())) continue;

            double distanceSq = mc.player.distanceToSqr(entity);
            if (distanceSq > maxSq) continue;

            Color color = colorFor(entity, Math.sqrt(distanceSq));

            line.set(color);
            side.set(color.r, color.g, color.b, color.a / 5);

            // Interpolated, so the box sits on the entity rather than a tick behind it.
            Vec3 at = entity.getPosition(event.tickDelta);
            AABB box = entity.getBoundingBox().move(at.subtract(entity.position()));

            event.renderer.box(box, side, line, shapeMode.get(), 0);
        }
    }

    private Color colorFor(Entity entity, double distance) {
        if (entity instanceof LivingEntity living && living.hurtTime > 0) return hurtColor.get();
        if (entity.isInvisible()) return invisibleColor.get();
        if (entity instanceof Player player && !Friends.get().shouldAttack(player)) return friendColor.get();

        return switch (colorMode.get()) {
            case Static -> staticColor.get();

            case Distance -> Color.fromHsv(Math.clamp(distance / maxDistance.get(), 0, 1) * 120, 1, 1);

            case Health -> {
                if (!(entity instanceof LivingEntity living)) yield staticColor.get();

                yield Color.fromHsv(Math.clamp(living.getHealth() / Math.max(1, living.getMaxHealth()), 0, 1) * 120, 1, 1);
            }
        };
    }

    public enum ColorMode {
        /** Hue runs from red up close to green far away. */
        Distance,
        /** Hue runs from red at nearly dead to green at full. */
        Health,
        /** One colour for everything. */
        Static
    }
}
