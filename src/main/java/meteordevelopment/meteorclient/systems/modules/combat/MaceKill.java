/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.mixininterface.IServerboundMovePlayerPacket;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.phys.AABB;

/**
 * Claims an enormous fall right before a mace hit, so the smash lands for far more than a real fall
 * ever could.
 *
 * <p>Smash damage comes off the fall distance the server has for you, which it builds from the vertical
 * delta of your movement packets. Claim a rise and then a matching drop and it credits you the whole
 * thing. Two details decide how much is reachable:
 *
 * <ul>
 * <li>The distance a single packet may claim is not fixed. The allowance is
 * {@code max(100, (10 * packets * speed)^2)} against squared distance, counted against the packets the
 * server has already taken this tick, and walking speed works out to 1. So each extra packet sent first
 * raises the ceiling by another ten blocks, and a stack of them ahead of the rise buys a very long fall.
 * <li>Only where the rise ends is checked, not the way up. The server does replay the move with
 * collision, but the test it applies afterwards zeroes the vertical component before measuring, so
 * vertical desync never registers as moving wrongly. What it will refuse is ending up inside a block.
 * </ul>
 *
 * <p>So the height to ask for is the tallest one whose landing box is clear, and ceilings in between do
 * not matter.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class MaceKill extends Module {
    /** Blocks of claim bought per packet sent ahead of the rise. */
    private static final int BLOCKS_PER_PACKET = 10;

    /**
     * Packets a tick may carry before the server stops counting them individually and collapses the
     * allowance back to one packet's worth. Standing still with no rotation refills the budget this is
     * measured against, which is what the priming packets below are doing.
     */
    private static final int MAX_PACKETS = 20;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> fallHeight = sgGeneral.add(new IntSetting.Builder()
        .name("fall-height")
        .description("The fall to claim. Damage keeps climbing with it, and it is clamped to the tallest clear landing above you.")
        .defaultValue(22)
        .min(1)
        .max(BLOCKS_PER_PACKET * MAX_PACKETS)
        .sliderRange(1, BLOCKS_PER_PACKET * MAX_PACKETS)
        .build()
    );

    public MaceKill() {
        super(Categories.Combat, "mace-kill", "Fakes a huge fall so mace smash attacks hit far harder.");
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (!(event.packet instanceof ServerboundAttackPacket)) return;
        if (!(mc.player.getMainHandItem().getItem() instanceof MaceItem)) return;

        // An elytra rules out a smash outright, and the server refuses to build fall distance while we
        // are in water, so either case would desync us for nothing.
        if (mc.player.isFallFlying() || mc.player.isInWater()) return;

        int height = findHeight();
        if (height <= MaceItem.SMASH_ATTACK_FALL_THRESHOLD) return;

        // Each of these raises what the rise behind them is allowed to claim, and standing still with no
        // rotation refills the packet budget they are spending.
        for (int i = 0; i < primingPackets(height); i++) send(0);

        send(height);
        send(0);
    }

    /**
     * The tallest claim whose landing box is clear, searched downwards so the best one wins. Blocks
     * between here and there are irrelevant - only the far end is checked.
     */
    private int findHeight() {
        AABB box = mc.player.getBoundingBox();

        for (int height = fallHeight.get(); height >= 1; height--) {
            if (!mc.level.getBlockCollisions(mc.player, box.move(0, height, 0)).iterator().hasNext()) {
                return height;
            }
        }

        return 0;
    }

    /**
     * How many packets to send ahead of the rise for it to be allowed to claim the height. Two is the
     * floor, because one alone leaves the rise on the smallest allowance.
     */
    private int primingPackets(int height) {
        int needed = (height + BLOCKS_PER_PACKET - 1) / BLOCKS_PER_PACKET;

        return Math.clamp(needed, 2, MAX_PACKETS);
    }

    private void send(double height) {
        ServerboundMovePlayerPacket packet = new ServerboundMovePlayerPacket.Pos(
            mc.player.getX(), mc.player.getY() + height, mc.player.getZ(), false, false
        );

        // NoFall rewrites the on ground flag of anything it does not recognise, and an on ground packet
        // wipes the fall distance this whole sequence exists to build.
        ((IServerboundMovePlayerPacket) packet).meteor$setTag(1337);

        mc.player.connection.send(packet);
    }
}
