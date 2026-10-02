/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Leaves an expanding ring on the ground wherever someone jumps.
 *
 * <p>Purely cosmetic, with one side effect worth knowing: it marks where other players left the
 * ground, which stays readable for a moment after they are behind something.
 *
 * <p>A jump is detected by watching for upward movement from a player who was on the ground the tick
 * before, since nothing is sent to say a jump happened.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class JumpEffect extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> others = sgGeneral.add(new BoolSetting.Builder()
        .name("other-players")
        .description("Draw rings for everyone, not just you.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> duration = sgGeneral.add(new IntSetting.Builder()
        .name("duration")
        .description("Ticks a ring lasts.")
        .defaultValue(15)
        .min(1)
        .sliderRange(5, 60)
        .build()
    );

    private final Setting<Double> radius = sgGeneral.add(new DoubleSetting.Builder()
        .name("radius")
        .description("How wide a ring grows before it is gone.")
        .defaultValue(1.2)
        .min(0.1)
        .sliderRange(0.5, 5)
        .build()
    );

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .defaultValue(new SettingColor(120, 200, 255, 180))
        .build()
    );

    private final List<Ring> rings = new ArrayList<>();
    private final java.util.Map<Integer, Boolean> wasOnGround = new java.util.HashMap<>();
    private final Color faded = new Color();

    public JumpEffect() {
        super(Categories.Render, "jump-effect", "Leaves an expanding ring wherever a player jumps.");
    }

    @Override
    public void onDeactivate() {
        rings.clear();
        wasOnGround.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        for (Player player : mc.level.players()) {
            if (!others.get() && player != mc.player) continue;

            boolean onGround = player.onGround();
            Boolean before = wasOnGround.put(player.getId(), onGround);

            // Left the ground moving upwards: a jump, rather than walking off an edge.
            if (before != null && before && !onGround && player.getDeltaMovement().y > 0) {
                rings.add(new Ring(player.position(), duration.get()));
            }
        }

        // Players that left render distance would otherwise keep an entry each.
        wasOnGround.keySet().removeIf(id -> mc.level.getEntity(id) == null);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        for (Iterator<Ring> it = rings.iterator(); it.hasNext(); ) {
            Ring ring = it.next();

            ring.age += event.frameTime * 20;
            if (ring.age >= ring.life) {
                it.remove();
                continue;
            }

            double progress = ring.age / ring.life;
            double size = radius.get() * progress;

            faded.set(color.get().r, color.get().g, color.get().b, (int) (color.get().a * (1 - progress)));

            event.renderer.boxLines(
                ring.pos.x - size, ring.pos.y, ring.pos.z - size,
                ring.pos.x + size, ring.pos.y + 0.05, ring.pos.z + size,
                faded, 0
            );
        }
    }

    private static class Ring {
        final Vec3 pos;
        final double life;
        double age;

        Ring(Vec3 pos, double life) {
            this.pos = pos;
            this.life = life;
        }
    }
}
