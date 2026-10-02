/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.world;

import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Switches to whichever tool breaks the block fastest, judged by how long it would actually take.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code auto-tool}, kept alongside it
 * under the {@code l} suffix the rest of this port uses for name collisions. Meteor's chooses by tool
 * type with preferences for silk touch and fortune; this one asks each tool in the hotbar what its
 * destroy speed against <i>this</i> block state is and takes the highest.
 *
 * <p>That gets the awkward cases right without a rule for each: shears on cobwebs and leaves, a sword
 * on bamboo, an axe on a pumpkin. All of them are tools whose type says nothing about the block but
 * whose speed against it is enormous.
 *
 * <p>The switch happens as the break starts, since the server reads your held item when the break
 * finishes rather than continuously.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class AutoTooll extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> antiBreak = sgGeneral.add(new BoolSetting.Builder()
        .name("anti-break")
        .description("Never switch to a tool that is about to break.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> durabilityThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("durability-threshold")
        .description("Points of durability at which a tool counts as about to break.")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 100)
        .visible(antiBreak::get)
        .build()
    );

    private final Setting<Boolean> switchBack = sgGeneral.add(new BoolSetting.Builder()
        .name("switch-back")
        .description("Return to the slot you were holding once the break finishes.")
        .defaultValue(true)
        .build()
    );

    /** The slot to go back to, or -1 when nothing is owed. */
    private int previous = -1;

    public AutoTooll() {
        super(Categories.World, "auto-tooll", "Switches to whichever tool actually breaks the block fastest.");
    }

    @Override
    public void onDeactivate() {
        previous = -1;
    }

    @EventHandler
    private void onStartBreakingBlock(StartBreakingBlockEvent event) {
        BlockState state = mc.level.getBlockState(event.blockPos);
        if (state.isAir()) return;

        int best = bestSlot(state);
        if (best == -1) return;

        if (switchBack.get() && previous == -1) previous = mc.player.getInventory().getSelectedSlot();

        InvUtils.swap(best, false);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (previous == -1 || mc.player == null) return;

        // Only once the break has actually stopped - switching back mid-break restarts the progress.
        if (mc.gameMode.isDestroying()) return;

        InvUtils.swap(previous, false);
        previous = -1;
    }

    /**
     * The hotbar slot whose tool has the highest destroy speed against this block, or -1 if nothing
     * beats what is already held.
     */
    private int bestSlot(BlockState state) {
        int bestSlot = -1;
        float bestSpeed = speed(mc.player.getInventory().getItem(mc.player.getInventory().getSelectedSlot()), state);

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;

            if (antiBreak.get() && stack.isDamageableItem() && stack.getMaxDamage() - stack.getDamageValue() <= durabilityThreshold.get()) continue;

            float stackSpeed = speed(stack, state);
            if (stackSpeed <= bestSpeed) continue;

            bestSlot = slot;
            bestSpeed = stackSpeed;
        }

        return bestSlot;
    }

    /** What the game itself says this item does against this block, enchantments included. */
    private float speed(ItemStack stack, BlockState state) {
        if (stack.isEmpty()) return 1;

        return stack.getDestroySpeed(state);
    }
}
