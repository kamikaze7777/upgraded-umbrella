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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Boxes storage blocks through walls, and stops drawing the ones you have already been through.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code storage-esp}, kept alongside it
 * under the {@code l} suffix the rest of this port uses for name collisions. Meteor's is the richer of
 * the two on colours and shapes; the piece here is the memory.
 *
 * <p>A container you have opened is a container you have emptied, and it staying lit is noise for the
 * rest of the session. Remembering which ones you have opened and dropping them from the display turns
 * a base full of boxes into a list of what is left - which is the difference between the module being
 * useful in a stash and being a wall of colour.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class StorageESPl extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range")
        .description("How far out to look, in blocks.")
        .defaultValue(32)
        .min(1)
        .sliderRange(8, 128)
        .build()
    );

    private final Setting<Boolean> hideOpened = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-opened")
        .description("Stop drawing a container once you have opened it.")
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

    private final Setting<SettingColor> chestColor = sgRender.add(new ColorSetting.Builder()
        .name("chest-color")
        .defaultValue(new SettingColor(0, 100, 255, 180))
        .build()
    );

    private final Setting<SettingColor> shulkerColor = sgRender.add(new ColorSetting.Builder()
        .name("shulker-color")
        .defaultValue(new SettingColor(150, 80, 150, 180))
        .build()
    );

    private final Setting<SettingColor> enderColor = sgRender.add(new ColorSetting.Builder()
        .name("ender-chest-color")
        .defaultValue(new SettingColor(200, 0, 200, 180))
        .build()
    );

    private final Setting<SettingColor> otherColor = sgRender.add(new ColorSetting.Builder()
        .name("other-color")
        .description("Furnaces, hoppers, dispensers and the rest.")
        .defaultValue(new SettingColor(120, 120, 120, 180))
        .build()
    );

    /** Positions already opened this session, kept across scans so they stay hidden. */
    private final LongSet opened = new LongOpenHashSet();

    private final List<Found> found = new ArrayList<>();
    private final List<Found> visible = new ArrayList<>();
    private final Color faded = new Color();

    public StorageESPl() {
        super(Categories.Render, "storage-espl", "Boxes storage blocks, hiding the ones you have already opened.", "chest-espl");
    }

    @Override
    public void onDeactivate() {
        visible.clear();
        found.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        // Whatever container is open right now is one you have been through.
        if (hideOpened.get() && mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>
            && mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit) {
            opened.add(hit.getBlockPos().asLong());
        }

        found.clear();

        BlockIterator.register(range.get(), range.get(), (pos, state) -> {
            SettingColor color = colorFor(state);
            if (color == null) return;

            if (hideOpened.get() && opened.contains(pos.asLong())) return;

            found.add(new Found(pos.immutable(), color));
        });

        BlockIterator.after(() -> {
            visible.clear();
            visible.addAll(found);
        });
    }

    private SettingColor colorFor(BlockState state) {
        if (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST) || state.is(Blocks.BARREL)) return chestColor.get();
        if (state.is(Blocks.ENDER_CHEST)) return enderColor.get();
        if (state.getBlock() instanceof net.minecraft.world.level.block.ShulkerBoxBlock) return shulkerColor.get();

        if (state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE) || state.is(Blocks.SMOKER)
            || state.is(Blocks.HOPPER) || state.is(Blocks.DISPENSER) || state.is(Blocks.DROPPER)
            || state.is(Blocks.BREWING_STAND)) return otherColor.get();

        return null;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        for (Found box : visible) {
            double distance = Math.sqrt(mc.player.distanceToSqr(box.pos.getX() + 0.5, box.pos.getY() + 0.5, box.pos.getZ() + 0.5));
            double strength = 1 - fade.get() * Math.clamp(distance / range.get(), 0, 1);

            faded.set(box.color.r, box.color.g, box.color.b, (int) (box.color.a * strength));

            event.renderer.box(box.pos, faded, faded, shapeMode.get(), 0);
        }
    }

    @Override
    public String getInfoString() {
        return visible.isEmpty() ? null : Integer.toString(visible.size());
    }

    private record Found(BlockPos pos, SettingColor color) {
    }
}
