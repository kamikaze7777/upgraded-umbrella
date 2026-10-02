/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.world.CollisionShapeEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * Catches you before a fall becomes a fall into the void.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code anti-void}, kept alongside it
 * under the {@code l} suffix the rest of this port uses for name collisions. The difference is that it
 * looks ahead rather than reacting: your fall is stepped forward a second or so and the column below
 * you is checked for anything at all, so it acts while there is still something to act with.
 *
 * <p>Two ways out, and they fail differently, which is why both are offered. <b>Ghost block</b> gives
 * the air under you a collision box and lets the game land you on it - cheap, and it does nothing the
 * server has to accept. <b>Rewind</b> puts you back at the last position you were safely standing on,
 * which always works and is a teleport the server may well refuse.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class AntiVoidl extends Module {
    /** What a falling entity keeps of its downward speed each tick, before gravity is added. */
    private static final double DRAG = 0.98;

    /** Blocks per tick squared a player falls at. */
    private static final double GRAVITY = 0.08;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("How to catch the fall.")
        .defaultValue(Mode.GhostBlock)
        .build()
    );

    private final Setting<Integer> lookahead = sgGeneral.add(new IntSetting.Builder()
        .name("lookahead")
        .description("Ticks of fall to simulate before deciding.")
        .defaultValue(20)
        .min(1)
        .sliderRange(5, 60)
        .build()
    );

    private final Setting<Integer> voidLevel = sgGeneral.add(new IntSetting.Builder()
        .name("void-level")
        .description("Blocks below the world's floor that count as gone.")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 64)
        .build()
    );

    /** The last position the player was standing safely on, which rewind mode goes back to. */
    private Vec3 safe;

    /** Set for the tick the catch is happening on, which is what the ghost block reads. */
    private boolean catching;

    public AntiVoidl() {
        super(Categories.Movement, "anti-voidl", "Catches you before a fall becomes a fall into the void.");
    }

    @Override
    public void onActivate() {
        safe = null;
        catching = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player.onGround()) {
            safe = mc.player.position();
            catching = false;
            return;
        }

        catching = isHeadingForVoid();
        if (!catching || mode.get() != Mode.Rewind || safe == null) return;

        mc.player.setPos(safe.x, safe.y, safe.z);
        mc.player.setDeltaMovement(Vec3.ZERO);
        catching = false;

        warning("Caught a fall into the void.");
    }

    /**
     * Steps the fall forward and asks whether anything stops it before the void does. Anything at all
     * counts - this is not about landing well, only about landing.
     */
    private boolean isHeadingForVoid() {
        Vec3 position = mc.player.position();
        Vec3 velocity = mc.player.getDeltaMovement();

        if (velocity.y >= 0) return false;

        double floor = mc.level.getMinY() + voidLevel.get();

        for (int tick = 0; tick < lookahead.get(); tick++) {
            velocity = new Vec3(velocity.x, (velocity.y - GRAVITY) * DRAG, velocity.z);
            position = position.add(velocity);

            AABB box = mc.player.getBoundingBox().move(position.subtract(mc.player.position()));

            // Something in the way means the fall ends somewhere, which is all that is being asked.
            if (!mc.level.noBlockCollision(mc.player, box.deflate(0.001))) return false;

            if (position.y <= floor) return true;
        }

        // Still falling at the end of the window with nothing found. On most worlds that is the void.
        return position.y < mc.player.getY() - 20;
    }

    @EventHandler
    private void onCollisionShape(CollisionShapeEvent event) {
        if (!catching || mode.get() != Mode.GhostBlock || mc.player == null) return;

        // Only the air directly beneath, and only on the tick the catch is happening, so this is a
        // floor for one landing rather than a world you could walk around on.
        if (!event.state.isAir()) return;
        if (event.pos.getY() != mc.player.blockPosition().getY() - 1) return;
        if (event.pos.getX() != mc.player.blockPosition().getX() || event.pos.getZ() != mc.player.blockPosition().getZ()) return;

        event.shape = Shapes.block();
    }

    public enum Mode {
        /** Give the air below you a floor and let the game land you on it. */
        GhostBlock,
        /** Put you back where you last stood. Always works, and always a teleport. */
        Rewind
    }
}
