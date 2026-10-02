/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.glfw.GLFW;

/**
 * Moves whatever the cursor passes over while shift and the left button are both held.
 *
 * <p>Shift-clicking a chest empty is the same action twenty times over, and the only reason it takes
 * twenty clicks is that the game asks for a click per slot. Holding the button and sweeping the cursor
 * across the grid asks for the same moves at the same rate, which is why the delay is a setting rather
 * than zero.
 *
 * <p>A slot is moved once per sweep. Without that, the cursor resting over one slot for two ticks would
 * move whatever slid into it next.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class ItemScroller extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks between moves. Zero is a good way to hit the server's click rate limit.")
        .defaultValue(2)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );

    /** The slot moved last, so holding the cursor still does not keep moving whatever lands there. */
    private int lastSlot = -1;
    private int timer;

    public ItemScroller() {
        super(Categories.Misc, "item-scroller", "Moves items by sweeping the cursor with shift and the left button held.");
    }

    @Override
    public void onActivate() {
        lastSlot = -1;
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)) {
            lastSlot = -1;
            return;
        }

        // Shift plus the left button held is what makes this a sweep rather than a click, and it is
        // also what keeps it out of the way of ordinary inventory use.
        if (!mc.hasShiftDown() || GLFW.glfwGetMouseButton(mc.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
            lastSlot = -1;
            return;
        }

        if (timer > 0) {
            timer--;
            return;
        }

        Slot slot = ((meteordevelopment.meteorclient.mixin.AbstractContainerScreenAccessor) screen).meteor$getHoveredSlot();
        if (slot == null || slot.getItem().isEmpty() || slot.index == lastSlot) return;

        // The same shift-click the player would have made, so the destination is the server's choice and
        // a full inventory simply refuses rather than putting the item somewhere unexpected.
        mc.gameMode.handleContainerInput(screen.getMenu().containerId, slot.index, 0, ContainerInput.QUICK_MOVE, mc.player);

        lastSlot = slot.index;
        timer = delay.get();
    }
}
