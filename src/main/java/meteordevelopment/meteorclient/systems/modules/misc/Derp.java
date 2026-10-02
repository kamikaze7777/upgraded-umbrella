/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

/**
 * Sends a rotation to the server that has nothing to do with where you are looking.
 *
 * <p>Your view and the rotation the server has for you are two separate things, and only the second
 * one is what other players see. Overwriting it makes your body spin on their screens while your own
 * camera stays exactly where you left it.
 *
 * <p>Anything that depends on the server-side rotation - a placement, a hit that needs to face its
 * target - will be judged against the spinning value, so this does not mix with those.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class Derp extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("What to do with the rotation.")
        .defaultValue(Mode.Spin)
        .build()
    );

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Degrees per tick while spinning.")
        .defaultValue(30)
        .min(1)
        .sliderRange(1, 180)
        .visible(() -> mode.get() == Mode.Spin)
        .build()
    );

    private float yaw;

    public Derp() {
        super(Categories.Misc, "derp", "Sends a rotation to the server unrelated to where you are looking.");
    }

    @Override
    public void onActivate() {
        yaw = mc.player == null ? 0 : mc.player.getYRot();
    }

    /**
     * Sent on the movement packets rather than by rotating the player, so the camera is untouched -
     * rotating the player itself would spin the view as well.
     */
    @EventHandler
    private void onSendMovementPackets(SendMovementPacketsEvent.Pre event) {
        yaw += speed.get().floatValue();

        float sendYaw = switch (mode.get()) {
            case Spin -> yaw;
            case Backwards -> mc.player.getYRot() + 180;
            case Random -> (float) (Math.random() * 360 - 180);
        };

        float sendPitch = mode.get() == Mode.Random ? (float) (Math.random() * 180 - 90) : mc.player.getXRot();

        mc.player.connection.send(new ServerboundMovePlayerPacket.Rot(sendYaw, sendPitch, mc.player.onGround(), mc.player.horizontalCollision));
    }

    public enum Mode {
        /** Turns steadily. */
        Spin,
        /** Faces the opposite way to your camera. */
        Backwards,
        /** A different direction every tick. */
        Random
    }
}
