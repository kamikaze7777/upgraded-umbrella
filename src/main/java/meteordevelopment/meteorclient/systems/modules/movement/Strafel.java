/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/**
 * Lets you steer mid-air at full speed instead of drifting where you jumped.
 *
 * <p>Vanilla gives you almost no control in the air: the movement you had when you left the ground is
 * most of the movement you keep, and pressing a direction adds a fraction of a block per tick. This
 * turns the speed you already have to face wherever you are pressing, so the total stays the same and
 * only its direction changes.
 *
 * <p>That last part is what keeps it from being a speed hack. The server measures how far you moved,
 * not which way, so redirecting a jump costs nothing - accelerating it would be refused.
 *
 * <p>Named with the {@code l} suffix this port uses for name collisions: Meteor has no strafe module,
 * but it does have a {@code Strafe} speed mode, and two classes under one name is a trap for whoever
 * reads this next.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class Strafel extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> inAir = sgGeneral.add(new DoubleSetting.Builder()
        .name("in-air")
        .description("How much of the turn to apply while airborne. One is full control.")
        .defaultValue(1)
        .min(0)
        .max(1)
        .sliderRange(0, 1)
        .build()
    );

    private final Setting<Double> onGround = sgGeneral.add(new DoubleSetting.Builder()
        .name("on-ground")
        .description("How much of the turn to apply while on the ground.")
        .defaultValue(0)
        .min(0)
        .max(1)
        .sliderRange(0, 1)
        .build()
    );

    private final Setting<Boolean> stopWhenIdle = sgGeneral.add(new BoolSetting.Builder()
        .name("stop-when-idle")
        .description("Kill horizontal movement outright when nothing is pressed, instead of sliding.")
        .defaultValue(false)
        .build()
    );

    public Strafel() {
        super(Categories.Movement, "strafel", "Turns your existing speed to face where you are pressing, mid-air.");
    }

    @EventHandler
    private void onMove(PlayerMoveEvent event) {
        // Only movement the player is driving. Knockback and pistons arrive as other mover types and
        // redirecting those would undo them.
        if (event.type != MoverType.SELF) return;

        double strength = mc.player.onGround() ? onGround.get() : inAir.get();
        if (strength <= 0) return;

        float forward = mc.player.zza;
        float left = mc.player.xxa;

        if (forward == 0 && left == 0) {
            if (stopWhenIdle.get()) ((IVec3) event.movement).meteor$setXZ(0, 0);
            return;
        }

        Vec3 movement = event.movement;
        double speed = Math.sqrt(movement.x * movement.x + movement.z * movement.z);
        if (speed < 1.0E-4) return;

        // The same rotation the game uses to turn key impulses into world movement.
        float yaw = mc.player.getYRot() * Mth.DEG_TO_RAD;
        float sin = Mth.sin(yaw);
        float cos = Mth.cos(yaw);

        double length = Math.sqrt(forward * forward + left * left);
        double wantX = (left * cos - forward * sin) / length;
        double wantZ = (forward * cos + left * sin) / length;

        // Blended rather than replaced, so a partial setting is a partial turn instead of an on/off.
        double x = Mth.lerp(strength, movement.x, wantX * speed);
        double z = Mth.lerp(strength, movement.z, wantZ * speed);

        ((IVec3) event.movement).meteor$setXZ(x, z);
    }
}
