/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Fires a wind charge under you just before you land.
 *
 * <p>A wind charge bursting at your feet throws you back up, which cancels the fall that was about to
 * hurt and launches you onward in the same motion. The timing is the whole thing: too early and the
 * burst is spent while you are still high, too late and you have already taken the damage.
 *
 * <p>So the fall is simulated forward a few ticks, and the charge goes off only once the ground is
 * genuinely about to arrive.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class AutoWindCharge extends Module {
    /** Vanilla downward acceleration per tick, and the drag left on what is already there. */
    private static final double GRAVITY = 0.08;
    private static final double DRAG = 0.98;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> lookahead = sgGeneral.add(new IntSetting.Builder()
        .name("lookahead")
        .description("How many ticks ahead to look for the ground before firing.")
        .defaultValue(7)
        .min(1)
        .sliderRange(1, 20)
        .build()
    );

    private final Setting<Boolean> requireJump = sgGeneral.add(new BoolSetting.Builder()
        .name("require-jump")
        .description("Only fire while you are holding jump, so it never goes off on a fall you meant to take.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> horizontalBoost = sgGeneral.add(new BoolSetting.Builder()
        .name("horizontal-boost")
        .description("Aim behind you rather than straight down, trading height for distance.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> boostPitch = sgGeneral.add(new DoubleSetting.Builder()
        .name("boost-pitch")
        .description("How far down to aim when boosting. 90 is straight down.")
        .defaultValue(70)
        .min(0)
        .max(90)
        .sliderRange(0, 90)
        .visible(horizontalBoost::get)
        .build()
    );

    public AutoWindCharge() {
        super(Categories.Player, "auto-wind-charge", "Fires a wind charge under you just before you land.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player.isFallFlying() || mc.player.isSwimming() || mc.player.isInWater() || mc.player.isInLava()) return;
        if (requireJump.get() && !mc.options.keyJump.isDown()) return;

        // Only interested on the way down; a rising player has no landing to cancel yet.
        if (mc.player.getDeltaMovement().y >= 0 || mc.player.onGround()) return;

        if (!landsWithinLookahead()) return;

        FindItemResult charge = InvUtils.findInHotbar(Items.WIND_CHARGE);
        if (!charge.found()) return;

        // Behind us for a boost, straight down otherwise: the burst pushes away from where it lands.
        double yaw = horizontalBoost.get() ? mc.player.getYRot() - 180 : mc.player.getYRot();
        double pitch = horizontalBoost.get() ? boostPitch.get() : 90;

        Rotations.rotate(yaw, pitch, 50, () -> {
            if (charge.isOffhand()) {
                mc.gameMode.useItem(mc.player, InteractionHand.OFF_HAND);
            } else {
                InvUtils.swap(charge.slot(), true);
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                InvUtils.swapBack();
            }
        });
    }

    /**
     * Walks the fall forward a tick at a time under vanilla gravity and drag, stopping as soon as the
     * player's box would meet something. Cheaper and steadier than reading the block below, which says
     * nothing about how long there is until we reach it.
     */
    private boolean landsWithinLookahead() {
        Vec3 position = mc.player.position();
        double velocity = mc.player.getDeltaMovement().y;
        AABB box = mc.player.getBoundingBox();

        for (int tick = 0; tick < lookahead.get(); tick++) {
            velocity = (velocity - GRAVITY) * DRAG;
            position = position.add(0, velocity, 0);

            AABB moved = box.move(0, position.y - mc.player.getY(), 0);
            if (!mc.level.noCollision(mc.player, moved)) return true;
        }

        return false;
    }
}
