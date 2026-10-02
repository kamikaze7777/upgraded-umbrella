/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

/**
 * Throws a splash potion at your own feet when it is worth doing.
 *
 * <p>A splash potion applied to yourself is thrown downwards, not drunk, which means the pitch has to
 * be straight down for the cloud to catch you - throw it level and it lands somewhere behind whoever
 * you are fighting. That is the whole difficulty, and it is why this rotates before throwing.
 *
 * <p>Only helpful potions are considered, read off the potion's own contents rather than from a list,
 * so a modded or custom potion is judged by what it actually does. Eating is deliberately left to the
 * modules that already do it.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class AutoBuff extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> health = sgGeneral.add(new DoubleSetting.Builder()
        .name("health")
        .description("Throw a healing potion below this much health.")
        .defaultValue(12)
        .min(1)
        .max(20)
        .sliderRange(1, 20)
        .build()
    );

    private final Setting<Boolean> buffs = sgGeneral.add(new BoolSetting.Builder()
        .name("buffs")
        .description("Also throw strength and speed when you are not already under them.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> cooldown = sgGeneral.add(new IntSetting.Builder()
        .name("cooldown")
        .description("Ticks between throws. Too short and the cloud from the last one is still there.")
        .defaultValue(40)
        .min(1)
        .sliderRange(10, 100)
        .build()
    );

    private final Setting<Boolean> onlyOnGround = sgGeneral.add(new BoolSetting.Builder()
        .name("only-on-ground")
        .description("Do not throw while airborne, where the cloud is likely to be left behind.")
        .defaultValue(true)
        .build()
    );

    private int timer;

    public AutoBuff() {
        super(Categories.Player, "auto-buff", "Throws splash potions at your feet when they would help.", "auto-pot");
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

        if (mc.player.isUsingItem()) return;
        if (onlyOnGround.get() && !mc.player.onGround()) return;

        FindItemResult potion = InvUtils.findInHotbar(this::isWanted);
        if (!potion.found()) return;

        timer = cooldown.get();

        int slot = potion.slot();

        // Straight down, so the cloud lands on the thrower. This is the whole reason a splash potion
        // used on yourself needs a rotation at all.
        Rotations.rotate(mc.player.getYRot(), 90, 50, () -> {
            InvUtils.swap(slot, true);
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            InvUtils.swapBack();
        });
    }

    /** Whether this stack is a splash potion carrying something worth having right now. */
    private boolean isWanted(ItemStack stack) {
        if (!stack.is(Items.SPLASH_POTION)) return false;

        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) return false;

        boolean wantHealth = mc.player.getHealth() < health.get();

        for (MobEffectInstance effect : contents.getAllEffects()) {
            // A harmful potion thrown at your own feet is a way to kill yourself, so anything with one
            // in it is left alone regardless of what else it carries.
            if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) return false;
        }

        for (MobEffectInstance effect : contents.getAllEffects()) {
            boolean isHealing = effect.getEffect().value().getCategory() == MobEffectCategory.BENEFICIAL
                && effect.getEffect().value().isInstantaneous();

            if (isHealing && wantHealth) return true;

            // Re-throwing something already running wastes the potion and the cooldown.
            if (buffs.get() && !isHealing && !mc.player.hasEffect(effect.getEffect())) return true;
        }

        return false;
    }
}
