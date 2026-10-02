/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.world;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Webs or sets fire to whoever you are fighting, instead of boxing them in.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code auto-trap}, kept alongside it
 * under the {@code l} suffix the rest of this port uses for name collisions. Meteor's builds an
 * obsidian box around a target; LiquidBounce's version of the name is the other two ways of stopping
 * someone, and it aims at where they are going rather than where they are.
 *
 * <p>A web placed on a moving player is worth more than one placed under them - they will have left
 * that block by the time the packet lands. So the position is taken from their movement a couple of
 * ticks ahead, which is what makes it land at all.
 *
 * <p>Fire is the fallback: it costs one flint and steel for the whole fight, does not need a block to
 * sit in, and denies the space rather than the movement.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class AutoTrapl extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far away a target may be.")
        .defaultValue(4.5)
        .min(1)
        .sliderRange(1, 6)
        .build()
    );

    private final Setting<Boolean> webs = sgGeneral.add(new BoolSetting.Builder()
        .name("webs")
        .description("Place cobwebs on the target.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> fire = sgGeneral.add(new BoolSetting.Builder()
        .name("fire")
        .description("Set fire under the target when there is no web to place.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> lead = sgGeneral.add(new IntSetting.Builder()
        .name("lead")
        .description("Ticks of the target's movement to aim ahead by.")
        .defaultValue(2)
        .min(0)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks between placements.")
        .defaultValue(10)
        .min(0)
        .sliderRange(0, 100)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Face each placement.")
        .defaultValue(true)
        .build()
    );

    private int timer;

    public AutoTrapl() {
        super(Categories.World, "auto-trapl", "Webs or sets fire to your target, aimed where they are going.", "ignite", "auto-web");
    }

    @Override
    public void onActivate() {
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (timer > 0) {
            timer--;
            return;
        }

        Player target = findTarget();
        if (target == null) return;

        // Where they will be, not where they are - a packet placing a web under a sprinting player
        // arrives after they have gone.
        BlockPos at = BlockPos.containing(target.position().add(target.getDeltaMovement().scale(lead.get())));

        if (webs.get() && place(at, Items.COBWEB)) return;

        // Fire needs a solid block under it, which is a different position from the web's.
        if (fire.get() && mc.level.getBlockState(at.below()).isSolidRender()) place(at, Items.FLINT_AND_STEEL);
    }

    private boolean place(BlockPos pos, net.minecraft.world.item.Item item) {
        if (!mc.level.getBlockState(pos).canBeReplaced()) return false;
        if (mc.player.getEyePosition().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(pos)) > range.get()) return false;

        FindItemResult found = InvUtils.findInHotbar(item);
        if (!found.found()) return false;

        if (!BlockUtils.place(pos, found, rotate.get(), 50)) return false;

        timer = delay.get();
        return true;
    }

    private Player findTarget() {
        Player best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Player player : mc.level.players()) {
            if (player == mc.player || !player.isAlive() || !Friends.get().shouldAttack(player)) continue;

            // Already burning or already webbed - a second one adds nothing.
            if (player.isOnFire() && !webs.get()) continue;
            if (mc.level.getBlockState(player.blockPosition()).is(Blocks.COBWEB)) continue;

            double distance = mc.player.distanceTo(player);
            if (distance > range.get() + lead.get() || distance >= bestDistance) continue;

            best = player;
            bestDistance = distance;
        }

        return best;
    }
}
