/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Leaves a trail behind every player, not only you, and fades it out over time.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code breadcrumbs}, kept alongside it
 * under the {@code l} suffix the rest of this port uses for name collisions. Meteor's draws your own
 * path; the two additions here change what it is for.
 *
 * <p>Drawing <b>other players</b> turns it from a record of where you have been into a record of where
 * everyone else has - which way someone left, whether they doubled back, where they came from. And
 * <b>fading</b> by age rather than by length means the trail says when as well as where: a bright
 * section is seconds old and a faint one is a minute.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class Breadcrumbsl extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> onlyOwn = sgGeneral.add(new BoolSetting.Builder()
        .name("only-own")
        .description("Draw only your own trail.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> lifetime = sgGeneral.add(new IntSetting.Builder()
        .name("lifetime")
        .description("Ticks a point stays on the trail.")
        .defaultValue(200)
        .min(10)
        .sliderRange(20, 1200)
        .build()
    );

    private final Setting<Boolean> fade = sgGeneral.add(new BoolSetting.Builder()
        .name("fade")
        .description("Fade a point out as it ages, so the trail shows when as well as where.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> height = sgGeneral.add(new DoubleSetting.Builder()
        .name("height")
        .description("How far above the feet to draw the line.")
        .defaultValue(0.5)
        .min(0)
        .sliderRange(0, 2)
        .build()
    );

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .defaultValue(new SettingColor(70, 119, 255, 200))
        .build()
    );

    /** One trail per entity id, oldest point first. */
    private final Int2ObjectMap<Deque<Point>> trails = new Int2ObjectOpenHashMap<>();
    private final Color faded = new Color();

    public Breadcrumbsl() {
        super(Categories.Render, "breadcrumbsl", "Leaves a fading trail behind every player, not just you.", "player-trails");
    }

    @Override
    public void onDeactivate() {
        trails.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        for (Player player : mc.level.players()) {
            if (onlyOwn.get() && player != mc.player) continue;

            Deque<Point> trail = trails.computeIfAbsent(player.getId(), _ -> new ArrayDeque<>());

            // Only record a point when the player has actually moved. Standing still would otherwise
            // pile a hundred identical points into one spot and fade the whole trail from there.
            Point last = trail.peekLast();
            if (last == null || last.pos.distanceToSqr(player.position()) > 0.01) {
                trail.addLast(new Point(player.position(), 0));
            }
        }

        // Age everything, drop what has expired, and forget players who have gone.
        trails.int2ObjectEntrySet().removeIf(entry -> {
            Deque<Point> trail = entry.getValue();

            for (Point point : trail) point.age++;
            while (!trail.isEmpty() && trail.peekFirst().age > lifetime.get()) trail.removeFirst();

            return trail.isEmpty() && mc.level.getEntity(entry.getIntKey()) == null;
        });
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        for (Deque<Point> trail : trails.values()) {
            Point previous = null;

            for (Point point : trail) {
                if (previous != null) draw(event, previous, point);
                previous = point;
            }
        }
    }

    private void draw(Render3DEvent event, Point from, Point to) {
        SettingColor base = color.get();

        if (fade.get()) {
            double remaining = 1 - Math.clamp((double) to.age / lifetime.get(), 0, 1);
            faded.set(base.r, base.g, base.b, (int) (base.a * remaining));
        } else {
            faded.set(base);
        }

        event.renderer.line(
            from.pos.x, from.pos.y + height.get(), from.pos.z,
            to.pos.x, to.pos.y + height.get(), to.pos.z,
            faded
        );
    }

    private static class Point {
        final Vec3 pos;
        int age;

        Point(Vec3 pos, int age) {
            this.pos = pos;
            this.age = age;
        }
    }
}
