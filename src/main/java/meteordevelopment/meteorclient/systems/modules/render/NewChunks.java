/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import it.unimi.dsi.fastutil.longs.Long2BooleanMap;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
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
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * Marks which loaded chunks have been visited before and which are freshly generated.
 *
 * <p>The tell is flowing liquid. A chunk that has only just been generated has water and lava in
 * states the generator left behind, and the server settles them the moment it loads the chunk - which
 * it sends you as block updates carrying a non-source fluid. A chunk that was generated long ago has
 * already settled and sends no such update.
 *
 * <p>So a chunk that arrives and then reports flowing liquid is new; one that arrives quietly has been
 * loaded before, which is what makes this a way to follow where people have been.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class NewChunks extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> renderDistance = sgGeneral.add(new IntSetting.Builder()
        .name("render-distance")
        .description("How far away to keep drawing marks, in chunks.")
        .defaultValue(32)
        .min(1)
        .sliderRange(4, 128)
        .build()
    );

    private final Setting<Boolean> remember = sgGeneral.add(new BoolSetting.Builder()
        .name("remember")
        .description("Keep chunks after they unload, so a route stays drawn behind you.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoHeight = sgRender.add(new BoolSetting.Builder()
        .name("auto-height")
        .description("Draw well below you rather than at a fixed height.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> height = sgRender.add(new DoubleSetting.Builder()
        .name("height")
        .description("The height to draw at.")
        .defaultValue(0)
        .sliderRange(-64, 320)
        .visible(() -> !autoHeight.get())
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .defaultValue(ShapeMode.Sides)
        .build()
    );

    private final Setting<SettingColor> newColor = sgRender.add(new ColorSetting.Builder()
        .name("new-color")
        .description("Chunks that were generated as you arrived.")
        .defaultValue(new SettingColor(0, 255, 0, 80))
        .build()
    );

    private final Setting<SettingColor> oldColor = sgRender.add(new ColorSetting.Builder()
        .name("old-color")
        .description("Chunks that were already generated.")
        .defaultValue(new SettingColor(255, 0, 0, 80))
        .build()
    );

    /** Chunk position to whether it looked freshly generated. Keyed long to avoid an object per chunk. */
    private final Long2BooleanMap chunks = new Long2BooleanOpenHashMap();

    public NewChunks() {
        super(Categories.Render, "new-chunks", "Marks which chunks were freshly generated and which were already loaded.");
    }

    @Override
    public void onDeactivate() {
        chunks.clear();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        chunks.clear();
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        switch (event.packet) {
            case ClientboundLevelChunkWithLightPacket packet ->
                // Recorded as old until something proves otherwise. Nothing is drawn differently for a
                // chunk that simply arrived.
                mc.execute(() -> chunks.putIfAbsent(ChunkPos.pack(packet.getX(), packet.getZ()), false));

            case ClientboundForgetLevelChunkPacket packet -> {
                if (!remember.get()) mc.execute(() -> chunks.remove(packet.pos().pack()));
            }

            case ClientboundBlockUpdatePacket packet -> mc.execute(() -> markIfFlowing(packet.getPos(), packet.getBlockState()));

            case ClientboundSectionBlocksUpdatePacket packet -> mc.execute(() -> packet.runUpdates(this::markIfFlowing));

            default -> {
            }
        }
    }

    /**
     * A liquid that is not a source is a liquid mid-settle, which only happens on a chunk the server has
     * just built.
     */
    private void markIfFlowing(BlockPos pos, BlockState state) {
        FluidState fluid = state.getFluidState();
        if (fluid.isEmpty() || fluid.isSource()) return;

        chunks.put(ChunkPos.pack(pos), true);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (chunks.isEmpty()) return;

        double y = autoHeight.get() ? mc.player.getY() - 100 : height.get();
        double maxDistance = renderDistance.get() * 16.0;
        double maxDistanceSq = maxDistance * maxDistance;

        for (Long2BooleanMap.Entry entry : chunks.long2BooleanEntrySet()) {
            ChunkPos pos = ChunkPos.unpack(entry.getLongKey());

            double dx = pos.getMiddleBlockX() - mc.player.getX();
            double dz = pos.getMiddleBlockZ() - mc.player.getZ();
            if (dx * dx + dz * dz > maxDistanceSq) continue;

            SettingColor color = entry.getBooleanValue() ? newColor.get() : oldColor.get();

            // Drawn as a flat slab rather than a plane so it reads from above and from the side.
            event.renderer.box(
                pos.getMinBlockX(), y, pos.getMinBlockZ(),
                pos.getMaxBlockX() + 1.0, y, pos.getMaxBlockZ() + 1.0,
                color, color, shapeMode.get(), 0
            );
        }
    }
}
