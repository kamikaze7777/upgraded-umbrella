/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;

/**
 * Colours players by the role their equipment gives away in a murder-mystery game.
 *
 * <p>The roles are meant to be secret, and the server keeps the assignment to itself - but it cannot
 * hide the items, because everyone has to see them to see the game. Equipment is sent for every player
 * in view, so anyone carrying a sword is the murderer and anyone carrying a bow is the detective, and
 * both are known before either of them acts.
 *
 * <p>This works on any server running that format, since it reads the equipment rather than any
 * particular plugin's messages.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class MurderMystery extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far away to keep drawing.")
        .defaultValue(64)
        .min(1)
        .sliderRange(8, 256)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> murdererColor = sgGeneral.add(new ColorSetting.Builder()
        .name("murderer-color")
        .description("Anyone carrying a sword.")
        .defaultValue(new SettingColor(255, 40, 40, 200))
        .build()
    );

    private final Setting<SettingColor> detectiveColor = sgGeneral.add(new ColorSetting.Builder()
        .name("detective-color")
        .description("Anyone carrying a bow.")
        .defaultValue(new SettingColor(60, 120, 255, 200))
        .build()
    );

    private final SettingColor side = new SettingColor();

    public MurderMystery() {
        super(Categories.Render, "murder-mystery", "Colours players by the role their equipment gives away.");
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        double rangeSq = range.get() * range.get();

        for (Player player : mc.level.players()) {
            if (player == mc.player || !player.isAlive()) continue;
            if (mc.player.distanceToSqr(player) > rangeSq) continue;

            SettingColor color = roleColor(player);
            if (color == null) continue;

            // The fill is the role colour at a fraction of its opacity, so several players at once stay
            // readable rather than blending into one block of colour.
            side.set(color.r, color.g, color.b, color.a / 5);

            event.renderer.box(player.getBoundingBox(), side, color, shapeMode.get(), 0);
        }
    }

    private SettingColor roleColor(Player player) {
        // Both hands and the head are checked, because a role item is not always the one being held -
        // some versions of the game put the bow in the offhand.
        for (ItemStack stack : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem(), player.getItemBySlot(EquipmentSlot.HEAD)}) {
            if (stack.is(ItemTags.SWORDS)) return murdererColor.get();
            if (stack.is(ItemTags.BOW_ENCHANTABLE) || stack.is(ItemTags.CROSSBOW_ENCHANTABLE)) return detectiveColor.get();
        }

        return null;
    }
}
