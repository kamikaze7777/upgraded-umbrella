/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Picks the weapon that will actually do the most damage to what you are about to hit.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code auto-weapon}, kept alongside it
 * under the {@code l} suffix the rest of this port uses for name collisions. Meteor's chooses by
 * weapon category with a damage threshold; this one works the number out per target.
 *
 * <p>That matters because the best weapon is not a property of the weapon. Smite only counts against
 * the undead, bane of arthropods only against spiders, and a mace does nothing special unless you are
 * falling. Against a player none of those apply and a plain sharpness sword wins; against a skeleton
 * the smite axe wins by a wide margin. Asking what each weapon would do to <i>this</i> target gets
 * that right without a rule per case.
 *
 * <p>The swap happens as the attack is sent, so the weapon is in hand for the hit and back out
 * immediately after.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class AutoWeaponl extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> antiBreak = sgGeneral.add(new BoolSetting.Builder()
        .name("anti-break")
        .description("Never swap to a weapon that is about to break.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> durabilityThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("durability-threshold")
        .description("Points of durability at which a weapon counts as about to break.")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 100)
        .visible(antiBreak::get)
        .build()
    );

    private final Setting<Boolean> swapBack = sgGeneral.add(new BoolSetting.Builder()
        .name("swap-back")
        .description("Return to the slot you were holding after the hit.")
        .defaultValue(true)
        .build()
    );

    public AutoWeaponl() {
        super(Categories.Combat, "auto-weaponl", "Swaps to whichever weapon does the most damage to this target.");
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (!(event.entity instanceof LivingEntity)) return;

        int best = bestSlot(event.entity);
        if (best == -1 || best == mc.player.getInventory().getSelectedSlot()) return;

        InvUtils.swap(best, swapBack.get());
    }

    /**
     * The hotbar slot whose weapon would do the most damage to this particular target, or -1 if
     * nothing beats what is already held.
     */
    private int bestSlot(Entity target) {
        int bestSlot = -1;
        double bestDamage = damage(mc.player.getInventory().getItem(mc.player.getInventory().getSelectedSlot()), target);

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;

            if (antiBreak.get() && stack.isDamageableItem() && stack.getMaxDamage() - stack.getDamageValue() <= durabilityThreshold.get()) continue;

            double damage = damage(stack, target);
            if (damage <= bestDamage) continue;

            bestSlot = slot;
            bestDamage = damage;
        }

        return bestSlot;
    }

    /**
     * What this weapon would do to this target, enchantments included. Meteor already works this out
     * for its own damage estimates, so the answer here is the same one the rest of the client uses.
     */
    private double damage(ItemStack stack, Entity target) {
        if (stack.isEmpty()) return 0;

        return DamageUtils.getAttackDamage(mc.player, target, stack);
    }
}
