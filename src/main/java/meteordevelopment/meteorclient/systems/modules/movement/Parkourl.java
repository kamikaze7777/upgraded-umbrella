/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Jumps at the edge of a block, deciding by where you are actually about to be.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code parkour}, kept alongside it under
 * the {@code l} suffix the rest of this port uses for name collisions. The difference is what decides
 * "edge": Meteor's measures a distance to it, and this steps your current movement forward one tick
 * and asks whether you would still be standing on anything.
 *
 * <p>That answers the question the module is really asking, and it means a fast approach and a slow
 * one both jump at the right moment - a fixed distance is either too early at a walk or too late at a
 * sprint.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class Parkourl extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> requireSprint = sgGeneral.add(new BoolSetting.Builder()
        .name("require-sprint")
        .description("Only jump while sprinting.")
        .defaultValue(false)
        .build()
    );

    public Parkourl() {
        super(Categories.Movement, "parkourl", "Jumps at block edges, judged by where your movement puts you next tick.");
    }

    /** Whether jump should be held this tick, given the movement keys as the keyboard reported them. */
    public boolean shouldJump(Input keys) {
        if (!isActive() || mc.player == null || mc.level == null) return false;

        // Sneak is how a player asks not to walk off, and holding jump already means they are jumping.
        if (keys.shift() || keys.jump() || mc.player.isShiftKeyDown()) return false;
        if (!mc.player.onGround() || mc.player.getAbilities().flying) return false;
        if (requireSprint.get() && !mc.player.isSprinting()) return false;

        Vec3 movement = mc.player.getDeltaMovement();
        if (movement.horizontalDistanceSqr() < 1.0E-4) return false;

        // One tick of the movement you already have, dropped to just under the feet. If nothing holds
        // that up, the next step is off the edge.
        AABB box = mc.player.getBoundingBox();
        AABB support = new AABB(box.minX, box.minY - 0.5, box.minZ, box.maxX, box.minY, box.maxZ)
            .move(movement.x, 0, movement.z)
            .deflate(0.001);

        return mc.level.noBlockCollision(mc.player, support);
    }
}
