/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.world.CollisionShapeEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Puts a ledge in front of the blocks that hurt you, so you walk into a step instead of into damage.
 *
 * <p>Collision shapes are decided on the client, which is what makes this work at all: a block given
 * a shape it does not really have stops you locally, and the server - which validates where you end
 * up, not what you thought was in the way - simply sees you not walking into lava.
 *
 * <p>The shape used is a quarter-height cap rather than a full block, so a hazard on the floor becomes
 * something to step onto rather than a wall. A full cube would stop you dead and make the difference
 * from vanilla obvious to anyone watching.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class AvoidHazards extends Module {
    /** A quarter-block cap: high enough to stop a walk into it, low enough to step over. */
    private static final VoxelShape CAP = Block.box(0, 0, 0, 16, 4, 16);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> fire = sgGeneral.add(new BoolSetting.Builder()
        .name("fire")
        .description("Fire and campfires.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> lava = sgGeneral.add(new BoolSetting.Builder()
        .name("lava")
        .description("Lava, flowing and still.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> cactus = sgGeneral.add(new BoolSetting.Builder()
        .name("cactus")
        .description("Cacti and sweet berry bushes.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> magma = sgGeneral.add(new BoolSetting.Builder()
        .name("magma")
        .description("Magma blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> cobweb = sgGeneral.add(new BoolSetting.Builder()
        .name("cobweb")
        .description("Cobwebs. Not damage, but being caught in one is usually worse.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> ignoreWhileSneaking = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-while-sneaking")
        .description("Let sneak override this, so a hazard can still be stepped into deliberately.")
        .defaultValue(true)
        .build()
    );

    public AvoidHazards() {
        super(Categories.Movement, "avoid-hazards", "Blocks you from walking into fire, lava, cacti and magma.");
    }

    @EventHandler
    private void onCollisionShape(CollisionShapeEvent event) {
        if (mc.player == null) return;
        if (ignoreWhileSneaking.get() && mc.player.isShiftKeyDown()) return;

        if (!isHazard(event.state)) return;

        // A cobweb has no collision at all to build on, so it needs the full cube to be avoidable.
        event.shape = event.state.is(Blocks.COBWEB) ? Shapes.block() : CAP;
    }

    private boolean isHazard(BlockState state) {
        if (fire.get() && (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE))) return true;
        if (lava.get() && state.getFluidState().is(FluidTags.LAVA)) return true;
        if (cactus.get() && (state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH))) return true;
        if (magma.get() && state.is(Blocks.MAGMA_BLOCK)) return true;

        return cobweb.get() && state.is(Blocks.COBWEB);
    }
}
