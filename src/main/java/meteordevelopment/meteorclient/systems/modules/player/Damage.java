/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.mixininterface.IServerboundMovePlayerPacket;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

/**
 * Takes fall damage on demand without falling, by claiming a great many very small drops.
 *
 * <p>Half a heart of damage is what a critical hit needs, and jumping for it costs the half second it
 * takes to come back down. The server builds fall distance out of the downward part of each movement
 * packet and never checks how many of them a tick carries, so the same distance can be claimed as
 * hundreds of hops of a twentieth of a block, sent at once. Nothing moves; the fall distance is real.
 *
 * <p>This hurts you and nobody else. It is here as the setup for a hit, which is why the amount is in
 * hearts rather than in blocks.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class Damage extends Module {
    /**
     * Hops per heart. A hop rises and falls 0.049 blocks - just under the 0.05 the server ignores as
     * noise - and fall damage starts after three blocks, so this many round trips add up to a heart.
     */
    private static final int HOPS_PER_HEART = 65;

    /** Kept below the threshold at which a claimed step counts as movement rather than as a jitter. */
    private static final double HOP_HEIGHT = 0.049;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> damage = sgGeneral.add(new IntSetting.Builder()
        .name("damage")
        .description("Hearts to take.")
        .defaultValue(1)
        .min(1)
        .max(19)
        .sliderRange(1, 10)
        .build()
    );

    public Damage() {
        super(Categories.Player, "damage", "Takes a set amount of fall damage without falling.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.getConnection() == null) {
            toggle();
            return;
        }

        double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
        boolean pushed = mc.player.horizontalCollision;

        for (int i = 0; i < HOPS_PER_HEART * damage.get(); i++) {
            send(x, y + HOP_HEIGHT, z, false, pushed);
            send(x, y, z, false, pushed);
        }

        // The landing. Fall distance is only cashed in when a packet says you are on the ground, and it
        // is also what clears the total so the next use starts from nothing.
        send(x, y, z, true, pushed);

        toggle();
    }

    private void send(double x, double y, double z, boolean onGround, boolean horizontalCollision) {
        ServerboundMovePlayerPacket packet = new ServerboundMovePlayerPacket.Pos(x, y, z, onGround, horizontalCollision);

        // NoFall rewrites the ground flag of anything it does not recognise, and doing that here would
        // erase the fall distance this is building.
        ((IServerboundMovePlayerPacket) packet).meteor$setTag(1337);

        mc.getConnection().send(packet);
    }
}
