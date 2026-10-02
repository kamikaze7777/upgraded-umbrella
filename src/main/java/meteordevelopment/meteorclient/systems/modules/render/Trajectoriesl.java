/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws where what you are holding will land, and marks what it would hit.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code trajectories}, kept alongside it
 * under the {@code l} suffix the rest of this port uses for name collisions. Meteor's also simulates
 * other players' shots and every projectile in flight; this one draws only yours, and puts the effort
 * into the end of the arc instead.
 *
 * <p>The landing point is what you are actually aiming with - the curve between here and there tells
 * you very little, and an arc that ends in a box on the block it hits is readable at a glance where a
 * line fading into terrain is not. Entities the arc passes through are marked too, since a pearl that
 * clips a mob on the way is a pearl that has gone nowhere.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class Trajectoriesl extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> steps = sgGeneral.add(new IntSetting.Builder()
        .name("steps")
        .description("How many ticks of flight to simulate before giving up.")
        .defaultValue(200)
        .min(10)
        .sliderRange(20, 600)
        .build()
    );

    private final Setting<Boolean> markLanding = sgRender.add(new BoolSetting.Builder()
        .name("mark-landing")
        .description("Box the block the arc ends on.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> markEntities = sgRender.add(new BoolSetting.Builder()
        .name("mark-entities")
        .description("Box anything the arc would pass through on the way.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .defaultValue(new SettingColor(255, 255, 255, 180))
        .build()
    );

    private final Setting<SettingColor> landingColor = sgRender.add(new ColorSetting.Builder()
        .name("landing-color")
        .defaultValue(new SettingColor(120, 200, 255, 200))
        .build()
    );

    private final Setting<SettingColor> hitColor = sgRender.add(new ColorSetting.Builder()
        .name("hit-color")
        .description("Something the arc would pass through before it lands.")
        .defaultValue(new SettingColor(255, 60, 60, 200))
        .build()
    );

    public Trajectoriesl() {
        super(Categories.Render, "trajectoriesl", "Draws where what you are holding will land, and what it would hit.");
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        Launch launch = launchFor(mc.player.getMainHandItem());
        if (launch == null) launch = launchFor(mc.player.getOffhandItem());
        if (launch == null) return;

        simulate(event, launch);
    }

    /**
     * Steps the projectile's own physics forward, drawing as it goes, and stops at the first thing it
     * would hit - a block, an entity, or the end of the search.
     */
    private void simulate(Render3DEvent event, Launch launch) {
        Vec3 position = mc.player.getEyePosition();
        Vec3 velocity = mc.player.getLookAngle().scale(launch.speed());

        for (int tick = 0; tick < steps.get(); tick++) {
            Vec3 next = position.add(velocity);

            if (markEntities.get()) {
                Entity hit = entityAlong(position, next);

                if (hit != null) {
                    event.renderer.line(position.x, position.y, position.z, next.x, next.y, next.z, lineColor.get());
                    event.renderer.box(hit.getBoundingBox(), hitColor.get(), hitColor.get(), shapeMode.get(), 0);
                    return;
                }
            }

            BlockPos block = BlockPos.containing(next);

            if (!mc.level.getBlockState(block).getCollisionShape(mc.level, block).isEmpty()) {
                event.renderer.line(position.x, position.y, position.z, next.x, next.y, next.z, lineColor.get());

                if (markLanding.get()) event.renderer.box(block, landingColor.get(), landingColor.get(), shapeMode.get(), 0);
                return;
            }

            event.renderer.line(position.x, position.y, position.z, next.x, next.y, next.z, lineColor.get());

            position = next;
            velocity = velocity.scale(launch.drag()).subtract(0, launch.gravity(), 0);

            if (position.y < mc.level.getMinY()) return;
        }
    }

    /** The first entity the segment passes through, ignoring you. */
    private Entity entityAlong(Vec3 from, Vec3 to) {
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player) continue;

            AABB box = entity.getBoundingBox().inflate(0.3);
            if (box.clip(from, to).isPresent()) return entity;
        }

        return null;
    }

    /**
     * How fast the held item throws and how hard its projectile falls. A bow is the odd one out, since
     * how far it is drawn decides the speed.
     */
    private Launch launchFor(ItemStack stack) {
        if (stack.is(Items.BOW)) {
            float charge = net.minecraft.world.item.BowItem.getPowerForTime(mc.player.getTicksUsingItem());
            return charge <= 0.1f ? null : new Launch(charge * 3, 0.05, 0.99);
        }

        if (stack.is(Items.CROSSBOW)) return new Launch(3.15, 0.05, 0.99);
        if (stack.is(Items.TRIDENT)) return new Launch(2.5, 0.05, 0.99);
        if (stack.is(Items.ENDER_PEARL) || stack.is(Items.SNOWBALL) || stack.is(Items.EGG)) return new Launch(1.5, 0.03, 0.99);
        if (stack.is(Items.EXPERIENCE_BOTTLE)) return new Launch(0.7, 0.07, 0.99);
        if (stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION)) return new Launch(0.5, 0.05, 0.99);

        return null;
    }

    /** The three numbers that decide an arc: how fast it leaves, how hard it falls, what it keeps. */
    private record Launch(double speed, double gravity, double drag) {
    }
}
