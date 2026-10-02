/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.world;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds one of a few fixed shapes where you are standing.
 *
 * <p>Both shapes are things worth having in a hurry and tedious by hand: a platform to stand on, and
 * the frame of a nether portal. The positions are worked out once when the module is switched on, so
 * the structure stays where you started it rather than following you around.
 *
 * <p>The portal frame is laid out relative to the direction you are facing, and the corners are left
 * out - a portal does not need them, and four fewer blocks is four fewer placements.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class AutoBuild extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Shape> shape = sgGeneral.add(new EnumSetting.Builder<Shape>()
        .name("shape")
        .description("What to build.")
        .defaultValue(Shape.Platform)
        .build()
    );

    private final Setting<Integer> radius = sgGeneral.add(new IntSetting.Builder()
        .name("radius")
        .description("How far the platform reaches from you.")
        .defaultValue(2)
        .min(0)
        .sliderRange(0, 8)
        .visible(() -> shape.get() == Shape.Platform)
        .build()
    );

    private final Setting<Double> reach = sgGeneral.add(new DoubleSetting.Builder()
        .name("reach")
        .description("How far away a block may be to place it.")
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
        .description("Face each block as it is placed.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> disableWhenDone = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-when-done")
        .description("Switch off once every position is filled.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Outline the positions still to fill.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .defaultValue(ShapeMode.Both)
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .defaultValue(new SettingColor(120, 180, 255, 40))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .defaultValue(new SettingColor(120, 180, 255, 180))
        .visible(render::get)
        .build()
    );

    /** Every position the shape wants filled, worked out once when the module is switched on. */
    private final List<BlockPos> positions = new ArrayList<>();

    public AutoBuild() {
        super(Categories.World, "auto-build", "Builds a platform or a portal frame where you stand.", "platform", "auto-portal");
    }

    @Override
    public void onActivate() {
        positions.clear();
        if (mc.player == null) return;

        // Anchored where you were standing, not where you are now, so the shape does not drift as you
        // move around it while it builds.
        BlockPos origin = mc.player.blockPosition();

        if (shape.get() == Shape.Platform) buildPlatform(origin);
        else buildPortal(origin, mc.player.getDirection());
    }

    @Override
    public void onDeactivate() {
        positions.clear();
    }

    private void buildPlatform(BlockPos origin) {
        int r = radius.get();

        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                positions.add(origin.offset(x, -1, z));
            }
        }
    }

    /**
     * A four by five frame in the plane across your facing, without the corners - a portal does not
     * light through them and leaving them out saves four blocks.
     */
    private void buildPortal(BlockPos origin, Direction facing) {
        Direction across = facing.getClockWise();

        for (int width = 0; width < 4; width++) {
            for (int height = 0; height < 5; height++) {
                boolean edgeColumn = width == 0 || width == 3;
                boolean edgeRow = height == 0 || height == 4;

                if (edgeColumn == edgeRow) continue;

                positions.add(origin.relative(across, width - 1).above(height - 1));
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        positions.removeIf(pos -> !mc.level.getBlockState(pos).canBeReplaced());

        if (positions.isEmpty()) {
            if (disableWhenDone.get()) toggle();
            return;
        }

        FindItemResult block = InvUtils.findInHotbar(this::isBlock);
        if (!block.found()) return;

        int placed = 0;

        for (BlockPos pos : positions) {
            if (placed >= perTick.get()) break;
            if (mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) > reach.get()) continue;
            if (!BlockUtils.canPlace(pos)) continue;

            if (BlockUtils.place(pos, block, rotate.get(), 50)) placed++;
        }
    }

    private boolean isBlock(ItemStack stack) {
        return stack.getItem() instanceof BlockItem;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get()) return;

        for (BlockPos pos : positions) event.renderer.box(pos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
    }

    @Override
    public String getInfoString() {
        return positions.isEmpty() ? null : positions.size() + " left";
    }

    public enum Shape {
        /** A flat square under your feet. */
        Platform,
        /** The frame of a nether portal, across the way you were facing. */
        Portal
    }
}
