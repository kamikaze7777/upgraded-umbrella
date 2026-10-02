/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the direction the server thinks you are facing, next to the one you are actually facing.
 *
 * <p>Anything that aims for you - an aura, a scaffold, a placement - sends a rotation the server then
 * believes, while your camera stays where you left it. The two are normally invisible to you, which
 * makes a module that is aiming somewhere absurd very hard to notice until something rejects it.
 *
 * <p>The server-side rotation is read from what Meteor last sent, so this shows the real value rather
 * than a re-derivation of it.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class Rotations extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> length = sgGeneral.add(new DoubleSetting.Builder()
        .name("length")
        .description("How far the line reaches.")
        .defaultValue(2)
        .min(0.5)
        .sliderRange(0.5, 8)
        .build()
    );

    private final Setting<Boolean> showClient = sgGeneral.add(new BoolSetting.Builder()
        .name("show-client")
        .description("Also draw where your camera is pointing, for comparison.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> serverColor = sgGeneral.add(new ColorSetting.Builder()
        .name("server-color")
        .description("The rotation the server has for you.")
        .defaultValue(new SettingColor(255, 60, 60, 220))
        .build()
    );

    private final Setting<SettingColor> clientColor = sgGeneral.add(new ColorSetting.Builder()
        .name("client-color")
        .description("Where your camera is pointing.")
        .defaultValue(new SettingColor(60, 160, 255, 220))
        .visible(showClient::get)
        .build()
    );

    public Rotations() {
        super(Categories.Render, "rotations", "Draws the rotation the server has for you next to your own.");
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        Vec3 eye = mc.player.getEyePosition(event.tickDelta);

        drawRay(event, eye, meteordevelopment.meteorclient.utils.player.Rotations.serverYaw, meteordevelopment.meteorclient.utils.player.Rotations.serverPitch, serverColor.get());

        if (showClient.get()) drawRay(event, eye, mc.player.getYRot(), mc.player.getXRot(), clientColor.get());
    }

    /** The same spherical-to-cartesian conversion the game uses to turn a look angle into a direction. */
    private void drawRay(Render3DEvent event, Vec3 from, double yaw, double pitch, SettingColor color) {
        double yawRad = yaw * Mth.DEG_TO_RAD;
        double pitchRad = pitch * Mth.DEG_TO_RAD;

        double horizontal = Math.cos(pitchRad);
        double x = -Math.sin(yawRad) * horizontal;
        double y = -Math.sin(pitchRad);
        double z = Math.cos(yawRad) * horizontal;

        double distance = length.get();

        event.renderer.line(from.x, from.y, from.z, from.x + x * distance, from.y + y * distance, from.z + z * distance, color);
    }
}
