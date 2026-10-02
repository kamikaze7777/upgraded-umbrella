/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Keeps mining whatever you are looking at without holding the button.
 *
 * <p>Breaking is driven a tick at a time by the client, so continuing it is a matter of asking for the
 * next tick of progress rather than of holding anything down. Looking away stops it on its own, which
 * makes this behave like a held button that never tires.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class AutoBreak extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
        .name("swing")
        .description("Swing your arm while mining, so it looks like you are holding the button.")
        .defaultValue(true)
        .build()
    );

    public AutoBreak() {
        super(Categories.Player, "auto-break", "Keeps mining the block you are looking at without holding the button.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.gameMode == null) return;
        if (mc.gameMode.getPlayerMode() == GameType.SPECTATOR) return;

        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        if (mc.level.getBlockState(hit.getBlockPos()).isAir()) return;

        // One tick of progress against the face we are actually looking at. Vanilla starts the break
        // on its own from here if one is not already running.
        mc.gameMode.continueDestroyBlock(hit.getBlockPos(), hit.getDirection());

        if (swing.get()) mc.player.swing(InteractionHand.MAIN_HAND);
    }
}
