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
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Fills the holes around a target so they have nowhere safe to stand.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code hole-filler}, kept alongside it
 * under the {@code l} suffix the rest of this port uses for name collisions. Meteor's fills holes
 * around a target with a great deal of control over which; this one searches around where the target
 * is <i>going</i> and refuses to fill the one you are in yourself.
 *
 * <p>Both details come from the same problem. Filling the hole under a stationary player is a race you
 * usually lose, and filling your own is how you end up standing on the surface in a crystal fight -
 * which is the situation the module exists to create for somebody else.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class HoleFillerl extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("blocks")
        .description("What to fill with.")
        .defaultValue(Blocks.OBSIDIAN)
        .filter(block -> block.asItem() instanceof BlockItem)
        .build()
    );

    private final Setting<Double> targetRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("target-range")
        .description("How far away a target may be.")
        .defaultValue(6)
        .min(1)
        .sliderRange(1, 16)
        .build()
    );

    private final Setting<Integer> area = sgGeneral.add(new IntSetting.Builder()
        .name("area")
        .description("How far around a target's feet to look for holes.")
        .defaultValue(2)
        .min(1)
        .sliderRange(1, 5)
        .build()
    );

    private final Setting<Integer> lead = sgGeneral.add(new IntSetting.Builder()
        .name("lead")
        .description("Ticks of the target's movement to search ahead by.")
        .defaultValue(2)
        .min(0)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<Double> reach = sgGeneral.add(new DoubleSetting.Builder()
        .name("reach")
        .description("How far away a hole may be to fill it.")
        .defaultValue(4.5)
        .min(1)
        .sliderRange(1, 6)
        .build()
    );

    private final Setting<Boolean> preventSelfFill = sgGeneral.add(new BoolSetting.Builder()
        .name("prevent-self-fill")
        .description("Never fill the hole you are standing in.")
        .defaultValue(true)
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
        .defaultValue(new SettingColor(255, 120, 40, 50))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .defaultValue(new SettingColor(255, 120, 40, 200))
        .visible(render::get)
        .build()
    );

    private final List<BlockPos> targets = new ArrayList<>();

    public HoleFillerl() {
        super(Categories.World, "hole-fillerl", "Fills holes around where a target is heading.");
    }

    @Override
    public void onDeactivate() {
        targets.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        scan();
        if (targets.isEmpty()) return;

        FindItemResult block = InvUtils.findInHotbar(stack -> stack.getItem() instanceof BlockItem item && blocks.get().contains(item.getBlock()));
        if (!block.found()) return;

        int placed = 0;

        for (BlockPos pos : targets) {
            if (placed >= perTick.get()) break;
            if (mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) > reach.get()) continue;

            if (BlockUtils.place(pos, block, rotate.get(), 50)) placed++;
        }
    }

    private void scan() {
        targets.clear();

        BlockPos own = mc.player.blockPosition();

        for (Player player : mc.level.players()) {
            if (player == mc.player || !player.isAlive() || !Friends.get().shouldAttack(player)) continue;
            if (mc.player.distanceTo(player) > targetRange.get()) continue;

            // Searched around where they will be, since a hole filled under a stationary player is a
            // race you usually lose.
            BlockPos centre = BlockPos.containing(player.position().add(player.getDeltaMovement().scale(lead.get())));

            for (int x = -area.get(); x <= area.get(); x++) {
                for (int z = -area.get(); z <= area.get(); z++) {
                    BlockPos pos = centre.offset(x, 0, z);

                    if (preventSelfFill.get() && pos.equals(own)) continue;
                    if (!isHole(pos) || targets.contains(pos)) continue;

                    targets.add(pos);
                }
            }
        }
    }

    /** A one-block gap with a floor and four walls - the thing that makes a crystal survivable. */
    private boolean isHole(BlockPos pos) {
        if (!mc.level.getBlockState(pos).canBeReplaced()) return false;
        if (mc.level.getBlockState(pos.below()).getCollisionShape(mc.level, pos.below()).isEmpty()) return false;

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = pos.relative(direction);

            if (mc.level.getBlockState(side).getCollisionShape(mc.level, side).isEmpty()) return false;
        }

        return true;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get()) return;

        for (BlockPos pos : targets) event.renderer.box(pos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
    }

    @Override
    public String getInfoString() {
        return targets.isEmpty() ? null : Integer.toString(targets.size());
    }
}
