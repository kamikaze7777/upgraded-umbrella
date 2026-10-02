/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Follows an enemy's ender pearl by throwing your own at where theirs will land.
 *
 * <p>A thrown pearl is an entity the server tells everyone about, and the packet that announces it
 * carries both its position and its launch velocity. Those two numbers are the whole of its flight, so
 * where it comes down can be worked out the moment it appears - a second and a half before it gets
 * there, which is exactly the time needed to throw one after it.
 *
 * <p>The landing point is found by stepping the pearl's own physics forward: drag, gravity, and a stop
 * as soon as the path enters a block. Then the same closed-form arc solve as the other projectile
 * modules turns that point into a launch angle.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class AutoPearl extends Module {
    /** What a thrown pearl leaves the hand at, in blocks per tick. */
    private static final double LAUNCH_SPEED = 1.5;

    /** Blocks per tick squared a pearl falls at. */
    private static final double GRAVITY = 0.03;

    /** What a pearl keeps of its speed each tick in air. */
    private static final double DRAG = 0.99;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Double> minDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-distance")
        .description("Ignore pearls that land close enough to walk to.")
        .defaultValue(8)
        .min(0)
        .sliderRange(0, 40)
        .build()
    );

    private final Setting<Double> maxDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-distance")
        .description("Ignore pearls landing further than your own throw can reach.")
        .defaultValue(50)
        .min(1)
        .sliderRange(10, 120)
        .build()
    );

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Mark where the pearl is going to land.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> color = sgRender.add(new ColorSetting.Builder()
        .name("color")
        .defaultValue(new SettingColor(150, 60, 255, 200))
        .visible(render::get)
        .build()
    );

    /** Where the last enemy pearl is going. Kept after the throw so it stays drawn. */
    private volatile Vec3 landing;

    /** Set when a new landing point arrives and cleared once it has been thrown at. */
    private volatile boolean pending;

    public AutoPearl() {
        super(Categories.Combat, "auto-pearl", "Throws a pearl at where an enemy's pearl will land.", "pearl-follower");
    }

    @Override
    public void onDeactivate() {
        landing = null;
        pending = false;
    }

    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        if (!(event.packet instanceof ClientboundAddEntityPacket packet) || packet.getType() != EntityTypes.ENDER_PEARL) return;

        Vec3 from = new Vec3(packet.getX(), packet.getY(), packet.getZ());
        Vec3 velocity = packet.getMovement();

        // Reading the packet is safe here; the simulation needs the world, so it waits for the tick.
        mc.execute(() -> {
            if (mc.level == null || mc.player == null) return;

            // Our own pearls announce themselves the same way, and following one of those would be a
            // loop rather than a chase.
            if (from.distanceToSqr(mc.player.getEyePosition()) < 4) return;

            Vec3 target = simulate(from, velocity);
            if (target == null) return;

            landing = target;
            pending = true;
        });
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        Vec3 target = landing;
        if (target == null || !pending) return;
        pending = false;

        double distance = Math.sqrt(mc.player.distanceToSqr(target));
        if (distance < minDistance.get() || distance > maxDistance.get()) return;

        FindItemResult pearl = InvUtils.findInHotbar(Items.ENDER_PEARL);
        if (!pearl.found()) return;

        Double pitch = solvePitch(target);
        if (pitch == null) return;

        int slot = pearl.slot();

        Rotations.rotate(Rotations.getYaw(target), pitch, 50, () -> {
            InvUtils.swap(slot, true);
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            InvUtils.swapBack();
        });
    }

    /**
     * Steps the pearl's own physics until it hits something. Drag is applied before gravity, in that
     * order, because that is the order the entity itself applies them.
     */
    private Vec3 simulate(Vec3 position, Vec3 velocity) {
        for (int tick = 0; tick < 240; tick++) {
            Vec3 next = position.add(velocity);
            BlockPos block = BlockPos.containing(next);

            // A pearl stops at the first block it enters, so the first solid one along the path is where
            // it lands. Testing the block rather than sweeping the segment is close enough at these
            // speeds and far cheaper over two hundred ticks.
            if (!mc.level.getBlockState(block).getCollisionShape(mc.level, block).isEmpty()) return next;

            position = next;
            velocity = velocity.scale(DRAG).subtract(0, GRAVITY, 0);

            // Past the bottom of the world it is not going to land anywhere worth following.
            if (position.y < mc.level.getMinY()) return null;
        }

        return null;
    }

    /** The shallower of the two arcs that pass through the landing point, which arrives soonest. */
    private Double solvePitch(Vec3 aim) {
        Vec3 eyes = mc.player.getEyePosition();

        double dx = aim.x - eyes.x;
        double dy = aim.y - eyes.y;
        double dz = aim.z - eyes.z;

        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 1.0E-4) return null;

        double vSq = LAUNCH_SPEED * LAUNCH_SPEED;

        double discriminant = vSq * vSq - GRAVITY * (GRAVITY * horizontal * horizontal + 2 * dy * vSq);
        if (discriminant < 0) return null;

        return -Math.toDegrees(Math.atan((vSq - Math.sqrt(discriminant)) / (GRAVITY * horizontal)));
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        Vec3 target = landing;
        if (!render.get() || target == null) return;

        event.renderer.line(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ(), target.x, target.y, target.z, color.get());
    }
}
