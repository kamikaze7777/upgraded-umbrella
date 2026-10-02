/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Requeues you into another game by pressing whatever the server put on the screen.
 *
 * <p>Every server does this differently - a command, a compass, a menu - so rather than shipping a
 * preset per server this matches on the text. A menu whose title contains what you asked for gets the
 * first item whose name contains what you asked for clicked, which covers almost all of them and does
 * not go stale when a server changes its layout.
 *
 * <p>Both halves of the matching are substrings and case insensitive, because titles and item names on
 * these menus are usually wrapped in colour codes and decoration.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class AutoQueue extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> command = sgGeneral.add(new StringSetting.Builder()
        .name("command")
        .description("Sent when no menu is open. Leave empty to only work menus.")
        .defaultValue("play")
        .build()
    );

    private final Setting<String> screenTitle = sgGeneral.add(new StringSetting.Builder()
        .name("screen-title")
        .description("Only act on menus whose title contains this. Empty means any menu.")
        .defaultValue("")
        .build()
    );

    private final Setting<String> itemName = sgGeneral.add(new StringSetting.Builder()
        .name("item-name")
        .description("Click the first item whose name contains this.")
        .defaultValue("play again")
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks between actions. Slow enough not to trip a command rate limit.")
        .defaultValue(40)
        .min(1)
        .sliderRange(10, 200)
        .build()
    );

    private final Setting<Boolean> pauseInChat = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-in-chat")
        .description("Do nothing while the chat box is open, so it cannot interrupt you typing.")
        .defaultValue(true)
        .build()
    );

    private int timer;

    public AutoQueue() {
        super(Categories.Player, "auto-queue", "Requeues you by pressing whatever the server puts on screen.", "auto-play");
    }

    @Override
    public void onActivate() {
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (timer > 0) {
            timer--;
            return;
        }

        if (mc.gui.screen() instanceof AbstractContainerScreen<?> screen) {
            if (!matches(screen.getTitle().getString(), screenTitle.get())) return;

            Slot slot = findButton(screen);
            if (slot == null) return;

            mc.gameMode.handleContainerInput(screen.getMenu().containerId, slot.index, 0, ContainerInput.PICKUP, mc.player);
            timer = delay.get();
            return;
        }

        if (pauseInChat.get() && mc.gui.screen() != null) return;
        if (command.get().isBlank() || mc.getConnection() == null) return;

        mc.getConnection().sendCommand(command.get());
        timer = delay.get();
    }

    private Slot findButton(AbstractContainerScreen<?> screen) {
        for (Slot slot : screen.getMenu().slots) {
            // Only the menu's own slots; your inventory is not what the server put the button in.
            if (slot.container == mc.player.getInventory()) continue;

            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;

            if (matches(stack.getHoverName().getString(), itemName.get())) return slot;
        }

        return null;
    }

    private boolean matches(String text, String wanted) {
        return wanted.isBlank() || text.toLowerCase().contains(wanted.toLowerCase());
    }
}
