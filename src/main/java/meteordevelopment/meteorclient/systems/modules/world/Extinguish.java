/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.world;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.Dimension;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Puts a bucket of water on your feet when you catch fire, and picks it back up.
 *
 * <p>Standing in water clears burning immediately and stops fire damage ticking, which no amount of
 * running does. The awkward part is the second half: a bucket of water left behind is a trail marking
 * exactly where you have been, so the pickup is not optional.
 *
 * <p>Both halves are refused in the Nether, where water placed from a bucket evaporates on contact and
 * the attempt only wastes the bucket.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class Extinguish extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> pickUp = sgGeneral.add(new BoolSetting.Builder()
        .name("pick-up")
        .description("Take the water back afterwards, so nothing is left behind.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> pickUpDelay = sgGeneral.add(new IntSetting.Builder()
        .name("pick-up-delay")
        .description("Ticks to leave the water down before taking it back. Too short and the fire is not out yet.")
        .defaultValue(4)
        .min(1)
        .sliderRange(1, 40)
        .build()
    );

    private final Setting<Integer> cooldown = sgGeneral.add(new IntSetting.Builder()
        .name("cooldown")
        .description("Ticks to wait before doing it again.")
        .defaultValue(20)
        .min(0)
        .sliderRange(0, 100)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Look down before using the bucket.")
        .defaultValue(true)
        .build()
    );

    /** Where the water was put, so the pickup aims at the same block rather than searching for it. */
    private BlockPos placed;
    private int timer;

    public Extinguish() {
        super(Categories.World, "extinguish", "Puts water under you when you catch fire, then takes it back.");
    }

    @Override
    public void onActivate() {
        placed = null;
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (timer > 0) {
            timer--;
            return;
        }

        if (placed != null) {
            retrieve();
            return;
        }

        // Fire resistance already does the job, and there is no fire to put out otherwise.
        if (!mc.player.isOnFire() || mc.player.hasEffect(MobEffects.FIRE_RESISTANCE)) return;
        if (PlayerUtils.getDimension() == Dimension.Nether) return;

        FindItemResult bucket = InvUtils.findInHotbar(Items.WATER_BUCKET);
        if (!bucket.found()) return;

        BlockPos target = mc.player.blockPosition();
        if (!mc.level.getBlockState(target).canBeReplaced()) return;

        use(bucket, target);
        placed = target;
        timer = pickUpDelay.get();
    }

    /** Takes the water back with an empty bucket, and gives up on it if there is nothing to take it with. */
    private void retrieve() {
        BlockPos target = placed;
        placed = null;
        timer = cooldown.get();

        if (!pickUp.get()) return;
        if (!mc.level.getBlockState(target).is(Blocks.WATER)) return;

        FindItemResult bucket = InvUtils.findInHotbar(Items.BUCKET);
        if (!bucket.found()) return;

        use(bucket, target);
    }

    /**
     * A bucket is used against the block itself rather than a face of it, which is why the hit is aimed
     * at the centre with an upward face - a side hit would place into the neighbour instead.
     */
    private void use(FindItemResult bucket, BlockPos pos) {
        Vec3 hit = Vec3.atCenterOf(pos);
        BlockHitResult result = new BlockHitResult(hit, Direction.UP, pos, false);

        Runnable action = () -> {
            InvUtils.swap(bucket.slot(), true);
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, result);
            mc.player.swing(InteractionHand.MAIN_HAND);
            InvUtils.swapBack();
        };

        if (rotate.get()) Rotations.rotate(Rotations.getYaw(hit), Rotations.getPitch(hit), 50, action);
        else action.run();
    }
}
