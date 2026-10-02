/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
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
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * Draws a line from you to every entity worth knowing about.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code tracers}, kept alongside it under
 * the {@code l} suffix the rest of this port uses for name collisions. What it brings is the colouring:
 * rather than one colour per entity category, the line itself carries a number.
 *
 * <p><b>Distance</b> runs the hue around as a target gets closer, so a glance at the colour tells you
 * roughly how far away something is without reading anything. <b>Health</b> does the same with what is
 * left of a target, which is the number that decides whether a fight is worth starting.
 *
 * <p>The line runs from foot to head rather than from you to them, which is how LiquidBounce draws it -
 * a vertical stroke at the target reads as a position, where a line across the screen reads as a
 * direction and covers everything it crosses.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class Tracersl extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColor = settings.createGroup("Color");

    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("What to draw a line to.")
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

    private final Setting<Boolean> ignoreSelf = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-self")
        .description("Skip your own body, which matters in third person and freecam.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ColorMode> colorMode = sgColor.add(new EnumSetting.Builder<ColorMode>()
        .name("color-mode")
        .description("What the line's colour is telling you.")
        .defaultValue(ColorMode.Distance)
        .build()
    );

    private final Setting<SettingColor> staticColor = sgColor.add(new ColorSetting.Builder()
        .name("color")
        .defaultValue(new SettingColor(0, 160, 255, 255))
        .visible(() -> colorMode.get() == ColorMode.Static)
        .build()
    );

    private final Setting<SettingColor> friendColor = sgColor.add(new ColorSetting.Builder()
        .name("friend-color")
        .description("Friends are always this colour, whatever the mode.")
        .defaultValue(new SettingColor(0, 100, 255, 255))
        .build()
    );

    /** Reused so a frame with a hundred entities in it does not allocate a hundred colours. */
    private final Color color = new Color();

    public Tracersl() {
        super(Categories.Render, "tracersl", "Lines to nearby entities, coloured by distance or health.");
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        double maxSq = maxDistance.get() * maxDistance.get();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (ignoreSelf.get() && entity == mc.player) continue;
            if (!entities.get().contains(entity.getType())) continue;

            double distanceSq = mc.player.distanceToSqr(entity);
            if (distanceSq > maxSq) continue;

            Vec3 at = entity.getPosition(event.tickDelta);

            event.renderer.line(at.x, at.y, at.z, at.x, at.y + entity.getBbHeight(), at.z, colorFor(entity, Math.sqrt(distanceSq)));
        }
    }

    private Color colorFor(Entity entity, double distance) {
        if (entity instanceof Player player && !Friends.get().shouldAttack(player)) return friendColor.get();

        return switch (colorMode.get()) {
            case Static -> staticColor.get();

            // Red up close through to green at the edge of the range. The hue is the distance, so a
            // line changing colour is something closing on you.
            case Distance -> {
                double fraction = Math.clamp(distance / maxDistance.get(), 0, 1);
                yield color.set(Color.fromHsv(fraction * 120, 1, 1));
            }

            case Health -> {
                if (!(entity instanceof LivingEntity living)) yield staticColor.get();

                double fraction = Math.clamp(living.getHealth() / Math.max(1, living.getMaxHealth()), 0, 1);
                yield color.set(Color.fromHsv(fraction * 120, 1, 1));
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
