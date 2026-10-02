/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.world;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Walls the four sides of the block you are standing in, and rebuilds them as they are broken.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code surround}, kept alongside it under
 * the {@code l} suffix the rest of this port uses for name collisions. Meteor's has a great deal of
 * control over the shape and when to toggle; this one keeps the shape simple and puts the effort into
 * holding it.
 *
 * <p>The point of a surround is not building it once - it is that the moment somebody breaks a side,
 * a crystal goes there. So the positions are rechecked every tick and refilled from the nearest gap
 * outwards, and the module stays on rather than switching itself off when the box is complete.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class Surroundl extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("blocks")
        .description("What to wall yourself in with.")
        .defaultValue(Blocks.OBSIDIAN, Blocks.ENDER_CHEST)
        .filter(block -> block.asItem() instanceof BlockItem)
        .build()
    );

    private final Setting<Boolean> doubleHeight = sgGeneral.add(new BoolSetting.Builder()
        .name("double-height")
        .description("Wall the block at head level too, against someone placing a crystal beside your head.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> floor = sgGeneral.add(new BoolSetting.Builder()
        .name("floor")
        .description("Fill the block under you, for when you are standing on nothing.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> perTick = sgGeneral.add(new IntSetting.Builder()
        .name("blocks-per-tick")
        .description("How many to place each tick. Higher rebuilds faster and is more visible.")
        .defaultValue(2)
        .min(1)
        .sliderRange(1, 8)
        .build()
    );

    private final Setting<Boolean> onlyOnGround = sgGeneral.add(new BoolSetting.Builder()
        .name("only-on-ground")
        .description("Do nothing while airborne, where the box would be built around nothing.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
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
        .defaultValue(new SettingColor(160, 60, 255, 50))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .defaultValue(new SettingColor(160, 60, 255, 200))
        .visible(render::get)
        .build()
    );

    private final List<BlockPos> gaps = new ArrayList<>();

    public Surroundl() {
        super(Categories.World, "surroundl", "Walls the sides of the block you stand in, and rebuilds them.");
    }

    @Override
    public void onDeactivate() {
        gaps.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        gaps.clear();

        if (onlyOnGround.get() && !mc.player.onGround()) return;

        BlockPos feet = mc.player.blockPosition();

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            add(feet.relative(direction));
            if (doubleHeight.get()) add(feet.above().relative(direction));
        }

        if (floor.get()) add(feet.below());

        // Nearest first, so the side someone has just opened is the one that gets refilled.
        gaps.sort((a, b) -> Double.compare(
            mc.player.distanceToSqr(a.getX() + 0.5, a.getY() + 0.5, a.getZ() + 0.5),
            mc.player.distanceToSqr(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5)
        ));

        if (gaps.isEmpty()) return;

        FindItemResult block = InvUtils.findInHotbar(stack -> stack.getItem() instanceof BlockItem item && blocks.get().contains(item.getBlock()));
        if (!block.found()) return;

        int placed = 0;

        for (BlockPos pos : gaps) {
            if (placed >= perTick.get()) break;

            if (BlockUtils.place(pos, block, rotate.get(), 50)) placed++;
        }
    }

    private void add(BlockPos pos) {
        if (mc.level.getBlockState(pos).canBeReplaced()) gaps.add(pos);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get()) return;

        for (BlockPos pos : gaps) event.renderer.box(pos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
    }

    @Override
    public String getInfoString() {
        return gaps.isEmpty() ? null : gaps.size() + " open";
    }
}
