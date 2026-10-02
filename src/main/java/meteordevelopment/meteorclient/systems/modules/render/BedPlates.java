/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockIterator;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Labels every bed in range with what is actually protecting it.
 *
 * <p>What matters in a bed game is not that a bed is there but what it is packed in - a bed under wool
 * and one under obsidian are minutes apart. The layers can be read off the world directly, so this
 * counts the blocks touching the bed and tallies them by type, giving the cost of breaking it at a
 * glance from across the map.
 *
 * <p>Only the blocks touching the bed are counted. Deeper layers are what the second trip is for, and
 * counting them would turn a readable label into a wall of numbers.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class BedPlates extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range")
        .description("How far away to keep labelling beds, in blocks.")
        .defaultValue(64)
        .min(1)
        .sliderRange(8, 128)
        .build()
    );

    private final Setting<Integer> maxBeds = sgGeneral.add(new IntSetting.Builder()
        .name("max-beds")
        .description("How many to label at once, nearest first.")
        .defaultValue(8)
        .min(1)
        .sliderRange(1, 32)
        .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Label size.")
        .defaultValue(1.5)
        .min(0.5)
        .sliderRange(0.5, 4)
        .build()
    );

    private final Setting<Boolean> showUnprotected = sgGeneral.add(new BoolSetting.Builder()
        .name("show-unprotected")
        .description("Label beds with nothing packed around them.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> textColor = sgGeneral.add(new ColorSetting.Builder()
        .name("text-color")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );

    /** Rebuilt on a tick rather than per frame - a bed's surroundings do not change between frames. */
    private final List<Plate> plates = new ArrayList<>();
    private final List<Plate> found = new ArrayList<>();
    private static final Vector3d screen = new Vector3d();

    public BedPlates() {
        super(Categories.Render, "bed-plates", "Labels beds with the blocks protecting them.");
    }

    @Override
    public void onDeactivate() {
        plates.clear();
        found.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        found.clear();

        BlockIterator.register(range.get(), range.get(), (pos, state) -> {
            if (!(state.getBlock() instanceof BedBlock)) return;

            // A bed is two blocks; counting only the head means one label per bed.
            if (state.getValue(BedBlock.PART) != BedPart.HEAD) return;

            Object2IntMap<Block> counts = countSurrounding(pos, state);
            if (counts.isEmpty() && !showUnprotected.get()) return;

            found.add(new Plate(pos.immutable(), counts, mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)));
        });

        BlockIterator.after(() -> {
            found.sort((a, b) -> Double.compare(a.distanceSq, b.distanceSq));

            plates.clear();
            plates.addAll(found.subList(0, Math.min(found.size(), maxBeds.get())));
        });
    }

    /**
     * Tallies the blocks touching either half of the bed. Both halves are walked because a bed is
     * usually boxed as a unit, and the head alone would miss half the wall.
     */
    private Object2IntMap<Block> countSurrounding(BlockPos head, BlockState state) {
        Object2IntMap<Block> counts = new Object2IntOpenHashMap<>();

        BlockPos foot = head.relative(state.getValue(BedBlock.FACING).getOpposite());

        for (BlockPos part : new BlockPos[]{head, foot}) {
            for (Direction direction : Direction.values()) {
                BlockPos neighbour = part.relative(direction);

                // The other half of the bed is not protection.
                if (neighbour.equals(head) || neighbour.equals(foot)) continue;

                BlockState neighbourState = mc.level.getBlockState(neighbour);
                if (neighbourState.isAir()) continue;

                counts.mergeInt(neighbourState.getBlock(), 1, Integer::sum);
            }
        }

        return counts;
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        TextRenderer text = TextRenderer.get();

        for (Plate plate : plates) {
            screen.set(plate.pos.getX() + 0.5, plate.pos.getY() + 1.0, plate.pos.getZ() + 0.5);
            if (!NametagUtils.to2D(screen, scale.get())) continue;

            String label = describe(plate);

            NametagUtils.begin(screen);
            text.beginBig(event.graphics);
            text.render(label, -text.getWidth(label) / 2, -text.getHeight(), textColor.get(), true);
            text.end();
            NametagUtils.end();
        }
    }

    private String describe(Plate plate) {
        if (plate.counts.isEmpty()) return "unprotected";

        StringBuilder label = new StringBuilder();

        for (Object2IntMap.Entry<Block> entry : plate.counts.object2IntEntrySet()) {
            if (!label.isEmpty()) label.append("  ");
            label.append(entry.getIntValue()).append("x ").append(entry.getKey().getName().getString());
        }

        return label.toString();
    }

    @Override
    public String getInfoString() {
        return Integer.toString(plates.size());
    }

    private record Plate(BlockPos pos, Object2IntMap<Block> counts, double distanceSq) {
    }
}
