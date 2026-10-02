/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.AABB;

/**
 * Sneaks only at the edge of what you are standing on, which is how bridging by hand is done.
 *
 * <p>Holding sneak the whole way is slow, and not holding it at all walks you off. Sneak is only
 * needed for the moment your box would otherwise clear the block behind you, so this presses it for
 * that moment and releases it again: fast across the block, safe at the edge.
 *
 * <p>Only the direction you are actually pressing is checked. Probing all four sides instead would
 * sneak permanently on a one-wide bridge, where the sides are always open, and that is exactly the
 * behaviour this exists to avoid.
 *
 * <p>Unlike a scaffold this places nothing and rotates nothing. It is the sneak key, pressed on time.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class Eagle extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> edgeDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("edge-distance")
        .description("How far ahead to look for the drop. Smaller cuts it finer and moves faster.")
        .defaultValue(0.5)
        .min(0.01)
        .sliderRange(0.01, 1.3)
        .build()
    );

    private final Setting<Boolean> onlyOnGround = sgGeneral.add(new BoolSetting.Builder()
        .name("only-on-ground")
        .description("Do nothing while airborne.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> onlyWithBlocks = sgGeneral.add(new BoolSetting.Builder()
        .name("only-holding-blocks")
        .description("Do nothing unless you are holding something placeable.")
        .defaultValue(true)
        .build()
    );

    public Eagle() {
        super(Categories.Player, "eagle", "Sneaks only at the edge of the block you stand on, for fast bridging.", "fast-bridge", "legit-scaffold");
    }

    /**
     * Whether the sneak key should be held this tick, given the movement keys as the keyboard reported
     * them. Taking the keys as an argument rather than reading the player's input keeps this correct
     * when another module has already rewritten them.
     */
    public boolean shouldSneak(Input keys) {
        if (!isActive() || mc.player == null || mc.level == null) return false;
        if (mc.player.getAbilities().flying) return false;
        if (onlyOnGround.get() && !mc.player.onGround()) return false;
        if (onlyWithBlocks.get() && !holdingPlaceable()) return false;

        // Opposed keys cancel, exactly as the client's own impulse maths does, so a pair held together
        // reads as no direction rather than as both.
        float left = impulse(keys.left(), keys.right());
        float forward = impulse(keys.forward(), keys.backward());
        if (left == 0 && forward == 0) return false;

        return isDropAhead(left, forward);
    }

    private static float impulse(boolean positive, boolean negative) {
        if (positive == negative) return 0;
        return positive ? 1 : -1;
    }

    private boolean holdingPlaceable() {
        return mc.player.getMainHandItem().getItem() instanceof BlockItem
            || mc.player.getOffhandItem().getItem() instanceof BlockItem;
    }

    /**
     * Takes the footprint you are standing on, slides it the edge distance along the direction being
     * pressed, and asks whether anything still holds it up.
     */
    private boolean isDropAhead(float left, float forward) {
        // The same rotation the client applies to turn key impulses into world movement.
        float yaw = mc.player.getYRot() * Mth.DEG_TO_RAD;
        float sin = Mth.sin(yaw);
        float cos = Mth.cos(yaw);

        double length = Math.sqrt(left * left + forward * forward);
        double dirX = (left * cos - forward * sin) / length;
        double dirZ = (forward * cos + left * sin) / length;

        double distance = edgeDistance.get();
        AABB box = mc.player.getBoundingBox();
        AABB support = new AABB(box.minX, box.minY - 0.5, box.minZ, box.maxX, box.minY, box.maxZ)
            .move(dirX * distance, 0, dirZ * distance)
            .deflate(0.001);

        return mc.level.noBlockCollision(mc.player, support);
    }
}
