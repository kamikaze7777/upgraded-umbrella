/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.world;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Fills the liquid around you in, and can use a sponge to do it in one go.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code liquid-filler}, kept alongside it
 * under the {@code l} suffix the rest of this port uses for name collisions. Meteor's places a block
 * into each source; the addition here is the sponge.
 *
 * <p>A sponge dries a seven-block radius of water on placement and can then be picked straight back
 * up, so one placement does what a dozen blocks would - and leaves nothing behind to give away that
 * you were there. It does nothing to lava, which is why the block list is still the fallback.
 *
 * <p>The order matters too: filling the furthest source first means the ones nearest you stay open as
 * long as possible, which is what keeps you able to reach the rest.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class LiquidFillerl extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> water = sgGeneral.add(new BoolSetting.Builder()
        .name("water")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> lava = sgGeneral.add(new BoolSetting.Builder()
        .name("lava")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> useSponge = sgGeneral.add(new BoolSetting.Builder()
        .name("use-sponge")
        .description("Dry water with a sponge where one is available, instead of filling each source.")
        .defaultValue(true)
        .visible(water::get)
        .build()
    );

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("blocks")
        .description("What to fill with.")
        .defaultValue(Blocks.COBBLESTONE, Blocks.DIRT, Blocks.NETHERRACK, Blocks.STONE)
        .filter(block -> block.asItem() instanceof BlockItem)
        .build()
    );

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range")
        .description("How far out to look for liquid.")
        .defaultValue(4)
        .min(1)
        .sliderRange(1, 8)
        .build()
    );

    private final Setting<Double> reach = sgGeneral.add(new DoubleSetting.Builder()
        .name("reach")
        .description("How far away a source may be to fill it.")
        .defaultValue(4.5)
        .min(1)
        .sliderRange(1, 6)
        .build()
    );

    private final Setting<Integer> perTick = sgGeneral.add(new IntSetting.Builder()
        .name("blocks-per-tick")
        .defaultValue(1)
        .min(1)
        .sliderRange(1, 8)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .defaultValue(true)
        .build()
    );

    private final List<BlockPos> targets = new ArrayList<>();

    public LiquidFillerl() {
        super(Categories.World, "liquid-fillerl", "Fills liquid around you, using a sponge for water where it can.");
    }

    @Override
    public void onDeactivate() {
        targets.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        scan();
        if (targets.isEmpty()) return;

        FindItemResult sponge = useSponge.get() && water.get() ? InvUtils.findInHotbar(Items.SPONGE) : null;
        FindItemResult filler = InvUtils.findInHotbar(stack -> stack.getItem() instanceof BlockItem item && blocks.get().contains(item.getBlock()));

        int placed = 0;

        for (BlockPos pos : targets) {
            if (placed >= perTick.get()) break;
            if (mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) > reach.get()) continue;

            // One sponge dries a whole radius of water, so it is worth using wherever the source is
            // water rather than lava.
            boolean isWater = mc.level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER);

            FindItemResult item = isWater && sponge != null && sponge.found() ? sponge : filler;
            if (item == null || !item.found()) continue;

            if (BlockUtils.place(pos, item, rotate.get(), 50)) placed++;
        }
    }

    /** Every liquid source in range, furthest first so the near ones stay reachable longest. */
    private void scan() {
        targets.clear();

        BlockIterator.register(range.get(), range.get(), (pos, state) -> {
            var fluid = state.getFluidState();
            if (fluid.isEmpty() || !fluid.isSource()) return;

            boolean isWater = fluid.is(net.minecraft.tags.FluidTags.WATER);
            if (isWater && !water.get()) return;
            if (!isWater && !lava.get()) return;

            targets.add(pos.immutable());
        });

        BlockIterator.after(() -> targets.sort((a, b) -> Double.compare(
            mc.player.distanceToSqr(b.getX(), b.getY(), b.getZ()),
            mc.player.distanceToSqr(a.getX(), a.getY(), a.getZ())
        )));
    }

    @Override
    public String getInfoString() {
        return targets.isEmpty() ? null : Integer.toString(targets.size());
    }
}
