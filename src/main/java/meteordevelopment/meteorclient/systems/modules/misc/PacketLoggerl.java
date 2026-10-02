/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Counts the packets going each way and shows the rates on screen.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code packet-logger}, kept alongside it
 * under the {@code l} suffix the rest of this port uses for name collisions. Meteor's logs packets to
 * chat or to a file, which is what you want when hunting one specific exchange.
 *
 * <p>This one is for the other question: what is this client actually sending? A module that spams a
 * hundred position packets a tick shows up here immediately, where in a log it is a hundred lines a
 * tick that hide everything else. It is the tool for finding out which module is going to get you
 * caught, which is why the rate is per second and on screen rather than in a file.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class PacketLoggerl extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Boolean> outgoing = sgGeneral.add(new BoolSetting.Builder()
        .name("outgoing")
        .description("Count what you send. This is the half that gets you noticed.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> incoming = sgGeneral.add(new BoolSetting.Builder()
        .name("incoming")
        .description("Count what you receive.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> rows = sgGeneral.add(new IntSetting.Builder()
        .name("rows")
        .description("How many of the busiest packet types to list.")
        .defaultValue(8)
        .min(1)
        .sliderRange(1, 30)
        .build()
    );

    private final Setting<Integer> x = sgRender.add(new IntSetting.Builder()
        .name("x")
        .defaultValue(10)
        .min(0)
        .sliderRange(0, 600)
        .build()
    );

    private final Setting<Integer> y = sgRender.add(new IntSetting.Builder()
        .name("y")
        .defaultValue(260)
        .min(0)
        .sliderRange(0, 600)
        .build()
    );

    private final Setting<SettingColor> textColor = sgRender.add(new ColorSetting.Builder()
        .name("text-color")
        .defaultValue(new SettingColor(200, 200, 255, 255))
        .build()
    );

    /** Counts for the second in progress, written from the network thread. */
    private final Object2IntMap<String> counting = new Object2IntOpenHashMap<>();

    /** The finished second's counts, which is what gets drawn. */
    private final List<Map.Entry<String, Integer>> shown = new ArrayList<>();

    private int tick;

    public PacketLoggerl() {
        super(Categories.Misc, "packet-loggerl", "Shows how many packets of each type are going each way, per second.");
    }

    @Override
    public void onActivate() {
        synchronized (counting) {
            counting.clear();
        }
        shown.clear();
        tick = 0;
    }

    @EventHandler
    private void onSend(PacketEvent.Sent event) {
        if (!outgoing.get()) return;

        count("→ " + event.packet.getClass().getSimpleName());
    }

    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        if (!incoming.get()) return;

        count("← " + event.packet.getClass().getSimpleName());
    }

    /** Called from both the network thread and the main one, hence the lock. */
    private void count(String name) {
        synchronized (counting) {
            counting.mergeInt(name, 1, Integer::sum);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        // Twenty ticks is a second, which is the unit the numbers mean anything in.
        if (++tick < 20) return;
        tick = 0;

        synchronized (counting) {
            shown.clear();

            counting.object2IntEntrySet().forEach(entry -> shown.add(Map.entry(entry.getKey(), entry.getIntValue())));
            counting.clear();
        }

        shown.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (shown.isEmpty()) return;

        TextRenderer text = TextRenderer.get();
        text.begin(event.graphics);

        double row = y.get();

        for (int i = 0; i < Math.min(rows.get(), shown.size()); i++) {
            Map.Entry<String, Integer> entry = shown.get(i);

            text.render(entry.getValue() + "/s  " + entry.getKey(), x.get(), row, textColor.get(), true);
            row += text.getHeight() + 1;
        }

        text.end();
    }

    @Override
    public String getInfoString() {
        int total = 0;
        for (Map.Entry<String, Integer> entry : shown) total += entry.getValue();

        return total == 0 ? null : total + "/s";
    }
}
