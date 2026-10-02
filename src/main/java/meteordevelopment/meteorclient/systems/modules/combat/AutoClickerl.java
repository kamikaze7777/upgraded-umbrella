/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Clicks for you while the attack button is held, timed so the hits are worth landing.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code auto-clicker}, kept alongside it
 * under the {@code l} suffix the rest of this port uses for name collisions. Two differences, and both
 * are about clicking less rather than faster.
 *
 * <p>It only fires at what you tell it to - an enemy, any entity, a block, or anything - so it does not
 * swing at air between targets, which is the most visible thing a clicker does. And it can wait for
 * the attack cooldown to refill, because a hit taken at half charge does about half the damage: a
 * clicker running at twenty a second does less damage per second than one waiting for the bar.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class AutoClickerl extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Objective> objective = sgGeneral.add(new EnumSetting.Builder<Objective>()
        .name("objective")
        .description("What has to be under the crosshair before it will click.")
        .defaultValue(Objective.Enemy)
        .build()
    );

    private final Setting<Double> minCps = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-cps")
        .description("Slowest rate to click at.")
        .defaultValue(8)
        .min(0.1)
        .sliderRange(0.1, 20)
        .build()
    );

    private final Setting<Double> maxCps = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-cps")
        .description("Fastest rate to click at. The gap between the two is what stops it being a metronome.")
        .defaultValue(12)
        .min(0.1)
        .sliderRange(0.1, 20)
        .build()
    );

    private final Setting<Boolean> waitForCooldown = sgGeneral.add(new BoolSetting.Builder()
        .name("wait-for-cooldown")
        .description("Only hit on a full attack bar. Slower, and it does far more damage.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseWhileUsing = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-while-using")
        .description("Hold off while an item is in use, instead of interrupting it.")
        .defaultValue(true)
        .build()
    );

    /** Milliseconds until the next click, redrawn after each one so the rate is never exactly steady. */
    private long nextClickAt;

    public AutoClickerl() {
        super(Categories.Combat, "auto-clickerl", "Clicks while the attack button is held, on a target and on cooldown.", "trigger-bot");
    }

    @Override
    public void onActivate() {
        nextClickAt = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!mc.options.keyAttack.isDown() || mc.gui.screen() != null) return;
        if (pauseWhileUsing.get() && mc.player.isUsingItem()) return;
        if (!onObjective()) return;

        // A hit below full charge scales the damage down with it, so waiting is usually the faster way
        // to kill something even though it is the slower way to click.
        if (waitForCooldown.get() && mc.player.getAttackStrengthScale(0) < 1) return;

        long now = System.currentTimeMillis();
        if (now < nextClickAt) return;

        // Only an entity can be attacked. Under a block or nothing this is a swing, which is what a
        // click at that crosshair actually does.
        if (mc.hitResult instanceof EntityHitResult entity) mc.gameMode.attack(mc.player, entity.getEntity());

        mc.player.swing(InteractionHand.MAIN_HAND);

        double cps = minCps.get() + Math.random() * Math.max(0, maxCps.get() - minCps.get());
        nextClickAt = now + (long) (1000 / Math.max(0.1, cps));
    }

    private boolean onObjective() {
        HitResult hit = mc.hitResult;

        return switch (objective.get()) {
            case Enemy -> hit instanceof EntityHitResult entity
                && (!(entity.getEntity() instanceof Player player) || Friends.get().shouldAttack(player));
            case Entity -> hit instanceof EntityHitResult;
            case Block -> hit instanceof BlockHitResult;
            case Anything -> true;
        };
    }

    public enum Objective {
        /** Anything attackable that is not a friend. */
        Enemy,
        /** Any entity at all. */
        Entity,
        /** A block. */
        Block,
        /** Whatever is there, including nothing. */
        Anything
    }
}
