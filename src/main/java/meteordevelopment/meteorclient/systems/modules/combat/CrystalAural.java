/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.Target;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Places and blows up end crystals, without waiting for the server to agree that they exist.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code crystal-aura}, kept alongside it
 * under the {@code l} suffix the rest of this port uses for name collisions. Meteor's is by far the
 * more configurable of the two; the two things here that it does not do are both about the gap between
 * placing a crystal and being allowed to hit it.
 *
 * <p><b>Predicted ids.</b> A crystal you place does not exist for you until the server sends the entity
 * back, which is a full round trip - long enough for someone to step out of the blast. But entity ids
 * are handed out in sequence, so the id the next crystal will get can be guessed from the last one seen
 * and attacked before it arrives. If the guess is wrong the attack hits nothing and costs nothing.
 *
 * <p><b>Marking dead.</b> A crystal that has been attacked is gone as far as the server is concerned,
 * but the client keeps drawing it until the removal packet lands. Remembering which ids have been hit
 * stops the next tick attacking the same crystal again, which is both wasted and the most obvious
 * thing an aura does.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class CrystalAural extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> targetRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("target-range")
        .description("How far away a player may be to place crystals on.")
        .defaultValue(8)
        .min(1)
        .sliderRange(1, 16)
        .build()
    );

    private final Setting<Double> placeRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("place-range")
        .description("How far away a crystal may be placed.")
        .defaultValue(4.5)
        .min(1)
        .sliderRange(1, 6)
        .build()
    );

    private final Setting<Double> breakRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("break-range")
        .description("How far away a crystal may be attacked.")
        .defaultValue(4.5)
        .min(1)
        .sliderRange(1, 6)
        .build()
    );

    private final Setting<Boolean> place = sgGeneral.add(new BoolSetting.Builder()
        .name("place")
        .description("Place crystals as well as breaking them.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> predictIds = sgGeneral.add(new BoolSetting.Builder()
        .name("predict-ids")
        .description("Attack a crystal you just placed before the server has sent it back.")
        .defaultValue(true)
        .visible(place::get)
        .build()
    );

    private final Setting<Boolean> markDead = sgGeneral.add(new BoolSetting.Builder()
        .name("mark-dead")
        .description("Stop attacking a crystal you have already hit, before the removal packet arrives.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks between actions.")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Face each crystal before acting on it.")
        .defaultValue(true)
        .build()
    );

    /** Ids already attacked, so the same crystal is not hit twice while the removal is in flight. */
    private final IntSet dead = new IntOpenHashSet();

    /** The highest entity id seen, which is what the next one is predicted from. */
    private volatile int highestId;

    private int timer;

    public CrystalAural() {
        super(Categories.Combat, "crystal-aural", "Places and breaks end crystals, predicting ids to hit them sooner.", "auto-crystal");
    }

    @Override
    public void onActivate() {
        dead.clear();
        timer = 0;
    }

    @Override
    public void onDeactivate() {
        dead.clear();
    }

    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        // Every spawned entity moves the counter along, not only crystals - the server hands ids out of
        // one sequence for everything.
        if (event.packet instanceof ClientboundAddEntityPacket packet) highestId = Math.max(highestId, packet.getId());
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (timer > 0) {
            timer--;
            return;
        }

        // Anything that has actually been removed can be forgotten; the set only needs to cover the
        // round trip.
        dead.removeIf((int id) -> mc.level.getEntity(id) == null);

        if (attackCrystal()) return;
        if (place.get()) placeCrystal();
    }

    /** Attacks the nearest crystal that has not already been hit. */
    private boolean attackCrystal() {
        EndCrystal best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof EndCrystal crystal)) continue;
            if (markDead.get() && dead.contains(crystal.getId())) continue;

            double distance = mc.player.distanceTo(crystal);
            if (distance > breakRange.get() || distance >= bestDistance) continue;

            best = crystal;
            bestDistance = distance;
        }

        if (best == null) return false;

        attack(best.getId(), best.getBoundingBox().getCenter());
        return true;
    }

    /**
     * Places a crystal next to a target and, if predicting, swings at the id it is about to get. The
     * prediction costs one wasted packet when it is wrong and saves a round trip when it is right.
     */
    private void placeCrystal() {
        FindItemResult crystal = InvUtils.findInHotbar(Items.END_CRYSTAL);
        if (!crystal.found()) return;

        Player target = findTarget();
        if (target == null) return;

        BlockPos base = findBase(target);
        if (base == null) return;

        if (!BlockUtils.place(base.above(), crystal, rotate.get(), 50)) return;

        timer = delay.get();

        if (!predictIds.get()) return;

        // The next entity the server creates gets the next id. Attacking it now is a swing at something
        // that does not exist yet locally, which the server resolves in the order it receives them.
        int predicted = highestId + 1;
        attack(predicted, Vec3.atCenterOf(base.above()));
    }

    /** An obsidian or bedrock block next to the target with room above it for the crystal. */
    private BlockPos findBase(Player target) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos feet = target.blockPosition();

        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            for (int y = -1; y <= 1; y++) {
                pos.set(feet.relative(direction)).move(0, y, 0);

                if (!mc.level.getBlockState(pos).is(Blocks.OBSIDIAN) && !mc.level.getBlockState(pos).is(Blocks.BEDROCK)) continue;
                if (!mc.level.getBlockState(pos.above()).isAir()) continue;

                double distance = mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(pos));
                if (distance > placeRange.get() || distance >= bestDistance) continue;

                best = pos.immutable();
                bestDistance = distance;
            }
        }

        return best;
    }

    private void attack(int id, Vec3 at) {
        Runnable swing = () -> {
            mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundAttackPacket(id));
            mc.player.swing(InteractionHand.MAIN_HAND);

            if (markDead.get()) dead.add(id);
        };

        if (rotate.get()) Rotations.rotate(Rotations.getYaw(at), Rotations.getPitch(at), 50, swing);
        else swing.run();

        timer = delay.get();
    }

    private Player findTarget() {
        Player best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Player player : mc.level.players()) {
            if (player == mc.player || !player.isAlive() || !Friends.get().shouldAttack(player)) continue;

            double distance = mc.player.distanceTo(player);
            if (distance > targetRange.get() || distance >= bestDistance) continue;

            best = player;
            bestDistance = distance;
        }

        return best;
    }
}
