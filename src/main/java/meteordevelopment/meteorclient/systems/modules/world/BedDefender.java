/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.world;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockIterator;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

import java.util.ArrayList;
import java.util.List;

/**
 * Packs blocks around your own bed as fast as you can place them.
 *
 * <p>The nearest bed is found, both of its halves are walked, and every open face gets a block. Doing
 * it by hand costs the seconds at the start of a game that decide it, and the order matters - the
 * faces closest to you go first, because those are the ones an attacker reaches first and a defence
 * half-built from the wrong end is no defence at all.
 *
 * <p>Whose bed it is has to be told to it. A bed has no owner the client can read, so this defends the
 * nearest one and offers a distance limit to keep it from wandering off to defend somebody else's.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class BedDefender extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> searchRange = sgGeneral.add(new IntSetting.Builder()
        .name("search-range")
        .description("How far to look for a bed to defend.")
        .defaultValue(16)
        .min(1)
        .sliderRange(4, 64)
        .build()
    );

    private final Setting<Double> placeRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("place-range")
        .description("How far away a face may be to still be filled.")
        .defaultValue(4.5)
        .min(1)
        .sliderRange(1, 6)
        .build()
    );

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("blocks")
        .description("What to pack the bed in, in order of preference.")
        .defaultValue(Blocks.OBSIDIAN, Blocks.ENDER_CHEST, Blocks.END_STONE)
        .filter(block -> Block.byItem(block.asItem()) != Blocks.AIR)
        .build()
    );

    private final Setting<Integer> perTick = sgGeneral.add(new IntSetting.Builder()
        .name("blocks-per-tick")
        .description("How many to place each tick.")
        .defaultValue(1)
        .min(1)
        .sliderRange(1, 8)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Face each block as it is placed.")
        .defaultValue(true)
        .build()
    );

    private final List<BlockPos> targets = new ArrayList<>();
    private BlockPos bed;

    public BedDefender() {
        super(Categories.World, "bed-defender", "Packs blocks around the nearest bed.");
    }

    @Override
    public void onDeactivate() {
        targets.clear();
        bed = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        findBed();
        if (bed == null) return;

        FindItemResult block = InvUtils.findInHotbar(stack -> stack.getItem() instanceof BlockItem item && blocks.get().contains(item.getBlock()));
        if (!block.found()) return;

        int placed = 0;

        for (BlockPos pos : targets) {
            if (placed >= perTick.get()) break;
            if (!BlockUtils.canPlace(pos)) continue;
            if (mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) > placeRange.get()) continue;

            if (BlockUtils.place(pos, block, rotate.get(), 50)) placed++;
        }
    }

    /**
     * Rebuilds the bed and its open faces. Both halves contribute, and the list is ordered by distance
     * so the side an attacker is standing on is filled before the far one.
     */
    private void findBed() {
        bed = null;
        targets.clear();

        BlockIterator.register(searchRange.get(), searchRange.get(), (pos, state) -> {
            if (bed != null) return;
            if (!(state.getBlock() instanceof BedBlock)) return;
            if (state.getValue(BedBlock.PART) != BedPart.HEAD) return;

            bed = pos.immutable();
            collectFaces(bed, state);
        });
    }

    private void collectFaces(BlockPos head, BlockState state) {
        BlockPos foot = head.relative(state.getValue(BedBlock.FACING).getOpposite());

        for (BlockPos part : new BlockPos[]{head, foot}) {
            for (Direction direction : Direction.values()) {
                BlockPos neighbour = part.relative(direction);

                // The other half of the bed is not a face to fill.
                if (neighbour.equals(head) || neighbour.equals(foot)) continue;
                if (targets.contains(neighbour)) continue;
                if (!mc.level.getBlockState(neighbour).canBeReplaced()) continue;

                targets.add(neighbour);
            }
        }

        targets.sort((a, b) -> Double.compare(mc.player.distanceToSqr(a.getX(), a.getY(), a.getZ()), mc.player.distanceToSqr(b.getX(), b.getY(), b.getZ())));
    }

    @Override
    public String getInfoString() {
        return bed == null ? "no bed" : targets.size() + " open";
    }
}
