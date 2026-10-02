/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IServerboundMovePlayerPacket;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Drops you down a step in one tick instead of falling it.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code reverse-step}, kept alongside it
 * under the {@code l} suffix the rest of this port uses for name collisions. Meteor's accelerates the
 * fall; this works out where the fall was going to end and goes straight there.
 *
 * <p>The intermediate positions are sent as well, in order, which is the difference that matters. The
 * server validates each claimed position against the last, so arriving at the bottom without the
 * steps between it looks like a teleport - and sending the steps costs nothing, because they are
 * positions the fall really would have passed through.
 *
 * <p>It refuses to act on a jump you started yourself, and on a landing that would be into water,
 * cobweb, or anything else where the fall itself was the point.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class ReverseStepl extends Module {
    /** What a falling entity keeps of its downward speed each tick, before gravity is added. */
    private static final double DRAG = 0.98;

    /** Blocks per tick squared a player falls at. */
    private static final double GRAVITY = 0.08;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> maxDrop = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-drop")
        .description("The tallest drop to take in one go. Beyond this the fall is left alone.")
        .defaultValue(3)
        .min(1)
        .sliderRange(1, 20)
        .build()
    );

    private final Setting<Integer> maxTicks = sgGeneral.add(new IntSetting.Builder()
        .name("max-ticks")
        .description("How far ahead to look for the landing.")
        .defaultValue(20)
        .min(1)
        .sliderRange(1, 60)
        .build()
    );

    private final Setting<Boolean> sendSteps = sgGeneral.add(new BoolSetting.Builder()
        .name("send-steps")
        .description("Send the positions the fall would have passed through. Off is a visible teleport.")
        .defaultValue(true)
        .build()
    );

    /** Set while a jump you started is in the air, so this does not cut your own jumps short. */
    private boolean jumped;

    public ReverseStepl() {
        super(Categories.Movement, "reverse-stepl", "Drops you down a step in one tick instead of falling it.");
    }

    @Override
    public void onActivate() {
        jumped = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player.onGround()) {
            jumped = false;
            return;
        }

        // Rising means the player left the ground on purpose; finishing that for them is not the job.
        if (mc.player.getDeltaMovement().y > 0) {
            jumped = true;
            return;
        }

        if (jumped || mc.player.getAbilities().flying || mc.player.isFallFlying()) return;
        if (mc.player.fallDistance > 0.5) return;

        List<Vec3> path = simulateFall();
        if (path == null || path.isEmpty()) return;

        Vec3 landing = path.getLast();
        if (mc.player.getY() - landing.y > maxDrop.get()) return;
        if (isUnwantedLanding(landing)) return;

        if (sendSteps.get()) {
            for (Vec3 step : path) send(step);
        }

        mc.player.setPos(landing.x, landing.y, landing.z);
        mc.player.setDeltaMovement(mc.player.getDeltaMovement().x, 0, mc.player.getDeltaMovement().z);
        mc.player.setOnGround(true);
    }

    /**
     * Steps the player's own fall physics forward until something stops it. Returns null when nothing
     * does within the window, which is the case worth leaving alone - a long fall is not a step.
     */
    private List<Vec3> simulateFall() {
        List<Vec3> path = new ArrayList<>();

        Vec3 position = mc.player.position();
        Vec3 velocity = mc.player.getDeltaMovement();
        AABB box = mc.player.getBoundingBox();

        for (int tick = 0; tick < maxTicks.get(); tick++) {
            velocity = new Vec3(velocity.x, (velocity.y - GRAVITY) * DRAG, velocity.z);

            Vec3 next = position.add(velocity);
            AABB moved = box.move(next.subtract(mc.player.position()));

            if (!mc.level.noBlockCollision(mc.player, moved.deflate(0.001))) {
                // Landed. The last clear position is where the fall actually ends.
                return path;
            }

            position = next;
            path.add(position);
        }

        return null;
    }

    /** Landings where the fall was the point, or where arriving early would hurt. */
    private boolean isUnwantedLanding(Vec3 landing) {
        BlockPos below = BlockPos.containing(landing.x, landing.y - 0.5, landing.z);

        return mc.level.getBlockState(below).is(Blocks.WATER)
            || mc.level.getBlockState(below).is(Blocks.COBWEB)
            || mc.level.getBlockState(below).is(Blocks.POWDER_SNOW)
            || mc.level.getBlockState(below).is(Blocks.SLIME_BLOCK)
            || mc.level.getBlockState(below).is(Blocks.HAY_BLOCK);
    }

    private void send(Vec3 at) {
        ServerboundMovePlayerPacket packet = new ServerboundMovePlayerPacket.Pos(at.x, at.y, at.z, false, mc.player.horizontalCollision);

        // NoFall rewrites the ground flag of anything it does not recognise, and these are meant to
        // read as a genuine fall.
        ((IServerboundMovePlayerPacket) packet).meteor$setTag(1337);

        mc.player.connection.send(packet);
    }
}
