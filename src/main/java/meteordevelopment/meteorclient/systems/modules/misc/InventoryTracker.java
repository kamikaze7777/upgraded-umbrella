/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Keeps a running count of the items you care about on screen.
 *
 * <p>Blocks, gaps and pearls are the numbers a fight is decided by, and all three live in whichever
 * hotbar slot they landed in. Counting them by looking costs the moment you spend looking, so this
 * counts them for you and puts the totals in one place.
 *
 * <p>Nothing here is sent anywhere - it is your own inventory, counted.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class InventoryTracker extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<List<Item>> tracked = sgGeneral.add(new ItemListSetting.Builder()
        .name("items")
        .description("What to count.")
        .defaultValue(Items.ENDER_PEARL, Items.ENCHANTED_GOLDEN_APPLE, Items.OBSIDIAN, Items.TOTEM_OF_UNDYING)
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
        .defaultValue(140)
        .min(0)
        .sliderRange(0, 600)
        .build()
    );

    private final Setting<SettingColor> textColor = sgRender.add(new ColorSetting.Builder()
        .name("text-color")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );

    /** Recounted on a tick rather than per frame - the inventory does not change between frames. */
    private final Object2IntMap<Item> counts = new Object2IntOpenHashMap<>();

    public InventoryTracker() {
        super(Categories.Misc, "inventory-tracker", "Keeps a running count of chosen items on screen.");
    }

    @Override
    public void onDeactivate() {
        counts.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        counts.clear();

        // The whole inventory, not only the hotbar - what is in the back rows still counts towards how
        // long you can keep going.
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty() || !tracked.get().contains(stack.getItem())) continue;

            counts.mergeInt(stack.getItem(), stack.getCount(), Integer::sum);
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (tracked.get().isEmpty()) return;

        TextRenderer text = TextRenderer.get();
        text.begin(event.graphics);

        double row = y.get();

        for (Item item : tracked.get()) {
            // Zero is written out rather than skipped, so the rows never move around and the number you
            // are looking for stays in the same place.
            String label = counts.getInt(item) + "x " + item.getName(item.getDefaultInstance()).getString();

            text.render(label, x.get(), row, textColor.get(), true);
            row += text.getHeight() + 1;
        }

        text.end();
    }
}
