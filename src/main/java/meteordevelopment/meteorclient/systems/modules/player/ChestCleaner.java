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
 * Throws unwanted items out of an open container, one click at a time.
 *
 * <p>Emptying a chest of junk by hand is the slowest part of looting, and the time is spent standing
 * still with a screen open. This does the same clicks the player would, spaced out - a throw is a
 * normal container click with the throw button, so nothing here is a packet the server would not have
 * seen anyway.
 *
 * <p>Your own inventory screen is skipped: dropping your own gear because it is not on a list is a
 * mistake you cannot take back.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class ChestCleaner extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<Item>> items = sgGeneral.add(new ItemListSetting.Builder()
        .name("items")
        .description("The items the filter applies to.")
        .build()
    );

    private final Setting<ListMode> filter = sgGeneral.add(new EnumSetting.Builder<ListMode>()
        .name("filter")
        .description("Whether the list names what to throw or what to keep.")
        .defaultValue(ListMode.Whitelist)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks between throws. Too fast and the server's click rate limit cuts in.")
        .defaultValue(2)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Boolean> closeWhenDone = sgGeneral.add(new BoolSetting.Builder()
        .name("close-when-done")
        .description("Close the container once there is nothing left to throw.")
        .defaultValue(true)
        .build()
    );

    private int timer;

    public ChestCleaner() {
        super(Categories.Player, "chest-cleaner", "Throws unwanted items out of an open container.", "container-cleaner");
    }

    @Override
    public void onActivate() {
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        // The player's own inventory shares the container screen type but is never something to empty.
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen) || screen instanceof InventoryScreen) return;

        if (timer > 0) {
            timer--;
            return;
        }

        Slot slot = findUnwanted(screen);

        if (slot == null) {
            if (closeWhenDone.get()) mc.player.closeContainer();
            return;
        }

        // Throw button, whole stack. The container is the menu the screen is showing, so this is the
        // same click the player's own drop would produce.
        mc.gameMode.handleContainerInput(screen.getMenu().containerId, slot.index, 1, ContainerInput.THROW, mc.player);
        timer = delay.get();
    }

    /**
     * The first slot in the container half of the screen holding something the filter rejects. Slots
     * belonging to the player's own inventory are skipped by container identity rather than by index,
     * which holds across every container size.
     */
    private Slot findUnwanted(AbstractContainerScreen<?> screen) {
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container == mc.player.getInventory()) continue;

            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;

            if (filter.get().allows(items.get().contains(stack.getItem()))) return slot;
        }

        return null;
    }
}
