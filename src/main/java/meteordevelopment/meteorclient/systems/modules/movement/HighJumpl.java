/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.entity.player.JumpVelocityMultiplierEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

/**
 * Jumps higher, and optionally slows the fall back down so the whole hop stays inside what a movement
 * check will accept.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code high-jump}, kept alongside it
 * under the {@code l} suffix the rest of this port uses for name collisions. Meteor's raises the jump
 * and leaves the descent alone; the addition here is the glide.
 *
 * <p>The reason it matters: a check watches how far you deviate from the fall it predicts, and an
 * over-tall jump is at its most obviously wrong on the way down, where the client's velocity and the
 * server's disagree by the extra height. Holding the descent near a plausible rate spreads that
 * disagreement over enough ticks that each one on its own looks ordinary.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class HighJumpl extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> multiplier = sgGeneral.add(new DoubleSetting.Builder()
        .name("multiplier")
        .description("How much higher to jump than vanilla.")
        .defaultValue(2)
        .min(1)
        .sliderRange(1, 6)
        .build()
    );

    private final Setting<Boolean> glide = sgGeneral.add(new BoolSetting.Builder()
        .name("glide")
        .description("Hold the descent to a steady rate instead of letting it accelerate.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> glideSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("glide-speed")
        .description("Blocks per tick to fall at while gliding.")
        .defaultValue(0.15)
        .min(0.01)
        .sliderRange(0.01, 0.5)
        .visible(glide::get)
        .build()
    );

    /** Set on the way up, cleared on landing - the glide only applies to a jump this module started. */
    private boolean ours;

    public HighJumpl() {
        super(Categories.Movement, "high-jumpl", "Jumps higher, and can hold the fall to a steady rate.");
    }

    @Override
    public void onActivate() {
        ours = false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player.onGround()) {
            ours = false;
            return;
        }

        if (!glide.get() || !ours) return;
        if (mc.player.getDeltaMovement().y >= 0) return;

        mc.player.setDeltaMovement(mc.player.getDeltaMovement().x, -glideSpeed.get(), mc.player.getDeltaMovement().z);
    }

    /**
     * Meteor already posts this where the jump velocity is decided, so no new hook is needed - and
     * this is also where the jump gets marked as ours, so the glide below only touches jumps this
     * module raised.
     */
    @EventHandler
    private void onJumpVelocityMultiplier(JumpVelocityMultiplierEvent event) {
        event.multiplier *= multiplier.get();
        ours = true;
    }
}
