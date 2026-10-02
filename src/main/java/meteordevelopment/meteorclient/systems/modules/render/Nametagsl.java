/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3d;

/**
 * Labels players with a tag whose size does not shrink into nothing at range.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code nametags}, kept alongside it under
 * the {@code l} suffix the rest of this port uses for name collisions. Meteor's carries far more
 * information; the one thing it does differently is the size.
 *
 * <p>A nametag drawn in world space shrinks with distance, which is correct and useless - the tags you
 * most want to read are the far ones. This holds the size flat out to a chosen distance and only then
 * lets it fall away, so a player across the map is still legible while one standing next to you does
 * not fill the screen.
 *
 * <p>The equipment row is the other half: what someone is holding and wearing decides whether a fight
 * is worth having, and it is the one thing you cannot read off a name.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class Nametagsl extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Double> maxDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-distance")
        .description("How far away to keep labelling.")
        .defaultValue(128)
        .min(1)
        .sliderRange(8, 512)
        .build()
    );

    private final Setting<Boolean> health = sgGeneral.add(new BoolSetting.Builder()
        .name("health")
        .description("Show how much health is left.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> equipment = sgGeneral.add(new BoolSetting.Builder()
        .name("equipment")
        .description("List what the player is holding and wearing under the name.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> scale = sgRender.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Text size.")
        .defaultValue(1.2)
        .min(0.25)
        .sliderRange(0.25, 4)
        .build()
    );

    private final Setting<Double> flatUntil = sgRender.add(new DoubleSetting.Builder()
        .name("flat-until")
        .description("Hold the tag at full size out to this far, then let it shrink.")
        .defaultValue(60)
        .min(0)
        .sliderRange(0, 200)
        .build()
    );

    private final Setting<SettingColor> background = sgRender.add(new ColorSetting.Builder()
        .name("background")
        .defaultValue(new SettingColor(0, 0, 0, 120))
        .build()
    );

    private final Setting<SettingColor> nameColor = sgRender.add(new ColorSetting.Builder()
        .name("name-color")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );

    private final Setting<SettingColor> friendColor = sgRender.add(new ColorSetting.Builder()
        .name("friend-color")
        .defaultValue(new SettingColor(0, 255, 100, 255))
        .build()
    );

    private static final Vector3d screen = new Vector3d();
    private final Color healthColor = new Color();

    public Nametagsl() {
        super(Categories.Render, "nametagsl", "Player nametags that stay legible at range, with equipment.");
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        TextRenderer text = TextRenderer.get();

        for (Player player : mc.level.players()) {
            if (player == mc.player || !player.isAlive()) continue;

            double distance = mc.player.distanceTo(player);
            if (distance > maxDistance.get()) continue;

            screen.set(player.getX(), player.getY() + player.getBbHeight() + 0.5, player.getZ());

            // Held flat out to the chosen distance, then scaled down in proportion to how far past it
            // the target is. That is the whole difference from a world-space tag.
            double size = scale.get();
            if (distance > flatUntil.get() && flatUntil.get() > 0) size *= flatUntil.get() / distance;

            if (!NametagUtils.to2D(screen, size)) continue;

            draw(event, text, player);
        }
    }

    private void draw(Render2DEvent event, TextRenderer text, Player player) {
        String name = player.getName().getString();
        String label = health.get() ? name + " " + (int) player.getHealth() : name;

        NametagUtils.begin(screen);
        text.beginBig(event.graphics);

        double width = text.getWidth(label);
        double half = width / 2;

        Renderer2D.COLOR.begin();
        Renderer2D.COLOR.quad(-half - 2, -text.getHeight() - 1, width + 4, text.getHeight() + 2, background.get());
        Renderer2D.COLOR.render();

        Color color = Friends.get().shouldAttack(player) ? nameColor.get() : friendColor.get();

        double x = text.render(name, -half, -text.getHeight(), color, true);

        if (health.get()) {
            // Red at nearly dead through to green at full, so the number carries its own urgency.
            double fraction = Math.clamp(player.getHealth() / Math.max(1, player.getMaxHealth()), 0, 1);
            healthColor.set(Color.fromHsv(fraction * 120, 1, 1));

            text.render(" " + (int) player.getHealth(), x, -text.getHeight(), healthColor, true);
        }

        if (equipment.get()) drawEquipment(text, player);

        text.end();
        NametagUtils.end();
    }

    /** One line under the name listing what is held and worn, skipping the empty slots. */
    private void drawEquipment(TextRenderer text, Player player) {
        StringBuilder line = new StringBuilder();

        for (ItemStack stack : new ItemStack[]{
            player.getMainHandItem(), player.getOffhandItem(),
            player.getItemBySlot(EquipmentSlot.HEAD), player.getItemBySlot(EquipmentSlot.CHEST),
            player.getItemBySlot(EquipmentSlot.LEGS), player.getItemBySlot(EquipmentSlot.FEET)
        }) {
            if (stack.isEmpty()) continue;

            if (!line.isEmpty()) line.append(' ');
            line.append(stack.getHoverName().getString());
        }

        if (line.isEmpty()) return;

        text.render(line.toString(), -text.getWidth(line.toString()) / 2, 1, nameColor.get(), true);
    }
}
