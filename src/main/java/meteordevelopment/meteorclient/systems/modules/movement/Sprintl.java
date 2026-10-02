/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

/**
 * Sprints for you, including in directions vanilla will not sprint in.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code sprint}, kept alongside it under
 * the {@code l} suffix the rest of this port uses for name collisions. What it adds is the pair of
 * refusals vanilla makes that have nothing to do with whether you could actually run.
 *
 * <p>Sprinting is refused while blind, and refused below three haunches of food. Neither is checked by
 * the server, which reads the sprint state out of the command packet you send it and takes your word
 * for it - so both are client-side rules, and lifting them is not something the server can see.
 *
 * <p>Sideways and backwards sprinting is the third piece. Vanilla only starts a sprint on forward
 * input, but the speed that comes out is applied along whatever direction you are moving, so the
 * restriction is in the trigger rather than in the movement.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class Sprintl extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> omnidirectional = sgGeneral.add(new BoolSetting.Builder()
        .name("omnidirectional")
        .description("Sprint sideways and backwards, not only forwards.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreBlindness = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-blindness")
        .description("Sprint while blind. The refusal is client-side only.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreHunger = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-hunger")
        .description("Sprint below six food. Also client-side only.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> stopWhileSneaking = sgGeneral.add(new BoolSetting.Builder()
        .name("stop-while-sneaking")
        .description("Drop the sprint while sneaking, which is what a player would do.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> stopWhileUsing = sgGeneral.add(new BoolSetting.Builder()
        .name("stop-while-using")
        .description("Drop the sprint while an item is in use.")
        .defaultValue(true)
        .build()
    );

    public Sprintl() {
        super(Categories.Movement, "sprintl", "Sprints for you, including sideways and while blind or hungry.");
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null) mc.player.setSprinting(false);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.gui.screen() != null) return;

        if (!shouldSprint()) {
            if (mc.player.isSprinting()) mc.player.setSprinting(false);
            return;
        }

        if (!mc.player.isSprinting()) mc.player.setSprinting(true);
    }

    private boolean shouldSprint() {
        boolean forward = mc.player.zza > 1.0E-5;
        boolean anyDirection = Math.abs(mc.player.zza) > 1.0E-5 || Math.abs(mc.player.xxa) > 1.0E-5;

        if (!(omnidirectional.get() ? anyDirection : forward)) return false;

        if (stopWhileSneaking.get() && mc.player.isShiftKeyDown()) return false;
        if (stopWhileUsing.get() && mc.player.isUsingItem()) return false;

        // A sprint in water is a swim, which is a different pose and a different speed - vanilla will
        // fight over it every tick if this tries to hold one.
        if (mc.player.isInWater() && !mc.player.isUnderWater()) return false;

        return true;
    }

    /** Whether the blindness refusal should be lifted, asked from the client's own sprint check. */
    public boolean ignoreBlindness() {
        return isActive() && ignoreBlindness.get();
    }

    /** Whether the food refusal should be lifted. */
    public boolean ignoreHunger() {
        return isActive() && ignoreHunger.get();
    }
}
