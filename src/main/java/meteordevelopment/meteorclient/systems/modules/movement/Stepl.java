/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IServerboundMovePlayerPacket;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Steps up blocks, and can send the climb as the shape of a real jump.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code step}, kept alongside it under the
 * {@code l} suffix the rest of this port uses for name collisions. Meteor's raises the step height,
 * which the client applies and the server sees as a sudden rise.
 *
 * <p>The addition is <b>jump-shaped</b> mode. A player jumping onto a block produces a very
 * recognisable sequence of heights - a fast rise that decelerates, a moment at the top, then a small
 * settle - and a movement check that compares your climb against a fall curve is looking for exactly
 * that. Sending the intermediate positions along that curve costs a few packets and makes the step
 * indistinguishable from the jump it is imitating.
 *
 * <p>Instant mode sends nothing extra and simply arrives, which is faster and far more obvious.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class Stepl extends Module {
    /**
     * The heights a real jump passes through, as offsets from where it started. Measured off a vanilla
     * jump onto a one-block step; the deceleration towards the top is the part a check looks for.
     */
    private static final double[] JUMP_CURVE = {0.42, 0.7532, 1.0013, 1.1661, 1.2491, 1.2522, 1.1767, 1.0243, 1.0};

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("How to report the climb.")
        .defaultValue(Mode.JumpShaped)
        .build()
    );

    private final Setting<Double> height = sgGeneral.add(new DoubleSetting.Builder()
        .name("height")
        .description("The tallest step to take. Above about 1.25 no jump shape will cover it.")
        .defaultValue(1)
        .min(0.6)
        .sliderRange(0.6, 3)
        .build()
    );

    public Stepl() {
        super(Categories.Movement, "stepl", "Steps up blocks, optionally shaped like a real jump.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!mc.player.onGround() || mc.player.getAbilities().flying) return;
        if (!mc.player.horizontalCollision) return;

        double step = findStep();
        if (step <= 0) return;

        double from = mc.player.getY();

        if (mode.get() == Mode.JumpShaped) {
            for (double offset : JUMP_CURVE) {
                if (offset > step) break;

                send(from + offset);
            }
        }

        mc.player.setPos(mc.player.getX(), from + step, mc.player.getZ());
    }

    /**
     * The height of the ledge you are pressed against, or zero if there is nothing to step onto. The
     * box is raised until it is clear and the ground under it is solid, so a two-block wall reports
     * nothing rather than half of itself.
     */
    private double findStep() {
        AABB box = mc.player.getBoundingBox();

        for (double offset = 0.5; offset <= height.get(); offset += 0.0625) {
            AABB raised = box.move(0, offset, 0).deflate(0.001);
            if (!mc.level.noBlockCollision(mc.player, raised)) continue;

            // Clear at this height. It is only a step if something holds you up there.
            AABB support = new AABB(box.minX, box.minY + offset - 0.1, box.minZ, box.maxX, box.minY + offset, box.maxZ)
                .move(mc.player.getDeltaMovement().x, 0, mc.player.getDeltaMovement().z)
                .deflate(0.001);

            if (!mc.level.noBlockCollision(mc.player, support)) return offset;
        }

        return 0;
    }

    private void send(double y) {
        ServerboundMovePlayerPacket packet = new ServerboundMovePlayerPacket.Pos(
            mc.player.getX(), y, mc.player.getZ(), false, mc.player.horizontalCollision
        );

        // NoFall rewrites the ground flag of anything it does not recognise, and these are meant to
        // read as the rise of a jump.
        ((IServerboundMovePlayerPacket) packet).meteor$setTag(1337);

        mc.player.connection.send(packet);
    }

    public enum Mode {
        /** Arrive at the top with nothing in between. Fastest, and the most obvious. */
        Instant,
        /** Send the heights a real jump would have passed through on the way up. */
        JumpShaped
    }
}
