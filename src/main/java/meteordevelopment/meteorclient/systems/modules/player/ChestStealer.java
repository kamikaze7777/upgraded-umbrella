/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.ListMode;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Empties an open container into your inventory, one click at a time.
 *
 * <p>Each move is an ordinary shift-click, which is what makes this safe to space out - the server
 * sees the same request it would have got from a player, and stopping halfway leaves nothing in a bad
 * state. Speed is the only thing being changed, and the delay exists because clicking faster than a
 * person can is both the giveaway and what the server's click rate limit is watching for.
 *
 * <p>Your own inventory screen is skipped, since there is nowhere for the items to go.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class ChestStealer extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<Item>> items = sgGeneral.add(new ItemListSetting.Builder()
        .name("items")
        .description("The items the filter applies to.")
        .build()
    );

    private final Setting<ListMode> filter = sgGeneral.add(new EnumSetting.Builder<ListMode>()
        .name("filter")
        .description("Whether the list names what to take or what to leave.")
        .defaultValue(ListMode.Blacklist)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks between moves.")
        .defaultValue(2)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Boolean> closeWhenDone = sgGeneral.add(new BoolSetting.Builder()
        .name("close-when-done")
        .description("Close the container once there is nothing left worth taking.")
        .defaultValue(true)
        .build()
    );

    private int timer;

    public ChestStealer() {
        super(Categories.Player, "chest-stealer", "Empties an open container into your inventory.");
    }

    @Override
    public void onActivate() {
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen) || screen instanceof InventoryScreen) return;

        if (timer > 0) {
            timer--;
            return;
        }

        Slot slot = findWanted(screen);

        if (slot == null) {
            if (closeWhenDone.get()) mc.player.closeContainer();
            return;
        }

        // Shift-click: the destination is decided by the server, so a full inventory simply refuses the
        // move rather than putting the item somewhere unexpected.
        mc.gameMode.handleContainerInput(screen.getMenu().containerId, slot.index, 0, ContainerInput.QUICK_MOVE, mc.player);
        timer = delay.get();
    }

    /**
     * The first slot in the container half of the screen holding something worth taking. Slots are
     * identified by which container backs them rather than by index, which holds at every container size.
     */
    private Slot findWanted(AbstractContainerScreen<?> screen) {
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container == mc.player.getInventory()) continue;

            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;

            if (filter.get().allows(items.get().contains(stack.getItem()))) return slot;
        }

        return null;
    }
}
