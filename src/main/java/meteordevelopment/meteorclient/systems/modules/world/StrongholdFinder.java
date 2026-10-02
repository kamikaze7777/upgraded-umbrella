/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.world;

import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.phys.Vec3;

/**
 * Works out where the stronghold is from two thrown ender eyes.
 *
 * <p>An eye flies towards the stronghold, so the line from where it was thrown along its direction of
 * travel passes through it. One line narrows the answer to a bearing; two thrown from different places
 * cross at the stronghold, which is the whole of the arithmetic here.
 *
 * <p>Accuracy comes from the angle between the two throws. Two lines that are nearly parallel cross a
 * long way off with a large error, so the second throw wants to be a few hundred blocks to the side of
 * the first rather than further along the same bearing.
 *
 * <p>Nothing is asked of the server that a player throwing eyes does not already ask. The eye's flight
 * is public, and this only measures it.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class StrongholdFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> render = sgGeneral.add(new BoolSetting.Builder()
        .name("render")
        .description("Draw a marker at the estimate.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .defaultValue(ShapeMode.Both)
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgGeneral.add(new ColorSetting.Builder()
        .name("side-color")
        .defaultValue(new SettingColor(180, 100, 255, 50))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgGeneral.add(new ColorSetting.Builder()
        .name("line-color")
        .defaultValue(new SettingColor(180, 100, 255, 200))
        .visible(render::get)
        .build()
    );

    /** The last two throws, as a point and a horizontal direction. */
    private Throw first, second;
    private Vec3 estimate;

    public StrongholdFinder() {
        super(Categories.World, "stronghold-finder", "Triangulates the stronghold from two thrown ender eyes.");
    }

    @Override
    public void onActivate() {
        reset();
    }

    private void reset() {
        first = second = null;
        estimate = null;
    }

    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        if (!(event.entity instanceof EyeOfEnder eye)) return;

        Vec3 direction = eye.getDeltaMovement();

        // A freshly spawned eye can arrive before its motion does; without a direction there is no
        // bearing to record.
        if (direction.horizontalDistanceSqr() < 1.0E-6) return;

        Throw thrown = new Throw(eye.position(), direction.normalize());

        // The two throws have to be from different places to cross anywhere useful, so a second eye
        // thrown from the same spot replaces the first rather than being treated as new information.
        if (first == null || first.origin.distanceTo(thrown.origin) < 8) {
            first = thrown;
            second = null;
            estimate = null;
            return;
        }

        second = thrown;
        estimate = intersect(first, second);

        if (estimate == null) {
            warning("Those two throws are too close to parallel to cross usefully. Move further to the side.");
            return;
        }

        info("Stronghold is near %d, %d - about %d blocks away.",
            (int) estimate.x, (int) estimate.z, (int) Math.sqrt(mc.player.distanceToSqr(estimate.x, mc.player.getY(), estimate.z)));
    }

    /**
     * Where the two bearings cross, on the horizontal plane. Returns null when they are close enough to
     * parallel that the crossing point is meaningless.
     */
    private Vec3 intersect(Throw a, Throw b) {
        double denominator = a.direction.x * b.direction.z - a.direction.z * b.direction.x;

        // The cross product of the two bearings. Near zero means they point the same way, and the answer
        // would be somewhere out past the world border.
        if (Math.abs(denominator) < 1.0E-4) return null;

        double dx = b.origin.x - a.origin.x;
        double dz = b.origin.z - a.origin.z;

        double t = (dx * b.direction.z - dz * b.direction.x) / denominator;

        // A negative distance along the first bearing means they cross behind the throw, which is not
        // where the eye was heading.
        if (t < 0) return null;

        return new Vec3(a.origin.x + a.direction.x * t, mc.player.getY(), a.origin.z + a.direction.z * t);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || estimate == null) return;

        // Drawn as a tall column rather than a box, so it stays visible from a distance and from below.
        event.renderer.box(
            estimate.x - 1, mc.level.getMinY(), estimate.z - 1,
            estimate.x + 1, mc.level.getMaxY(), estimate.z + 1,
            sideColor.get(), lineColor.get(), shapeMode.get(), 0
        );
    }

    @Override
    public String getInfoString() {
        if (estimate != null) return (int) estimate.x + ", " + (int) estimate.z;

        return first == null ? "throw one" : "throw again, further sideways";
    }

    private record Throw(Vec3 origin, Vec3 direction) {
    }
}
