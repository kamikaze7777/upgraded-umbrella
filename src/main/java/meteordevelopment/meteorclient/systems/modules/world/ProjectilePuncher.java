/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.world;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.Target;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Hits fireballs and shulker bullets back at whatever sent them.
 *
 * <p>Both are deflected by being attacked, and both are slow enough to be caught in flight. The hard
 * part is knowing which ones matter: a ghast fires constantly and most of it goes nowhere near you, so
 * swinging at everything in range would be a permanent giveaway for no benefit.
 *
 * <p>Two tests decide it, and either one is enough. Whether the projectile is travelling roughly at
 * you, from the angle between its velocity and the line to your box; and whether extending that
 * velocity actually intersects your box. The second exists for the case the first misses - something
 * very close and aimed at your feet is on a line too far off the one to your centre to look like it is
 * coming at you, right up until it hits.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class ProjectilePuncher extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far away to start swinging at something.")
        .defaultValue(4)
        .min(1)
        .sliderRange(1, 6)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks between swings.")
        .defaultValue(2)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Face the projectile before hitting it. Off is faster and far more obvious.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseInScreens = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-in-screens")
        .description("Do nothing while a screen is open.")
        .defaultValue(false)
        .build()
    );

    private int timer;

    public ProjectilePuncher() {
        super(Categories.World, "projectile-puncher", "Hits incoming fireballs and shulker bullets back.", "anti-fireball");
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

        Entity target = findTarget();
        if (target == null) return;

        if (rotate.get()) Rotations.rotate(Rotations.getYaw(target), Rotations.getPitch(target, Target.Body), 50, () -> hit(target));
        else hit(target);

        timer = delay.get();
    }

    private void hit(Entity target) {
        mc.gameMode.attack(mc.player, target);
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private Entity findTarget() {
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        double rangeSq = range.get() * range.get();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LargeFireball) && !(entity instanceof ShulkerBullet)) continue;

            double distance = entity.getBoundingBox().distanceToSqr(mc.player.getEyePosition());
            if (distance > rangeSq || distance >= bestDistance) continue;
            if (!isIncoming(entity)) continue;

            best = entity;
            bestDistance = distance;
        }

        return best;
    }

    /**
     * Whether this projectile is worth swinging at. Velocity is taken from the step it actually moved
     * last tick rather than from its stored motion, because a deflected fireball keeps a stored motion
     * that no longer matches where it is going.
     */
    private boolean isIncoming(Entity entity) {
        Vec3 velocity = entity.position().subtract(entity.xOld, entity.yOld, entity.zOld);
        if (velocity.lengthSqr() < 1.0E-7) return false;

        Vec3 toPlayer = mc.player.getBoundingBox().getCenter().subtract(entity.position());

        // Within sixty degrees of straight at us. cos(60 degrees) is 0.5, so comparing the dot product
        // against half the product of the lengths is the same test without the trigonometry.
        if (toPlayer.dot(velocity) > 0.5 * velocity.length() * toPlayer.length()) return true;

        // Second chance: extend the velocity and see whether it crosses a box grown by the projectile's
        // own size. Anything already inside is on its way out and does not count.
        AABB reachable = mc.player.getBoundingBox().inflate(entity.getBoundingBox().getXsize() / 2);

        return !reachable.contains(entity.position()) && reachable.clip(entity.position(), entity.position().add(velocity.scale(20))).isPresent();
    }
}
