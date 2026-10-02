/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.world;

import meteordevelopment.meteorclient.events.entity.player.InteractItemEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Places a block out in the open at a fixed distance, with nothing to place it against.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code air-place}, kept alongside it
 * under the {@code l} suffix the rest of this port uses for name collisions. Meteor's picks along your
 * look direction and places at whatever the ray finds; this one puts the block at a set distance out
 * regardless, which is what makes the placement predictable.
 *
 * <p>The difference matters when bridging out over nothing: a ray that finds no surface has no answer,
 * and a fixed distance always does. The outline is drawn for the same reason - there is nothing else
 * on screen telling you where the block will land.
 *
 * <p>Placing into liquid is separate, because it is the case where the ray <i>would</i> have found
 * something and you want it ignored.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class AirPlacel extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far along your look direction to put the block.")
        .defaultValue(3)
        .min(1)
        .sliderRange(1, 4.5)
        .build()
    );

    private final Setting<Boolean> intoLiquid = sgGeneral.add(new BoolSetting.Builder()
        .name("into-liquid")
        .description("Place into water and lava as well as air.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Outline where the block would go.")
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
        .defaultValue(new SettingColor(69, 119, 255, 60))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .defaultValue(new SettingColor(255, 255, 255, 200))
        .visible(render::get)
        .build()
    );

    public AirPlacel() {
        super(Categories.World, "air-placel", "Places blocks in mid-air at a fixed distance.");
    }

    @EventHandler
    private void onInteractItem(InteractItemEvent event) {
        if (!(mc.player.getItemInHand(event.hand).getItem() instanceof BlockItem)) return;

        BlockHitResult target = target();
        if (target == null) return;

        BlockUtils.interact(target, event.hand, true);
        event.toReturn = InteractionResult.SUCCESS;
    }

    /** The hit result to place against, built at the chosen distance rather than found by a ray. */
    private BlockHitResult target() {
        if (mc.player == null || mc.level == null || mc.player.isSpectator()) return null;

        Vec3 at = mc.player.getEyePosition().add(mc.player.getLookAngle().scale(range.get()));
        BlockPos pos = BlockPos.containing(at);

        if (!mc.level.getBlockState(pos).isAir()) {
            // Only a liquid is worth placing into; anything else there is already a block.
            if (!intoLiquid.get() || mc.level.getFluidState(pos).isEmpty()) return null;
        }

        // The face is taken from the way you are looking, so the block orients as it would if you had
        // really clicked a surface from here.
        Direction look = Direction.getApproximateNearest(mc.player.getLookAngle().x, mc.player.getLookAngle().y, mc.player.getLookAngle().z);

        return new BlockHitResult(at, look.getOpposite(), pos, false);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get()) return;

        BlockHitResult target = target();
        if (target == null) return;

        event.renderer.box(target.getBlockPos(), sideColor.get(), lineColor.get(), shapeMode.get(), 0);
    }
}
