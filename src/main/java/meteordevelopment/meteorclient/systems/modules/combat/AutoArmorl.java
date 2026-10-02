/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;

/**
 * Wears the best armour you are carrying, judged by the armour value the item itself declares.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code auto-armor}, kept alongside it
 * under the {@code l} suffix the rest of this port uses for name collisions. Meteor's scores pieces by
 * their enchantments with a preference setting; this one reads the armour and toughness attributes off
 * the item and compares those, which is the number that actually decides how much damage gets through.
 *
 * <p>The other difference is how it moves things. Meteor's uses the inventory; this one uses the
 * hotbar where it can - right-clicking a piece equips it in one interaction rather than two container
 * clicks, which is both faster and something the server sees a player do all the time.
 *
 * <p>The elytra is never touched: swapping it out mid-flight is a fall.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class AutoArmorl extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks between swaps.")
        .defaultValue(2)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Boolean> saveArmor = sgGeneral.add(new BoolSetting.Builder()
        .name("save-armor")
        .description("Refuse to wear a piece that is about to break.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> durabilityThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("durability-threshold")
        .description("Points of durability at which a piece counts as about to break.")
        .defaultValue(8)
        .min(1)
        .sliderRange(1, 100)
        .visible(saveArmor::get)
        .build()
    );

    private final Setting<Boolean> useHotbar = sgGeneral.add(new BoolSetting.Builder()
        .name("use-hotbar")
        .description("Equip from the hotbar with a right click where possible, instead of a container move.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseInScreens = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-in-screens")
        .description("Do nothing while a screen is open, so it does not fight your own clicks.")
        .defaultValue(true)
        .build()
    );

    private int timer;

    public AutoArmorl() {
        super(Categories.Combat, "auto-armorl", "Wears the best armour you carry, judged by its armour value.");
    }

    @Override
    public void onActivate() {
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player.isSpectator()) return;
        if (pauseInScreens.get() && mc.gui.screen() != null) return;

        if (timer > 0) {
            timer--;
            return;
        }

        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (equip(slot)) {
                timer = delay.get();
                return;
            }
        }
    }

    /** Moves the best piece for this slot into it, if anything in the inventory beats what is worn. */
    private boolean equip(EquipmentSlot slot) {
        ItemStack worn = mc.player.getItemBySlot(slot);

        // An elytra is a decision about flying, not a piece of armour to be improved on.
        if (worn.is(Items.ELYTRA)) return false;

        int best = -1;
        double bestScore = score(worn, slot);

        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty() || !fits(stack, slot)) continue;

            double stackScore = score(stack, slot);
            if (stackScore <= bestScore) continue;

            best = i;
            bestScore = stackScore;
        }

        if (best == -1) return false;

        // A hotbar right click equips in one interaction; anything further back has to be moved.
        if (useHotbar.get() && best < 9 && mc.gui.screen() == null) {
            InvUtils.swap(best, true);
            mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
            InvUtils.swapBack();
        } else {
            InvUtils.move().from(best).toArmor(slot.getIndex());
        }

        return true;
    }

    private boolean fits(ItemStack stack, EquipmentSlot slot) {
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);

        return equippable != null && equippable.slot() == slot;
    }

    /**
     * How much damage this piece actually stops, read off its own attributes. A piece about to break
     * scores as nothing, which is what makes the module choose a weaker whole piece over a good one on
     * its last point.
     */
    private double score(ItemStack stack, EquipmentSlot slot) {
        if (stack.isEmpty() || !fits(stack, slot)) return 0;

        if (saveArmor.get() && stack.isDamageableItem() && stack.getMaxDamage() - stack.getDamageValue() <= durabilityThreshold.get()) {
            return 0;
        }

        double armor = 0;
        double toughness = 0;

        var modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (modifiers != null) {
            for (var entry : modifiers.modifiers()) {
                if (entry.attribute() == Attributes.ARMOR) armor += entry.modifier().amount();
                else if (entry.attribute() == Attributes.ARMOR_TOUGHNESS) toughness += entry.modifier().amount();
            }
        }

        // Toughness matters far less than armour but is the right tiebreak between two pieces with the
        // same rating - diamond and iron both give eight on a chestplate, and diamond survives more.
        return armor * 100 + toughness * 10 + (stack.isDamageableItem() ? (double) (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage() : 1);
    }
}
