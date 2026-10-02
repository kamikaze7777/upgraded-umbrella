/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Casts a fishing rod at whoever you are fighting, then reels it straight back in.
 *
 * <p>A bobber landing on a player knocks them, which is small on its own but arrives instantly and
 * cannot be blocked - enough to interrupt a bow draw, break a sprint, or drag someone off an edge. The
 * reel matters as much as the cast: a rod already out cannot be cast again, so the two have to happen
 * a few ticks apart, which is what the reel delay is.
 *
 * <p>The bobber falls far enough over the distance this is useful at that pointing straight at someone
 * lands short, so the launch angle is solved for rather than aimed. Its speed leaves the rod with a
 * random component, which is why the arc is treated as approximate and the target box is what has to
 * be hit rather than a point.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class AutoRod extends Module {
    /** Blocks per tick a bobber leaves at, averaged over the random spread the throw adds. */
    private static final double LAUNCH_SPEED = 1.1;

    /** Blocks per tick squared a bobber falls at. */
    private static final double GRAVITY = 0.03;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far away a target may be to cast at it.")
        .defaultValue(8)
        .min(1)
        .sliderRange(1, 24)
        .build()
    );

    private final Setting<Integer> reelDelay = sgGeneral.add(new IntSetting.Builder()
        .name("reel-delay")
        .description("Ticks between the cast and the reel. Too short and the bobber has not landed yet.")
        .defaultValue(6)
        .min(1)
        .sliderRange(1, 40)
        .build()
    );

    private final Setting<Integer> cooldown = sgGeneral.add(new IntSetting.Builder()
        .name("cooldown")
        .description("Ticks between casts.")
        .defaultValue(20)
        .min(0)
        .sliderRange(0, 100)
        .build()
    );

    private final Setting<Boolean> swapBack = sgGeneral.add(new BoolSetting.Builder()
        .name("swap-back")
        .description("Return to the slot you were holding after each use.")
        .defaultValue(true)
        .build()
    );

    /** Ticks until the next action; which action it is depends on whether a bobber is already out. */
    private int timer;
    private boolean bobberOut;

    public AutoRod() {
        super(Categories.Combat, "auto-rod", "Casts a fishing rod at your target and reels it back in.");
    }

    @Override
    public void onActivate() {
        timer = 0;
        bobberOut = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (timer > 0) {
            timer--;
            return;
        }

        FindItemResult rod = InvUtils.findInHotbar(Items.FISHING_ROD);
        if (!rod.found()) return;

        if (bobberOut) {
            use(rod.slot());
            bobberOut = false;
            timer = cooldown.get();
            return;
        }

        Player target = findTarget();
        if (target == null) return;

        Vec3 aim = target.getBoundingBox().getCenter();
        Double pitch = solvePitch(aim);
        if (pitch == null) return;

        int slot = rod.slot();
        Rotations.rotate(Rotations.getYaw(aim), pitch, 50, () -> use(slot));

        bobberOut = true;
        timer = reelDelay.get();
    }

    /**
     * The launch angle whose arc passes through the aim point, taking the shallower of the two
     * solutions - the flatter arc arrives sooner and is far less obvious than lobbing it.
     */
    private Double solvePitch(Vec3 aim) {
        Vec3 eyes = mc.player.getEyePosition();

        double dx = aim.x - eyes.x;
        double dy = aim.y - eyes.y;
        double dz = aim.z - eyes.z;

        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 1.0E-4) return null;

        double vSq = LAUNCH_SPEED * LAUNCH_SPEED;

        // Negative means no angle reaches that far; the cast cannot cover the distance.
        double discriminant = vSq * vSq - GRAVITY * (GRAVITY * horizontal * horizontal + 2 * dy * vSq);
        if (discriminant < 0) return null;

        return -Math.toDegrees(Math.atan((vSq - Math.sqrt(discriminant)) / (GRAVITY * horizontal)));
    }

    private void use(int slot) {
        InvUtils.swap(slot, swapBack.get());
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        if (swapBack.get()) InvUtils.swapBack();
    }

    private Player findTarget() {
        Player best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Player player : mc.level.players()) {
            if (player == mc.player || !player.isAlive() || !Friends.get().shouldAttack(player)) continue;

            double distance = mc.player.distanceTo(player);
            if (distance > range.get() || distance >= bestDistance) continue;

            best = player;
            bestDistance = distance;
        }

        return best;
    }
}
