/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.entity.player.JumpVelocityMultiplierEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HoneyBlock;
import net.minecraft.world.level.block.SlimeBlock;

/**
 * Jumps higher off the blocks that are already springy.
 *
 * <p>Slime, beds and honey bounce you when you land on them but do nothing for a jump you make
 * yourself. Scaling the jump while standing on one turns them into launch pads without touching how
 * you jump anywhere else.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class BlockBounce extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> multiplier = sgGeneral.add(new DoubleSetting.Builder()
        .name("multiplier")
        .description("How much to scale the jump by while stood on a springy block. 1 is a normal jump.")
        .defaultValue(2)
        .min(1)
        .sliderRange(1, 4)
        .build()
    );

    public BlockBounce() {
        super(Categories.Movement, "block-bounce", "Jumps higher off slime, beds and honey.");
    }

    @EventHandler
    private void onJumpVelocity(JumpVelocityMultiplierEvent event) {
        if (!isOnSpringyBlock()) return;

        event.multiplier *= multiplier.get().floatValue();
    }

    /**
     * Looks at what is holding us up rather than the block we are inside, so this reads the same block
     * the jump is pushing off.
     */
    private boolean isOnSpringyBlock() {
        BlockPos below = BlockPos.containing(mc.player.getX(), mc.player.getBoundingBox().minY - 0.01, mc.player.getZ());
        Block block = mc.level.getBlockState(below).getBlock();

        return block instanceof SlimeBlock || block instanceof BedBlock || block instanceof HoneyBlock;
    }
}
