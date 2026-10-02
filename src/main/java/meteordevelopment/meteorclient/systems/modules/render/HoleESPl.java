/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Marks the holes you can stand in to survive a crystal, fading them out with distance.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code hole-esp}, kept alongside it
 * under the {@code l} suffix the rest of this port uses for name collisions. Meteor's colours holes by
 * what they are made of; the difference here is the fade.
 *
 * <p>Every hole in render distance drawn at full strength is a field of boxes you cannot read. Fading
 * them out with distance means the ones near enough to reach stand out and the rest stay as context,
 * which is the only way the module is usable in an open fight rather than a corridor.
 *
 * <p>The classification is the same either way, because it is what the hole is worth: bedrock cannot
 * be broken and is the only truly safe one; obsidian takes long enough that it may as well be; and
 * anything else is a hole someone can open in a second.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class HoleESPl extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> horizontalRange = sgGeneral.add(new IntSetting.Builder()
        .name("horizontal-range")
        .description("How far out to look for holes.")
        .defaultValue(16)
        .min(1)
        .sliderRange(4, 64)
        .build()
    );

    private final Setting<Integer> verticalRange = sgGeneral.add(new IntSetting.Builder()
        .name("vertical-range")
        .description("How far up and down to look.")
        .defaultValue(4)
        .min(1)
        .sliderRange(1, 32)
        .build()
    );

    private final Setting<Boolean> onlySafe = sgGeneral.add(new BoolSetting.Builder()
        .name("only-safe")
        .description("Only show holes surrounded by bedrock or obsidian.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> fade = sgRender.add(new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
        .name("fade")
        .description("How much to fade a hole out with distance. Zero draws them all alike.")
        .defaultValue(0.6)
        .min(0)
        .max(1)
        .sliderRange(0, 1)
        .build()
    );

    private final Setting<Double> height = sgRender.add(new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
        .name("height")
        .description("How tall to draw each hole.")
        .defaultValue(0.15)
        .min(0)
        .sliderRange(0, 1)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> bedrockColor = sgRender.add(new ColorSetting.Builder()
        .name("bedrock-color")
        .description("Holes that cannot be opened at all.")
        .defaultValue(new SettingColor(25, 193, 92, 180))
        .build()
    );

    private final Setting<SettingColor> obsidianColor = sgRender.add(new ColorSetting.Builder()
        .name("obsidian-color")
        .description("Holes that take real time to open.")
        .defaultValue(new SettingColor(53, 186, 204, 180))
        .build()
    );

    private final Setting<SettingColor> weakColor = sgRender.add(new ColorSetting.Builder()
        .name("weak-color")
        .description("Holes someone can open in a second.")
        .defaultValue(new SettingColor(247, 56, 27, 180))
        .build()
    );

    private final List<Hole> holes = new ArrayList<>();
    private final Color faded = new Color();

    public HoleESPl() {
        super(Categories.Render, "hole-espl", "Marks holes you can survive a crystal in, faded by distance.");
    }

    @Override
    public void onDeactivate() {
        holes.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        holes.clear();

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos origin = mc.player.blockPosition();

        int h = horizontalRange.get();
        int v = verticalRange.get();

        for (int x = -h; x <= h; x++) {
            for (int y = -v; y <= v; y++) {
                for (int z = -h; z <= h; z++) {
                    pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);

                    Strength strength = classify(pos);
                    if (strength == null) continue;
                    if (onlySafe.get() && strength == Strength.Weak) continue;

                    holes.add(new Hole(pos.immutable(), strength));
                }
            }
        }
    }

    /**
     * A one-block gap with a floor and four walls, classified by the weakest thing enclosing it -
     * a hole is only as strong as the block someone chooses to break.
     */
    private Strength classify(BlockPos pos) {
        if (!mc.level.getBlockState(pos).isAir() || !mc.level.getBlockState(pos.above()).isAir()) return null;

        Strength weakest = Strength.Bedrock;

        for (Direction direction : Direction.values()) {
            if (direction == Direction.UP) continue;

            BlockPos side = pos.relative(direction);
            BlockState state = mc.level.getBlockState(side);

            if (state.getCollisionShape(mc.level, side).isEmpty()) return null;

            Strength strength = state.is(Blocks.BEDROCK) ? Strength.Bedrock
                : state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN) || state.is(Blocks.RESPAWN_ANCHOR) ? Strength.Obsidian
                : Strength.Weak;

            if (strength.ordinal() > weakest.ordinal()) weakest = strength;
        }

        return weakest;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        double maxDistance = horizontalRange.get();

        for (Hole hole : holes) {
            SettingColor base = switch (hole.strength) {
                case Bedrock -> bedrockColor.get();
                case Obsidian -> obsidianColor.get();
                case Weak -> weakColor.get();
            };

            double distance = Math.sqrt(mc.player.distanceToSqr(hole.pos.getX() + 0.5, hole.pos.getY(), hole.pos.getZ() + 0.5));
            double strength = 1 - fade.get() * Math.clamp(distance / maxDistance, 0, 1);

            faded.set(base.r, base.g, base.b, (int) (base.a * strength));

            event.renderer.box(
                hole.pos.getX(), hole.pos.getY(), hole.pos.getZ(),
                hole.pos.getX() + 1.0, hole.pos.getY() + height.get(), hole.pos.getZ() + 1.0,
                faded, faded, shapeMode.get(), 0
            );
        }
    }

    @Override
    public String getInfoString() {
        return holes.isEmpty() ? null : Integer.toString(holes.size());
    }

    /** Ordered weakest last, so the worst side of a hole is what it is classified as. */
    private enum Strength {
        Bedrock,
        Obsidian,
        Weak
    }

    private record Hole(BlockPos pos, Strength strength) {
    }
}
