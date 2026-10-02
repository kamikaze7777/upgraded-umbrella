/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
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
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Expands a ring where someone pops a totem, so a pop is visible from across a fight.
 *
 * <p>A pop reaches the client as an entity event, not as a message, and the vanilla particles are
 * lost the moment anything else is happening. A ring that grows outwards and fades is readable from
 * further away and stays on screen long enough to count.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class TotemEffect extends Module {
    /** The entity event id the server sends for a totem saving someone. */
    private static final byte TOTEM_OF_UNDYING = 35;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> duration = sgGeneral.add(new IntSetting.Builder()
        .name("duration")
        .description("Ticks a ring lasts.")
        .defaultValue(20)
        .min(1)
        .sliderRange(5, 60)
        .build()
    );

    private final Setting<Double> radius = sgGeneral.add(new DoubleSetting.Builder()
        .name("radius")
        .description("How wide the ring grows before it is gone.")
        .defaultValue(2.5)
        .min(0.1)
        .sliderRange(0.5, 8)
        .build()
    );

    private final Setting<Boolean> selfOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("self-only")
        .description("Only draw your own pops.")
        .defaultValue(false)
        .build()
    );

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .defaultValue(new SettingColor(255, 215, 0, 200))
        .build()
    );

    private final List<Ring> rings = new ArrayList<>();
    private final Color faded = new Color();

    public TotemEffect() {
        super(Categories.Render, "totem-effect", "Draws an expanding ring where someone pops a totem.");
    }

    @Override
    public void onDeactivate() {
        rings.clear();
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (!(event.packet instanceof ClientboundEntityEventPacket packet)) return;
        if (packet.getEventId() != TOTEM_OF_UNDYING) return;

        // The packet arrives on the network thread and resolving it needs the world, so both the lookup
        // and the write happen back on the main thread.
        mc.execute(() -> {
            if (mc.level == null) return;

            Entity entity = packet.getEntity(mc.level);
            if (entity == null) return;
            if (selfOnly.get() && entity != mc.player) return;

            rings.add(new Ring(entity.position(), duration.get()));
        });
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        for (Iterator<Ring> it = rings.iterator(); it.hasNext(); ) {
            Ring ring = it.next();

            // Advanced by frame time rather than by tick so the ring grows smoothly at any frame rate.
            ring.age += event.frameTime * 20;
            if (ring.age >= ring.life) {
                it.remove();
                continue;
            }

            double progress = ring.age / ring.life;
            double size = radius.get() * progress;

            // Fades out as it grows, so the largest ring is also the faintest.
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
