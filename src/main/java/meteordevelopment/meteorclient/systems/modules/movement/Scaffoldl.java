/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.ListMode;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Bridges for you, using one of the techniques people actually bridge with.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code scaffold}, kept alongside it under
 * the {@code l} suffix the rest of this port uses for name collisions. Meteor's places under your feet
 * with a great deal of control over where; the techniques here are the different <i>shapes</i> a
 * bridge is built in, each of which is a real thing players do by hand.
 *
 * <p><b>Normal</b> places under you, facing wherever you are going. <b>God bridge</b> holds the pitch
 * near the shallow angle that lets you sprint along the edge of the blocks rather than the top of
 * them, which is the fastest bridge there is. <b>Breezily</b> alternates the yaw a few degrees either
 * side each tick, which is what a person's hand does at speed and what makes a straight line come out
 * of it.
 *
 * <p>The same-Y option is the other half: holding the bridge to the height you started at, so walking
 * off an edge does not turn into a staircase.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class Scaffoldl extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Technique> technique = sgGeneral.add(new EnumSetting.Builder<Technique>()
        .name("technique")
        .description("The shape to bridge in.")
        .defaultValue(Technique.Normal)
        .build()
    );

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("blocks")
        .description("The blocks the filter applies to.")
        .filter(block -> block.asItem() instanceof BlockItem)
        .build()
    );

    private final Setting<ListMode> blocksFilter = sgGeneral.add(new EnumSetting.Builder<ListMode>()
        .name("blocks-filter")
        .description("Whether the list names what to use or what to avoid.")
        .defaultValue(ListMode.Blacklist)
        .build()
    );

    private final Setting<Boolean> sameY = sgGeneral.add(new BoolSetting.Builder()
        .name("same-y")
        .description("Hold the bridge at the height you started, instead of following you down.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> perTick = sgGeneral.add(new IntSetting.Builder()
        .name("blocks-per-tick")
        .defaultValue(1)
        .min(1)
        .sliderRange(1, 4)
        .build()
    );

    private final Setting<Boolean> swapBack = sgGeneral.add(new BoolSetting.Builder()
        .name("swap-back")
        .description("Return to the slot you were holding between placements.")
        .defaultValue(true)
        .build()
    );

    /** The height the bridge is held at while same-Y is on. */
    private int placementY;

    /** Flipped each tick, which is what makes the breezily yaw alternate. */
    private boolean beat;

    public Scaffoldl() {
        super(Categories.Movement, "scaffoldl", "Bridges for you, using god bridge or breezily technique.");
    }

    @Override
    public void onActivate() {
        placementY = mc.player == null ? 0 : mc.player.blockPosition().getY() - 1;
        beat = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        FindItemResult block = InvUtils.findInHotbar(stack ->
            stack.getItem() instanceof BlockItem item && blocksFilter.get().allows(blocks.get().contains(item.getBlock())));

        if (!block.found()) return;

        beat = !beat;

        int placed = 0;

        for (BlockPos pos : positions()) {
            if (placed >= perTick.get()) break;
            if (!mc.level.getBlockState(pos).canBeReplaced()) continue;
            if (!BlockUtils.canPlace(pos)) continue;

            aim();

            if (BlockUtils.place(pos, block, false, 50, true, swapBack.get())) placed++;
        }
    }

    /**
     * Where to place. Normal follows you down; same-Y holds the height the bridge started at, which is
     * what stops a walk off an edge turning into a staircase.
     */
    private BlockPos[] positions() {
        BlockPos under = mc.player.blockPosition().below();

        if (sameY.get()) under = new BlockPos(under.getX(), placementY, under.getZ());

        // The block ahead as well, so a sprint does not outrun the bridge.
        Vec3 movement = mc.player.getDeltaMovement();
        BlockPos ahead = under.offset((int) Math.signum(movement.x), 0, (int) Math.signum(movement.z));

        return new BlockPos[]{under, ahead};
    }

    /**
     * The rotation each technique is built on. All three place the same blocks; what differs is where
     * you are looking while doing it, which is the whole of what a check sees.
     */
    private void aim() {
        float yaw = mc.player.getYRot();
        float pitch;

        switch (technique.get()) {
            case Normal -> {
                // Straight down and behind, which is where a player looks to place under themselves.
                yaw += 180;
                pitch = 75;
            }

            case GodBridge -> {
                // The shallow angle that lets you run along the edge of the blocks rather than over the
                // top of each one. Sixty degrees is the value the technique is built around.
                yaw += 180;
                pitch = 60;
            }

            case Breezily -> {
                // A few degrees either side, alternating. It is what a hand does at speed, and it is
                // what makes a straight bridge come out of an unsteady grip.
                yaw += 180 + (beat ? 8 : -8);
                pitch = 72;
            }

            default -> pitch = 75;
        }

        Rotations.rotate(Mth.wrapDegrees(yaw), pitch, 50);
    }

    @Override
    public String getInfoString() {
        return technique.get().toString();
    }

    public enum Technique {
        /** Look down and behind. The plain way to place under yourself. */
        Normal,
        /** Hold the shallow angle that lets you sprint along the block edges. */
        GodBridge,
        /** Alternate the yaw either side each tick, as a hand does at speed. */
        Breezily
    }
}
