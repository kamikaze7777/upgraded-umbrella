/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.world;

import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
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
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * Breaks blocks by sending the start and stop packets directly, with no animation and no held mining.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code packet-mine}, kept alongside it
 * under the {@code l} suffix the rest of this port uses for name collisions. Meteor's mines one block
 * at a time and re-aims at it; this one keeps a queue.
 *
 * <p>That is the difference worth having. The server tracks break progress per position, not per
 * player, so several blocks can be part-way broken at once - start each of them, wait out the longest,
 * and finish them together. Against a wall of obsidian that is the difference between one gap and a
 * hole you can walk through.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class PacketMinel extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> maxBlocks = sgGeneral.add(new IntSetting.Builder()
        .name("max-blocks")
        .description("How many blocks to keep breaking at once.")
        .defaultValue(2)
        .min(1)
        .sliderRange(1, 8)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks between the start and the finish of each block.")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 40)
        .build()
    );

    private final Setting<Boolean> autoRemove = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-remove")
        .description("Drop a block from the queue once it is gone or out of reach.")
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
        .defaultValue(new SettingColor(255, 60, 60, 40))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .defaultValue(new SettingColor(255, 60, 60, 180))
        .visible(render::get)
        .build()
    );

    /** Blocks that have been started and are waiting to be finished. */
    private final List<Mining> queue = new ArrayList<>();

    public PacketMinel() {
        super(Categories.World, "packet-minel", "Breaks several blocks at once by sending the packets directly.");
    }

    @Override
    public void onDeactivate() {
        queue.clear();
    }

    @EventHandler
    private void onStartBreakingBlock(StartBreakingBlockEvent event) {
        // The break is taken over entirely - the client's own progress would fight with the packets.
        event.cancel();

        if (queue.size() >= maxBlocks.get()) return;
        for (Mining mining : queue) if (mining.pos.equals(event.blockPos)) return;

        mc.player.connection.send(new ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, event.blockPos, event.direction
        ));

        queue.add(new Mining(event.blockPos, event.direction, 0));
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        queue.removeIf(mining -> {
            if (autoRemove.get() && (mc.level.getBlockState(mining.pos).isAir() || !BlockUtils.canBreak(mining.pos))) return true;

            if (mining.age++ < delay.get()) return false;

            // The server holds the progress per position, so the finish can be sent for each block
            // independently and they all fall together.
            mc.player.connection.send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, mining.pos, mining.direction
            ));

            return true;
        });
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get()) return;

        for (Mining mining : queue) event.renderer.box(mining.pos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
    }

    @Override
    public String getInfoString() {
        return queue.isEmpty() ? null : Integer.toString(queue.size());
    }

    private static class Mining {
        final BlockPos pos;
        final Direction direction;
        int age;

        Mining(BlockPos pos, Direction direction, int age) {
            this.pos = pos;
            this.direction = direction;
            this.age = age;
        }
    }
}
