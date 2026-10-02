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
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockIterator;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Boxes chosen blocks through walls, drawing a run of them as one outline rather than a grid.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code block-esp}, kept alongside it
 * under the {@code l} suffix the rest of this port uses for name collisions. Meteor's has per-block
 * configuration and tracers; the difference here is merging.
 *
 * <p>A vein of forty ores drawn as forty boxes is a solid block of lines you cannot see through or
 * count. Hiding the faces between touching blocks leaves the outline of the vein itself, which reads
 * as one thing at a glance and shows its shape - and it is also far less to draw.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class BlockESPl extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<java.util.List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("blocks")
        .description("What to draw.")
        .defaultValue(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.ANCIENT_DEBRIS)
        .build()
    );

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range")
        .description("How far out to look, in blocks.")
        .defaultValue(32)
        .min(1)
        .sliderRange(8, 128)
        .build()
    );

    private final Setting<Boolean> merge = sgRender.add(new BoolSetting.Builder()
        .name("merge-adjacent")
        .description("Hide the faces between touching blocks, so a vein reads as one outline.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> fade = sgRender.add(new DoubleSetting.Builder()
        .name("fade")
        .description("How much to fade a box out with distance.")
        .defaultValue(0.5)
        .min(0)
        .max(1)
        .sliderRange(0, 1)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> color = sgRender.add(new ColorSetting.Builder()
        .name("color")
        .defaultValue(new SettingColor(255, 179, 72, 180))
        .build()
    );

    /** Found positions, keyed long so the adjacency test is a set lookup rather than a search. */
    private LongSet found = new LongOpenHashSet();
    private final LongSet building = new LongOpenHashSet();

    private final Color faded = new Color();
    private final BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();

    public BlockESPl() {
        super(Categories.Render, "block-espl", "Boxes chosen blocks, merging a run of them into one outline.");
    }

    @Override
    public void onDeactivate() {
        found = new LongOpenHashSet();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        building.clear();

        BlockIterator.register(range.get(), range.get(), (pos, state) -> {
            if (blocks.get().contains(state.getBlock())) building.add(pos.asLong());
        });

        BlockIterator.after(() -> found = new LongOpenHashSet(building));
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        for (long packed : found) {
            BlockPos pos = BlockPos.of(packed);

            double distance = Math.sqrt(mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
            double strength = 1 - fade.get() * Math.clamp(distance / range.get(), 0, 1);

            faded.set(color.get().r, color.get().g, color.get().b, (int) (color.get().a * strength));

            event.renderer.box(pos, faded, faded, shapeMode.get(), merge.get() ? hiddenFaces(pos) : 0);
        }
    }

    /**
     * A bit mask of the faces to leave out, being the ones with another drawn block against them. The
     * renderer takes it directly, so merging costs one set lookup per face and nothing else.
     */
    private int hiddenFaces(BlockPos pos) {
        int excluded = 0;

        for (Direction direction : Direction.values()) {
            neighbour.set(pos).move(direction);

            if (found.contains(neighbour.asLong())) excluded |= meteordevelopment.meteorclient.utils.world.Dir.get(direction);
        }

        return excluded;
    }

    @Override
    public String getInfoString() {
        return found.isEmpty() ? null : Integer.toString(found.size());
    }
}
