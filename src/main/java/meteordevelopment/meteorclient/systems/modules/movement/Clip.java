/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Steps you through a wall or a floor to the first open space on the far side.
 *
 * <p>The server validates how far a claimed position is from the last one, but the check only measures
 * horizontal deviation and it never walks the path between the two points - it only refuses a
 * destination you would be standing inside a block at. So a short move through solid ground is
 * accepted where the same move sideways through a wall is far more visible.
 *
 * <p>Hence the two halves of the search: it looks along the direction you are pressing, refuses to
 * move unless something solid was actually crossed - otherwise this would just be a teleport across
 * open air - and lands on the first spot with floor under it and headroom above.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class Clip extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> vertical = sgGeneral.add(new IntSetting.Builder()
        .name("vertical")
        .description("Blocks to search up and down. Zero disables vertical clipping.")
        .defaultValue(5)
        .min(0)
        .sliderRange(0, 16)
        .build()
    );

    private final Setting<Integer> horizontal = sgGeneral.add(new IntSetting.Builder()
        .name("horizontal")
        .description("Blocks to search sideways when you are pressed against a wall. Zero disables it.")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 8)
        .build()
    );

    private final Setting<Boolean> requireFloor = sgGeneral.add(new BoolSetting.Builder()
        .name("require-floor")
        .description("Only land somewhere with solid ground under it.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> cooldown = sgGeneral.add(new IntSetting.Builder()
        .name("cooldown")
        .description("Ticks between clips.")
        .defaultValue(5)
        .min(0)
        .sliderRange(0, 40)
        .build()
    );

    private int timer;

    public Clip() {
        super(Categories.Movement, "clip", "Steps you through a wall or floor to the first open space beyond it.");
    }

    @Override
    public void onActivate() {
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (timer > 0) {
            timer--;
            return;
        }

        Direction direction = pressedDirection();
        if (direction == null) return;

        int length = direction.getAxis().isVertical() ? vertical.get() : horizontal.get();

        BlockPos landing = search(direction, length);
        if (landing == null) return;

        mc.player.absSnapTo(landing.getX() + 0.5, landing.getY(), landing.getZ() + 0.5);
        mc.player.setDeltaMovement(Vec3.ZERO);

        timer = cooldown.get();
    }

    /**
     * Which way to try. Sideways is only offered while actually pressed against something, because a
     * horizontal clip through open air is a teleport and reads as one.
     */
    private Direction pressedDirection() {
        if (mc.player.horizontalCollision) {
            if (mc.options.keyUp.isDown()) return mc.player.getDirection();
            if (mc.options.keyDown.isDown()) return mc.player.getDirection().getOpposite();
            if (mc.options.keyLeft.isDown()) return mc.player.getDirection().getCounterClockWise();
            if (mc.options.keyRight.isDown()) return mc.player.getDirection().getClockWise();
        }

        if (mc.options.keyShift.isDown()) return Direction.DOWN;
        if (mc.options.keyJump.isDown()) return Direction.UP;

        return null;
    }

    /**
     * The first standable spot past something solid. Tracking whether a wall was crossed is what stops
     * this from simply sliding you forwards across a clear floor.
     */
    private BlockPos search(Direction direction, int length) {
        if (length == 0) return null;

        BlockPos.MutableBlockPos pos = mc.player.blockPosition().mutable();
        boolean crossedSomething = false;

        for (int i = 0; i < length; i++) {
            pos.move(direction);

            // Standing on the ceiling is not a thing, so a clip upwards is allowed to end in mid air.
            if (fits(pos, requireFloor.get() && direction != Direction.UP)) {
                if (crossedSomething) return pos.immutable();
            } else {
                crossedSomething = true;
            }
        }

        return null;
    }

    private boolean fits(BlockPos pos, boolean needsFloor) {
        if (needsFloor && mc.level.getBlockState(pos.below()).getCollisionShape(mc.level, pos.below()).isEmpty()) return false;

        return mc.level.getBlockState(pos).isAir() && mc.level.getBlockState(pos.above()).isAir();
    }
}
