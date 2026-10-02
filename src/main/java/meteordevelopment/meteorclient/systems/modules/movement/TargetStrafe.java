/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.systems.modules.combat.TargetLock;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * Circles whoever you are fighting instead of walking at them.
 *
 * <p>Movement is turned side on to the target rather than pointed at it, so you orbit at a set distance.
 * Holding that orbit is what keeps you off the end of their swing while staying inside your own, and it
 * is far harder to track than a straight line.
 *
 * <p>Speed is never added - the direction of the movement is rewritten and its length left alone - so
 * this stacks with whatever else is driving your movement instead of fighting it.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class TargetStrafe extends Module {
    /** Below this the direction to the target is meaningless and orbiting has nothing to orbit. */
    private static final double EPSILON = 1.0E-4;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargeting = settings.createGroup("Targeting");

    private final Setting<Double> radius = sgGeneral.add(new DoubleSetting.Builder()
        .name("radius")
        .description("The distance to hold from the target while circling it.")
        .defaultValue(2.5)
        .min(0.5)
        .sliderRange(0.5, 6)
        .build()
    );

    private final Setting<Direction> direction = sgGeneral.add(new EnumSetting.Builder<Direction>()
        .name("direction")
        .description("""
                Which way round to circle:
                - Input: Follow your own strafe keys, so A and D pick the direction.
                - Left:  Always anticlockwise.
                - Right: Always clockwise.
            """)
        .defaultValue(Direction.Input)
        .build()
    );

    private final Setting<Double> correction = sgGeneral.add(new DoubleSetting.Builder()
        .name("correction")
        .description("How hard to pull back to the radius. Higher closes the gap faster but orbits less cleanly.")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 3)
        .build()
    );

    private final Setting<Boolean> onlyWhenMoving = sgGeneral.add(new BoolSetting.Builder()
        .name("only-when-moving")
        .description("Only steer while you are actually holding a movement key.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> onlyOnGround = sgGeneral.add(new BoolSetting.Builder()
        .name("only-on-ground")
        .description("Stop steering in mid air, so jumps and falls go where you aimed them.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> followCombat = sgTargeting.add(new BoolSetting.Builder()
        .name("follow-combat")
        .description("Circle whoever Target Lock or Kill Aura is already on, rather than picking separately.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Set<EntityType<?>>> entities = sgTargeting.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Entities to circle, when picking a target separately.")
        .onlyAttackable()
        .defaultValue(EntityTypes.PLAYER)
        .build()
    );

    private final Setting<Double> range = sgTargeting.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far away a target can be before it is left alone.")
        .defaultValue(8)
        .min(0)
        .sliderRange(2, 20)
        .build()
    );

    private Entity target;

    public TargetStrafe() {
        super(Categories.Movement, "target-strafe", "Circles your target instead of walking straight at them.");
    }

    @Override
    public void onDeactivate() {
        target = null;
    }

    @EventHandler
    private void onMove(PlayerMoveEvent event) {
        target = findTarget();
        if (target == null) return;

        if (onlyWhenMoving.get() && !PlayerUtils.isMoving()) return;
        if (onlyOnGround.get() && !mc.player.onGround()) return;

        // The speed we are already travelling at. Rewriting only the direction means this never turns
        // into a speed hack, and whatever else is driving movement keeps its say over how fast we go.
        double speed = Math.sqrt(event.movement.x * event.movement.x + event.movement.z * event.movement.z);
        if (speed < EPSILON) return;

        double dx = mc.player.getX() - target.getX();
        double dz = mc.player.getZ() - target.getZ();

        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < EPSILON) return;

        // Out from the target to us, and the quarter turn off it that runs around them.
        double outX = dx / distance;
        double outZ = dz / distance;

        int sign = turnDirection();
        double alongX = -outZ * sign;
        double alongZ = outX * sign;

        // Lean in or out to settle onto the radius. Clamped so a target far outside it never overwhelms
        // the orbit and turns this into a straight run at them.
        double drift = Mth.clamp((distance - radius.get()) * correction.get(), -1, 1);

        double moveX = alongX - outX * drift;
        double moveZ = alongZ - outZ * drift;

        double length = Math.sqrt(moveX * moveX + moveZ * moveZ);
        if (length < EPSILON) return;

        ((IVec3) event.movement).meteor$setXZ(moveX / length * speed, moveZ / length * speed);
    }

    /** Which way round to go. Input mode reads your own strafe keys so you stay in charge of it. */
    private int turnDirection() {
        return switch (direction.get()) {
            case Left -> -1;
            case Right -> 1;
            case Input -> mc.player.xxa > 0 ? -1 : 1;
        };
    }

    /**
     * Prefers whatever is already being fought, so the orbit and the aura never disagree about who the
     * target is. Falls back to picking the nearest match.
     */
    private Entity findTarget() {
        if (followCombat.get()) {
            Entity locked = Modules.get().get(TargetLock.class).getTarget();
            if (locked != null && inRange(locked)) return locked;

            KillAura aura = Modules.get().get(KillAura.class);
            if (aura.isActive()) {
                Entity auraTarget = aura.getTarget();
                if (auraTarget != null && inRange(auraTarget)) return auraTarget;
            }
        }

        return TargetUtils.get(this::entityCheck, SortPriority.LowestDistance);
    }

    private boolean inRange(Entity entity) {
        return entity.isAlive() && PlayerUtils.isWithin(entity, range.get());
    }

    private boolean entityCheck(Entity entity) {
        if (entity.equals(mc.player) || entity.equals(mc.getCameraEntity())) return false;
        if ((entity instanceof LivingEntity living && living.isDeadOrDying()) || !entity.isAlive()) return false;
        if (!entities.get().contains(entity.getType())) return false;
        if (!PlayerUtils.isWithin(entity, range.get())) return false;

        if (entity instanceof Player player) {
            if (player.isCreative()) return false;
            if (!Friends.get().shouldAttack(player)) return false;
        }

        return true;
    }

    @Override
    public String getInfoString() {
        if (target != null) return EntityUtils.getName(target);
        return null;
    }

    public enum Direction {
        Input,
        Left,
        Right
    }
}
