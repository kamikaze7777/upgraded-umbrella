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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

/**
 * Attacks nearby players, with the small imperfections that keep it from looking like a machine.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code kill-aura}, kept alongside it
 * under the {@code l} suffix the rest of this port uses for name collisions. Meteor's has far more
 * control over targeting; the three pieces here are about how a swinging player looks rather than what
 * they hit.
 *
 * <p><b>Fail swings.</b> A person misses. An aura that only ever swings when something is in range has
 * a hit rate no human produces, and that ratio is one of the cheapest things for a server to measure.
 * Swinging at nothing occasionally costs a fraction of the damage and removes the signal entirely.
 *
 * <p><b>Charged hits.</b> A hit below full charge does damage in proportion to the bar, so a hit taken
 * at half charge does about half. Waiting is slower per swing and faster per kill.
 *
 * <p><b>One target.</b> Switching between targets each tick produces rotations no hand makes. Holding
 * one until it dies or leaves is both more human and more effective.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class KillAural extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far away a target may be. The server accepts about six.")
        .defaultValue(4.5)
        .min(1)
        .sliderRange(1, 6)
        .build()
    );

    private final Setting<Boolean> waitForCooldown = sgGeneral.add(new BoolSetting.Builder()
        .name("wait-for-cooldown")
        .description("Only hit on a full attack bar. Slower per swing, faster per kill.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> failSwingChance = sgGeneral.add(new DoubleSetting.Builder()
        .name("fail-swing-chance")
        .description("How often to swing at nothing, so the hit rate is not perfect.")
        .defaultValue(0.05)
        .min(0)
        .max(0.5)
        .sliderRange(0, 0.5)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Face the target before hitting it.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Target> aimAt = sgGeneral.add(new EnumSetting.Builder<Target>()
        .name("aim-at")
        .defaultValue(Target.Body)
        .visible(rotate::get)
        .build()
    );

    private final Setting<Boolean> requireLineOfSight = sgGeneral.add(new BoolSetting.Builder()
        .name("require-line-of-sight")
        .description("Do not hit through walls, which the server allows and a player cannot do.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseWhileUsing = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-while-using")
        .description("Hold off while an item is in use, instead of interrupting it.")
        .defaultValue(true)
        .build()
    );

    /** Held until it dies or leaves, rather than re-picked each tick. */
    private Player target;

    public KillAural() {
        super(Categories.Combat, "kill-aural", "Attacks nearby players, with the imperfections of a person.");
    }

    @Override
    public void onDeactivate() {
        target = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (pauseWhileUsing.get() && mc.player.isUsingItem()) return;

        if (!isValid(target)) target = findTarget();
        if (target == null) return;

        // A hit below full charge scales the damage down with it, so waiting is usually the faster way
        // to kill something even though it is the slower way to swing.
        if (waitForCooldown.get() && mc.player.getAttackStrengthScale(0) < 1) return;

        Player at = target;

        // The miss. It costs a fraction of the damage and removes a signal that is otherwise perfect.
        if (Math.random() < failSwingChance.get()) {
            mc.player.swing(InteractionHand.MAIN_HAND);
            return;
        }

        if (rotate.get()) {
            Rotations.rotate(Rotations.getYaw(at), Rotations.getPitch(at, aimAt.get()), 50, () -> hit(at));
        } else {
            hit(at);
        }
    }

    private void hit(Player at) {
        mc.gameMode.attack(mc.player, at);
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private boolean isValid(Player player) {
        if (player == null || !player.isAlive() || player.isRemoved()) return false;
        if (mc.player.distanceTo(player) > range.get()) return false;

        return !requireLineOfSight.get() || mc.player.hasLineOfSight(player);
    }

    private Player findTarget() {
        Player best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Player player : mc.level.players()) {
            if (player == mc.player || !Friends.get().shouldAttack(player)) continue;
            if (!isValid(player)) continue;

            double distance = mc.player.distanceTo(player);
            if (distance >= bestDistance) continue;

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
