/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Floats the amount of damage taken above whoever took it.
 *
 * <p>Nothing about a hit tells you how much it did. Health is sent for every entity you can see
 * though, so the difference between two ticks is the damage - which is exact for anything the server
 * keeps you updated on, and simply absent for anything it does not.
 *
 * <p>Useful for knowing whether an opponent is on their last hit without having to read a health bar
 * mid-fight.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class DamageParticles extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far away to keep showing numbers.")
        .defaultValue(32)
        .min(1)
        .sliderRange(4, 128)
        .build()
    );

    private final Setting<Integer> duration = sgGeneral.add(new IntSetting.Builder()
        .name("duration")
        .description("Ticks a number stays up.")
        .defaultValue(30)
        .min(1)
        .sliderRange(5, 100)
        .build()
    );

    private final Setting<Double> rise = sgGeneral.add(new DoubleSetting.Builder()
        .name("rise")
        .description("Blocks a number drifts upwards over its life.")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 4)
        .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Text size.")
        .defaultValue(1.5)
        .min(0.5)
        .sliderRange(0.5, 4)
        .build()
    );

    private final Setting<Boolean> healing = sgGeneral.add(new BoolSetting.Builder()
        .name("healing")
        .description("Show health gained as well as health lost.")
        .defaultValue(false)
        .build()
    );

    private final Setting<SettingColor> damageColor = sgGeneral.add(new ColorSetting.Builder()
        .name("damage-color")
        .defaultValue(new SettingColor(255, 60, 60, 255))
        .build()
    );

    private final Setting<SettingColor> healColor = sgGeneral.add(new ColorSetting.Builder()
        .name("heal-color")
        .defaultValue(new SettingColor(60, 255, 90, 255))
        .visible(healing::get)
        .build()
    );

    /** Last seen health per entity id, which is what a change is measured against. */
    private final Int2FloatMap lastHealth = new Int2FloatOpenHashMap();
    private final List<Number> numbers = new ArrayList<>();

    private static final Vector3d screen = new Vector3d();
    private final Color faded = new Color();

    public DamageParticles() {
        super(Categories.Render, "damage-particles", "Floats damage numbers above whoever took the hit.");
    }

    @Override
    public void onActivate() {
        lastHealth.clear();
        numbers.clear();
    }

    @Override
    public void onDeactivate() {
        lastHealth.clear();
        numbers.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;
            if (mc.player.distanceTo(living) > range.get()) continue;

            float health = living.getHealth();
            int id = living.getId();

            if (!lastHealth.containsKey(id)) {
                // First sighting is not a change. Recording it without a number stops an entity coming
                // into range from reading as a hit.
                lastHealth.put(id, health);
                continue;
            }

            float change = lastHealth.put(id, health) - health;

            if (change > 0.01f) numbers.add(new Number(living.getEyePosition(), change, false, duration.get()));
            else if (healing.get() && change < -0.01f) numbers.add(new Number(living.getEyePosition(), -change, true, duration.get()));
        }

        lastHealth.keySet().removeIf(id -> mc.level.getEntity(id) == null);
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        TextRenderer text = TextRenderer.get();

        for (Iterator<Number> it = numbers.iterator(); it.hasNext(); ) {
            Number number = it.next();

            number.age += event.frameTime * 20;
            if (number.age >= number.life) {
                it.remove();
                continue;
            }

            double progress = number.age / number.life;

            screen.set(number.pos.x, number.pos.y + rise.get() * progress, number.pos.z);
            if (!NametagUtils.to2D(screen, scale.get())) continue;

            SettingColor base = number.healed ? healColor.get() : damageColor.get();
            faded.set(base.r, base.g, base.b, (int) (base.a * (1 - progress)));

            String label = String.format("%.1f", number.amount);

            NametagUtils.begin(screen);
            text.beginBig(event.graphics);
            text.render(label, -text.getWidth(label) / 2, -text.getHeight(), faded, true);
            text.end();
            NametagUtils.end();
        }
    }

    private static class Number {
        final Vec3 pos;
        final float amount;
        final boolean healed;
        final double life;
        double age;

        Number(Vec3 pos, float amount, boolean healed, double life) {
            this.pos = pos;
            this.amount = amount;
            this.healed = healed;
            this.life = life;
        }
    }
}
