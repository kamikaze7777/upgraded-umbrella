/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Draws a top-down dot for every player around you, rotated so up is the way you are facing.
 *
 * <p>Tracers and boxes only help for something you can point at. A radar answers a different question -
 * whether anyone is behind you - and answers it without having to turn round to ask.
 *
 * <p>Positions come from the same entity list everything else uses, so the range is whatever the server
 * sends you and nothing more.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class Radar extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("The distance the edge of the dial represents.")
        .defaultValue(64)
        .min(4)
        .sliderRange(16, 256)
        .build()
    );

    private final Setting<Boolean> friends = sgGeneral.add(new BoolSetting.Builder()
        .name("friends")
        .description("Show friends too, in their own colour.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> size = sgRender.add(new IntSetting.Builder()
        .name("size")
        .description("Width of the dial in pixels.")
        .defaultValue(120)
        .min(40)
        .sliderRange(40, 400)
        .build()
    );

    private final Setting<Integer> x = sgRender.add(new IntSetting.Builder()
        .name("x")
        .description("Distance from the left edge of the screen.")
        .defaultValue(10)
        .min(0)
        .sliderRange(0, 600)
        .build()
    );

    private final Setting<Integer> y = sgRender.add(new IntSetting.Builder()
        .name("y")
        .description("Distance from the top edge of the screen.")
        .defaultValue(10)
        .min(0)
        .sliderRange(0, 400)
        .build()
    );

    private final Setting<Integer> dotSize = sgRender.add(new IntSetting.Builder()
        .name("dot-size")
        .defaultValue(4)
        .min(1)
        .sliderRange(1, 12)
        .build()
    );

    private final Setting<SettingColor> background = sgRender.add(new ColorSetting.Builder()
        .name("background")
        .defaultValue(new SettingColor(0, 0, 0, 80))
        .build()
    );

    private final Setting<SettingColor> playerColor = sgRender.add(new ColorSetting.Builder()
        .name("player-color")
        .defaultValue(new SettingColor(255, 60, 60, 220))
        .build()
    );

    private final Setting<SettingColor> friendColor = sgRender.add(new ColorSetting.Builder()
        .name("friend-color")
        .defaultValue(new SettingColor(60, 255, 90, 220))
        .visible(friends::get)
        .build()
    );

    public Radar() {
        super(Categories.Render, "radar", "Draws a top-down dial of the players around you.");
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        int half = size.get() / 2;
        double centerX = x.get() + half;
        double centerY = y.get() + half;

        Renderer2D.COLOR.begin();
        Renderer2D.COLOR.quad(x.get(), y.get(), size.get(), size.get(), background.get());

        // Yaw is measured clockwise from south; negating it and turning it into radians is what makes
        // the dial point where the player is looking rather than at a fixed compass direction.
        float yaw = mc.player.getYRot() * Mth.DEG_TO_RAD;
        double sin = Mth.sin(yaw);
        double cos = Mth.cos(yaw);

        double scale = half / range.get();

        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;

            boolean friend = !Friends.get().shouldAttack(player);
            if (friend && !friends.get()) continue;

            double dx = player.getX() - mc.player.getX();
            double dz = player.getZ() - mc.player.getZ();
            if (dx * dx + dz * dz > range.get() * range.get()) continue;

            // Rotate into the player's frame: forward is up the screen, right is right.
            double right = dx * cos + dz * sin;
            double forward = dz * cos - dx * sin;

            double px = centerX + right * scale;
            double py = centerY - forward * scale;

            Renderer2D.COLOR.quad(px - dotSize.get() / 2.0, py - dotSize.get() / 2.0, dotSize.get(), dotSize.get(), friend ? friendColor.get() : playerColor.get());
        }

        Renderer2D.COLOR.render();
    }
}
