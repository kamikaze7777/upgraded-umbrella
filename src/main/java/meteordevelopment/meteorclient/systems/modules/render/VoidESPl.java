/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Marks the gaps in the floor ahead of you that have nothing under them.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code void-esp}, kept alongside it under
 * the {@code l} suffix the rest of this port uses for name collisions. Meteor's looks for holes through
 * the bedrock layers, which is the End and the Nether roof; this one is general.
 *
 * <p>It finds the floor you are actually standing on, sweeps the area in front of you, and marks every
 * position on that level with no support beneath it as far down as it can see. That works on a bridge,
 * a skyblock island or a half-built platform - anywhere the danger is a gap rather than the world's
 * bottom.
 *
 * <p>The sweep runs forward and to the sides rather than as a circle, because the gap that matters is
 * the one you are walking into.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class VoidESPl extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> depth = sgGeneral.add(new IntSetting.Builder()
        .name("depth")
        .description("How far down a gap has to be clear before it counts as a drop.")
        .defaultValue(16)
        .min(2)
        .sliderRange(2, 64)
        .build()
    );

    private final Setting<Integer> forward = sgGeneral.add(new IntSetting.Builder()
        .name("forward")
        .description("How far ahead to sweep.")
        .defaultValue(8)
        .min(1)
        .sliderRange(1, 32)
        .build()
    );

    private final Setting<Integer> sideways = sgGeneral.add(new IntSetting.Builder()
        .name("sideways")
        .description("How far to either side to sweep.")
        .defaultValue(3)
        .min(0)
        .sliderRange(0, 32)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .defaultValue(ShapeMode.Sides)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgGeneral.add(new ColorSetting.Builder()
        .name("side-color")
        .defaultValue(new SettingColor(255, 0, 0, 90))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgGeneral.add(new ColorSetting.Builder()
        .name("line-color")
        .defaultValue(new SettingColor(255, 0, 0, 200))
        .build()
    );

    /** Positions found on the last tick, keyed long so the sweep does not allocate per position. */
    private LongSet gaps = new LongOpenHashSet();

    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();

    public VoidESPl() {
        super(Categories.Render, "void-espl", "Marks gaps in the floor ahead of you with nothing under them.");
    }

    @Override
    public void onDeactivate() {
        gaps.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        LongSet found = new LongOpenHashSet();

        BlockPos floor = findFloor();
        if (floor == null) {
            gaps = found;
            return;
        }

        Direction facing = mc.player.getDirection();
        Direction right = facing.getClockWise();

        for (int ahead = -1; ahead <= forward.get(); ahead++) {
            for (int across = -sideways.get(); across <= sideways.get(); across++) {
                cursor.set(floor).move(facing, ahead).move(right, across);

                if (isDrop(cursor)) found.add(cursor.asLong());
            }
        }

        gaps = found;
    }

    /**
     * The first solid block under you within the search depth. Everything is measured from that level,
     * so the sweep follows the surface you are on rather than a fixed height.
     */
    private BlockPos findFloor() {
        cursor.set(mc.player.blockPosition()).move(Direction.DOWN);

        for (int i = 0; i < depth.get(); i++) {
            if (!mc.level.getBlockState(cursor).getCollisionShape(mc.level, cursor).isEmpty()) return cursor.immutable();

            cursor.move(Direction.DOWN);
        }

        return null;
    }

    /** Whether this position, and everything under it within the depth, is empty. */
    private boolean isDrop(BlockPos pos) {
        for (int i = 0; i < depth.get(); i++) {
            probe.set(pos.getX(), pos.getY() - i, pos.getZ());

            if (probe.getY() < mc.level.getMinY()) return true;

            BlockState state = mc.level.getBlockState(probe);
            if (!state.getCollisionShape(mc.level, probe).isEmpty()) return false;
        }

        return true;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        for (long packed : gaps) {
            BlockPos pos = BlockPos.of(packed);

            event.renderer.box(pos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
        }
    }

    @Override
    public String getInfoString() {
        return gaps.isEmpty() ? null : Integer.toString(gaps.size());
    }
}
