/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.Target;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Steers a glide towards whoever you are fighting.
 *
 * <p>Gliding is entirely a matter of where you are pointing: the client turns your look direction into
 * velocity every tick, and the server accepts the positions that come out. So chasing someone on an
 * elytra is aiming, not thrust - which is what makes it something a module can do at all.
 *
 * <p>It only takes over while actually gliding, and it aims a little above the target because a glide
 * that points straight at something is already below it by the time it arrives.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class ElytraTarget extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far away a target may be to chase it.")
        .defaultValue(64)
        .min(1)
        .sliderRange(8, 256)
        .build()
    );

    private final Setting<Double> lead = sgGeneral.add(new DoubleSetting.Builder()
        .name("lead")
        .description("How far above the target to aim, to make up for the drop on the way in.")
        .defaultValue(1.5)
        .min(0)
        .sliderRange(0, 8)
        .build()
    );

    private final Setting<Boolean> avoidCollision = sgGeneral.add(new BoolSetting.Builder()
        .name("avoid-collision")
        .description("Nudge upwards when the path ahead is about to hit something.")
        .defaultValue(true)
        .build()
    );

    private Player target;

    public ElytraTarget() {
        super(Categories.Combat, "elytra-target", "Steers your glide towards the nearest target.");
    }

    @Override
    public void onDeactivate() {
        target = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        // Nothing to steer when not gliding; the look direction does not drive movement otherwise.
        if (!mc.player.isFallFlying()) {
            target = null;
            return;
        }

        target = findTarget();
        if (target == null) return;

        Vec3 at = target.getBoundingBox().getCenter().add(0, lead.get(), 0);

        Rotations.rotate(Rotations.getYaw(at), Rotations.getPitch(at), 50);

        // A glide into terrain ends it outright, and the recovery costs more than the chase is worth.
        if (avoidCollision.get() && !mc.level.noCollision(mc.player, mc.player.getBoundingBox().move(mc.player.getDeltaMovement()))) {
            mc.player.push(0, 0.1, 0);
        }
    }

    private Player findTarget() {
        Player best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Player player : mc.level.players()) {
            if (player == mc.player || !player.isAlive() || !Friends.get().shouldAttack(player)) continue;

            double distance = mc.player.distanceTo(player);
            if (distance > range.get() || distance >= bestDistance) continue;
            if (!mc.player.hasLineOfSight(player)) continue;

            best = player;
            bestDistance = distance;
        }

        return best;
    }

    @Override
    public String getInfoString() {
        return target == null ? null : target.getName().getString();
    }
}
