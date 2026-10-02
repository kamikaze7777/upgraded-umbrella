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
import meteordevelopment.meteorclient.utils.entity.Target;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Turns your camera towards a target rather than sending a rotation behind your back.
 *
 * <p>This is the difference between it and an aura: an aura leaves your view alone and tells the
 * server you are looking somewhere else, which is two rotations that have to be kept consistent. Here
 * there is only one - the camera moves, the server is told the truth, and everything downstream (the
 * hit ray, the swing, the block you are pointed at) follows from it without being coordinated.
 *
 * <p>The cost is that it is a step per tick, capped, and it is left to you to click. Set the step high
 * and it is a snap; set it low and it is a drag towards the target that a real hand could have made.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class Aimbot extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far away a target may be.")
        .defaultValue(5)
        .min(1)
        .sliderRange(1, 16)
        .build()
    );

    private final Setting<Double> fov = sgGeneral.add(new DoubleSetting.Builder()
        .name("fov")
        .description("How far off centre a target may already be, in degrees. Small keeps it to what you were nearly pointing at.")
        .defaultValue(60)
        .min(1)
        .max(180)
        .sliderRange(1, 180)
        .build()
    );

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Degrees to turn per tick. Above about thirty this reads as a snap.")
        .defaultValue(20)
        .min(1)
        .sliderRange(1, 180)
        .build()
    );

    private final Setting<Target> aimAt = sgGeneral.add(new EnumSetting.Builder<Target>()
        .name("aim-at")
        .description("Which part of the target to aim for.")
        .defaultValue(Target.Body)
        .build()
    );

    private final Setting<Boolean> onlyWhileHolding = sgGeneral.add(new BoolSetting.Builder()
        .name("only-while-attacking")
        .description("Only turn while the attack key is held.")
        .defaultValue(true)
        .build()
    );

    private Player target;

    public Aimbot() {
        super(Categories.Combat, "aimbot", "Turns your camera towards a target, a step at a time.");
    }

    @Override
    public void onDeactivate() {
        target = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (onlyWhileHolding.get() && !mc.options.keyAttack.isDown()) {
            target = null;
            return;
        }

        target = findTarget();
        if (target == null) return;

        double wantYaw = Rotations.getYaw(target);
        double wantPitch = Rotations.getPitch(target, aimAt.get());

        // The camera moves, so this is a step towards the answer rather than the answer itself.
        double yaw = mc.player.getYRot() + clampStep(Mth.wrapDegrees(wantYaw - mc.player.getYRot()));
        double pitch = mc.player.getXRot() + clampStep(wantPitch - mc.player.getXRot());

        Rotations.setCamRotation(yaw, Mth.clamp(pitch, -90, 90));
    }

    private double clampStep(double delta) {
        return Mth.clamp(delta, -speed.get(), speed.get());
    }

    /**
     * The closest target already within the field of view. Anything further off centre than that is
     * something you were not looking at, and turning to it is the part that reads as a machine.
     */
    private Player findTarget() {
        Player best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Player player : mc.level.players()) {
            if (player == mc.player || !player.isAlive() || !Friends.get().shouldAttack(player)) continue;

            double distance = mc.player.distanceTo(player);
            if (distance > range.get() || distance >= bestDistance) continue;

            double offset = Math.abs(Mth.wrapDegrees(Rotations.getYaw(player) - mc.player.getYRot()));
            if (offset > fov.get()) continue;

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
