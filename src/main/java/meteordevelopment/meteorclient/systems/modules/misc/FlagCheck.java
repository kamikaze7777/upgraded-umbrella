/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.phys.Vec3;

/**
 * Counts the times the server has corrected your position, and how far it moved you.
 *
 * <p>A correction is the server saying it does not believe where you said you were. It is the only
 * direct feedback a movement check gives you, so counting them is how you tell a setting that works
 * from one that is quietly being rejected the whole time.
 *
 * <p>The distance matters as much as the count: a correction of a few centimetres is ordinary
 * rounding, and one of several blocks is a check that actually fired. Both are shown, and small ones
 * can be filtered out.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class FlagCheck extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Boolean> notify = sgGeneral.add(new BoolSetting.Builder()
        .name("notify")
        .description("Say so in chat each time.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> minDistance = sgGeneral.add(new IntSetting.Builder()
        .name("min-distance")
        .description("Ignore corrections that moved you less than this, in centimetres.")
        .defaultValue(10)
        .min(0)
        .sliderRange(0, 500)
        .build()
    );

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Show the running count on screen.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> x = sgRender.add(new IntSetting.Builder()
        .name("x")
        .defaultValue(10)
        .min(0)
        .sliderRange(0, 600)
        .visible(render::get)
        .build()
    );

    private final Setting<Integer> y = sgRender.add(new IntSetting.Builder()
        .name("y")
        .defaultValue(200)
        .min(0)
        .sliderRange(0, 600)
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> textColor = sgRender.add(new ColorSetting.Builder()
        .name("text-color")
        .defaultValue(new SettingColor(255, 120, 120, 255))
        .visible(render::get)
        .build()
    );

    private int flags;
    private double lastDistance;

    /** Where the client thought it was, sampled each tick, which is what a correction is measured from. */
    private Vec3 believed = Vec3.ZERO;

    public FlagCheck() {
        super(Categories.Misc, "flag-check", "Counts the times the server has corrected your position.");
    }

    @Override
    public void onActivate() {
        flags = 0;
        lastDistance = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        believed = mc.player.position();
    }

    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        if (!(event.packet instanceof ClientboundPlayerPositionPacket packet)) return;

        // Reading the packet is safe from the network thread; everything that touches the client is not.
        Vec3 corrected = packet.change().position();
        Vec3 from = believed;

        mc.execute(() -> {
            double distance = corrected.distanceTo(from);
            if (distance * 100 < minDistance.get()) return;

            flags++;
            lastDistance = distance;

            if (notify.get()) warning("Corrected %.2f blocks. That is %d so far.", distance, flags);
        });
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!render.get()) return;

        TextRenderer text = TextRenderer.get();
        String label = String.format("flags: %d  last: %.2f", flags, lastDistance);

        text.begin(event.graphics);
        text.render(label, x.get(), y.get(), textColor.get(), true);
        text.end();
    }

    @Override
    public String getInfoString() {
        return Integer.toString(flags);
    }
}
