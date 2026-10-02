/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Hides everything except the blocks worth digging for, and lights what is left.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code xray}, kept alongside it under
 * the {@code l} suffix the rest of this port uses for name collisions. The two differ in what they do
 * with the block once it is the only thing left.
 *
 * <p>An ore with every surrounding block hidden is lit by whatever light reaches it, which underground
 * is nothing - so the thing you cleared the view for is a black shape. Lighting the kept blocks fully
 * is what makes the module usable rather than merely correct.
 *
 * <p><b>Exposed only</b> is the other half: an ore with air against it is one somebody has already dug
 * to, or one in a cave. Hiding those leaves the untouched veins, which is what you are looking for.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class XRayl extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("blocks")
        .description("The only blocks that stay visible.")
        .defaultValue(
            Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE,
            Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE,
            Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE,
            Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE,
            Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
            Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
            Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
            Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
            Blocks.ANCIENT_DEBRIS, Blocks.NETHER_GOLD_ORE, Blocks.NETHER_QUARTZ_ORE
        )
        .build()
    );

    private final Setting<Boolean> fullBright = sgGeneral.add(new BoolSetting.Builder()
        .name("full-bright")
        .description("Light the kept blocks fully. Without this they are black shapes underground.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> exposedOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("exposed-only")
        .description("Only show ores with air against them - the ones already dug to or in a cave.")
        .defaultValue(false)
        .build()
    );

    public XRayl() {
        super(Categories.Render, "x-rayl", "Hides everything but chosen blocks, and lights what is left.");
    }

    @Override
    public void onActivate() {
        reload();
    }

    @Override
    public void onDeactivate() {
        reload();
    }

    /** The world has to be rebuilt for a visibility change to take effect. */
    private void reload() {
        if (mc.levelExtractor != null) mc.levelExtractor.allChanged();
    }

    /** Whether this block should be drawn at all. */
    public boolean shouldRender(BlockState state, BlockPos pos) {
        if (!isActive()) return true;
        if (!blocks.get().contains(state.getBlock())) return false;

        return !exposedOnly.get() || isExposed(pos);
    }

    public boolean fullBright() {
        return isActive() && fullBright.get();
    }

    /** An ore with air on any side has already been reached by somebody, or opens onto a cave. */
    private boolean isExposed(BlockPos pos) {
        if (mc.level == null) return true;

        for (Direction direction : Direction.values()) {
            if (mc.level.getBlockState(pos.relative(direction)).isAir()) return true;
        }

        return false;
    }
}
