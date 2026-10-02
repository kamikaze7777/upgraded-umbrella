/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Pulls you into the nearest hole and holds you in the middle of it.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code anchor}, kept alongside it under
 * the {@code l} suffix the rest of this port uses for name collisions. Meteor's stops your movement
 * over a hole so you drop in; this one steers towards one and then centres you in it.
 *
 * <p>Centring is the part that matters. A hole only protects you from a crystal if you are actually
 * inside it, and standing on the lip is most of the damage with none of the cover. Once in, the pull
 * continues towards the middle at a much lower speed, which also stops you drifting back out.
 *
 * <p>Everything is done by editing your own movement, so the server sees a player who walked into a
 * hole rather than one who arrived in it.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class Anchorl extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> maxDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-distance")
        .description("How far away a hole may be to pull towards it.")
        .defaultValue(4)
        .min(0.5)
        .sliderRange(0.5, 8)
        .build()
    );

    private final Setting<Double> horizontalSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("horizontal-speed")
        .description("Blocks per tick to move towards the hole.")
        .defaultValue(0.3)
        .min(0.01)
        .sliderRange(0.01, 1)
        .build()
    );

    private final Setting<Double> centerSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("center-speed")
        .description("Blocks per tick to drift towards the middle once you are inside.")
        .defaultValue(0.08)
        .min(0.01)
        .sliderRange(0.01, 0.5)
        .build()
    );

    /** The middle of the hole being aimed at, at floor level. Recomputed each tick. */
    private Vec3 goal;
    private boolean inside;

    public Anchorl() {
        super(Categories.Movement, "anchorl", "Pulls you into the nearest hole and centres you in it.");
    }

    @Override
    public void onDeactivate() {
        goal = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        BlockPos best = findHole();

        if (best == null) {
            goal = null;
            return;
        }

        goal = new Vec3(best.getX() + 0.5, best.getY(), best.getZ() + 0.5);
        inside = mc.player.blockPosition().equals(best);
    }

    /**
     * A one-block gap with something solid on all four sides. That is what a hole is for this purpose -
     * anything more open does not stop the blast.
     */
    private BlockPos findHole() {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos origin = mc.player.blockPosition();

        int radius = (int) Math.ceil(maxDistance.get());

        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -2; y <= 1; y++) {
                for (int z = -radius; z <= radius; z++) {
                    pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);

                    if (!isHole(pos)) continue;

                    double distance = mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                    if (distance > maxDistance.get() * maxDistance.get() || distance >= bestDistance) continue;

                    best = pos.immutable();
                    bestDistance = distance;
                }
            }
        }

        return best;
    }

    private boolean isHole(BlockPos pos) {
        if (!mc.level.getBlockState(pos).isAir() || !mc.level.getBlockState(pos.above()).isAir()) return false;
        if (mc.level.getBlockState(pos.below()).getCollisionShape(mc.level, pos.below()).isEmpty()) return false;

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = pos.relative(direction);

            if (mc.level.getBlockState(side).getCollisionShape(mc.level, side).isEmpty()) return false;
        }

        return true;
    }

    @EventHandler
    private void onMove(PlayerMoveEvent event) {
        if (goal == null) return;

        double dx = goal.x - mc.player.getX();
        double dz = goal.z - mc.player.getZ();

        // Once inside, the pull becomes a slow drift to the middle - at the approach speed it would
        // overshoot the centre and bounce off the far wall every tick.
        double speed = inside ? centerSpeed.get() : horizontalSpeed.get();

        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.01) {
            ((IVec3) event.movement).meteor$setXZ(0, 0);
            return;
        }

        double step = Math.min(speed, length);

        ((IVec3) event.movement).meteor$setXZ(dx / length * step, dz / length * step);
    }

    @Override
    public String getInfoString() {
        return goal == null ? null : inside ? "centring" : "pulling";
    }
}
