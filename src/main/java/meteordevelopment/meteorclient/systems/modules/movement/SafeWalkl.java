/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Stops you walking off an edge, and can push you away from it rather than just holding you at it.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code safe-walk}, kept alongside it
 * under the {@code l} suffix the rest of this port uses for name collisions. Meteor's holds the sneak
 * behaviour at the edge; the addition here is what happens once you are there.
 *
 * <p><b>Stop</b> is the usual behaviour - the movement into the gap is cancelled and you stand still.
 * <b>Invert</b> turns it around, so pressing into an edge walks you back from it, which is what you
 * want when the edge is behind you and the mouse is elsewhere. <b>Centre</b> steers you towards the
 * middle of the block you are on, which is the one that reliably stops a corner clip.
 *
 * <p>All three work by editing the movement before it is applied, which is the same place the game
 * decides a sneaking player does not fall - so the server sees a player who did not step off, not a
 * player who stepped off and came back.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class SafeWalkl extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("What to do once you are at the edge.")
        .defaultValue(Mode.Stop)
        .build()
    );

    private final Setting<Double> centerSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("center-speed")
        .description("Blocks per tick to drift back towards the middle of the block.")
        .defaultValue(0.08)
        .min(0.01)
        .sliderRange(0.01, 0.3)
        .visible(() -> mode.get() == Mode.Center)
        .build()
    );

    public SafeWalkl() {
        super(Categories.Movement, "safe-walkl", "Stops you walking off edges, and can push you back from them.");
    }

    @EventHandler
    private void onMove(PlayerMoveEvent event) {
        // Only matters on the ground; in the air there is nothing to walk off.
        if (!mc.player.onGround() || mc.player.getAbilities().flying) return;

        Vec3 movement = event.movement;

        boolean unsupportedX = wouldLeaveGround(movement.x, 0);
        boolean unsupportedZ = wouldLeaveGround(0, movement.z);

        if (!unsupportedX && !unsupportedZ) return;

        double x = unsupportedX ? 0 : movement.x;
        double z = unsupportedZ ? 0 : movement.z;

        switch (mode.get()) {
            case Stop -> ((IVec3) event.movement).meteor$setXZ(x, z);

            // Pressing into the edge walks you away from it instead.
            case Invert -> ((IVec3) event.movement).meteor$setXZ(
                unsupportedX ? -movement.x : movement.x,
                unsupportedZ ? -movement.z : movement.z
            );

            case Center -> {
                BlockPos below = mc.player.blockPosition().below();

                double towardsX = (below.getX() + 0.5) - mc.player.getX();
                double towardsZ = (below.getZ() + 0.5) - mc.player.getZ();

                ((IVec3) event.movement).meteor$setXZ(
                    unsupportedX ? Math.clamp(towardsX, -centerSpeed.get(), centerSpeed.get()) : movement.x,
                    unsupportedZ ? Math.clamp(towardsZ, -centerSpeed.get(), centerSpeed.get()) : movement.z
                );
            }
        }
    }

    /**
     * Whether taking this step alone would leave the box with nothing under it. Each axis is asked
     * separately so a diagonal into a corner keeps the half that is still supported.
     */
    private boolean wouldLeaveGround(double dx, double dz) {
        if (dx == 0 && dz == 0) return false;

        AABB box = mc.player.getBoundingBox();
        AABB support = new AABB(box.minX, box.minY - 0.5, box.minZ, box.maxX, box.minY, box.maxZ)
            .move(dx, 0, dz)
            .deflate(0.001);

        return mc.level.noBlockCollision(mc.player, support);
    }

    public enum Mode {
        /** Cancel the step into the gap. */
        Stop,
        /** Turn the step around, so pressing into an edge backs away from it. */
        Invert,
        /** Drift towards the middle of the block you are standing on. */
        Center
    }
}
