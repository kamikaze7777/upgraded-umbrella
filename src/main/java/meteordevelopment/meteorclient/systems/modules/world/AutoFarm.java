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
import meteordevelopment.meteorclient.utils.world.BlockIterator;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Harvests fully grown crops around you and puts the seed back.
 *
 * <p>Ripeness is a block state the server sends, so which crops are ready is known exactly rather than
 * guessed at from the texture. Wheat, carrots, potatoes, beetroot and nether wart all carry an age
 * that tops out at a value the block itself declares, which is what this compares against rather than
 * hard-coding a number per crop.
 *
 * <p>Replanting is the half that makes it a farm rather than a harvester, and it runs the tick after
 * the break so the block has actually gone before something is placed into it.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class AutoFarm extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range")
        .description("How far to look for crops.")
        .defaultValue(5)
        .min(1)
        .sliderRange(1, 8)
        .build()
    );

    private final Setting<Double> reach = sgGeneral.add(new DoubleSetting.Builder()
        .name("reach")
        .description("How far away a crop may be to act on it.")
        .defaultValue(4.5)
        .min(1)
        .sliderRange(1, 6)
        .build()
    );

    private final Setting<Boolean> replant = sgGeneral.add(new BoolSetting.Builder()
        .name("replant")
        .description("Put the seed back after harvesting.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks between actions.")
        .defaultValue(2)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Face each block as it is acted on.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Outline the crops that are ready.")
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
        .defaultValue(new SettingColor(120, 255, 120, 40))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .defaultValue(new SettingColor(120, 255, 120, 180))
        .visible(render::get)
        .build()
    );

    private final List<BlockPos> ripe = new ArrayList<>();
    private final List<BlockPos> empty = new ArrayList<>();
    private int timer;

    public AutoFarm() {
        super(Categories.World, "auto-farm", "Harvests grown crops around you and replants them.");
    }

    @Override
    public void onDeactivate() {
        ripe.clear();
        empty.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        scan();

        if (timer > 0) {
            timer--;
            return;
        }

        for (BlockPos pos : ripe) {
            if (!inReach(pos)) continue;

            BlockUtils.breakBlock(pos, true);
            timer = delay.get();
            return;
        }

        if (!replant.get()) return;

        for (BlockPos pos : empty) {
            if (!inReach(pos)) continue;

            FindItemResult seed = InvUtils.findInHotbar(stack -> stack.getItem() instanceof BlockItem item && isCrop(item.getBlock().defaultBlockState()));
            if (!seed.found()) return;

            if (BlockUtils.place(pos, seed, rotate.get(), 50)) {
                timer = delay.get();
                return;
            }
        }
    }

    private boolean inReach(BlockPos pos) {
        return mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) <= reach.get();
    }

    /** Splits what is in range into what is ready to take and what is ready to be planted into. */
    private void scan() {
        ripe.clear();
        empty.clear();

        BlockIterator.register(range.get(), range.get(), (pos, state) -> {
            if (isCrop(state)) {
                if (isRipe(state)) ripe.add(pos.immutable());
                return;
            }

            // Tilled soil with nothing on it is where a seed goes back.
            if (replant.get() && state.is(Blocks.FARMLAND) && mc.level.getBlockState(pos.above()).isAir()) {
                empty.add(pos.above());
            }
        });
    }

    private boolean isCrop(BlockState state) {
        return state.getBlock() instanceof CropBlock || state.getBlock() instanceof NetherWartBlock;
    }

    /**
     * Compares the age against the maximum the block itself declares, so each crop is judged by its own
     * growth stages rather than by a number written down here.
     */
    private boolean isRipe(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) return crop.isMaxAge(state);

        return state.hasProperty(BlockStateProperties.AGE_3) && state.getValue(BlockStateProperties.AGE_3) >= 3;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get()) return;

        for (BlockPos pos : ripe) event.renderer.box(pos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
    }

    @Override
    public String getInfoString() {
        return Integer.toString(ripe.size());
    }
}
