/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Ticks the server's copy of you forward in bursts, so timed effects run down faster than they should.
 *
 * <p>Older servers, and the translation layers that front them, count your position packets as ticks:
 * regeneration, burning, and potion durations advance once per packet rather than once per real tick.
 * Sending a burst of standing-still packets therefore ages those timers without moving you an inch.
 *
 * <p>Nothing here helps on a modern server, which keeps its own clock; the packets are simply ignored
 * as duplicates. It is worth switching on only when playing through a version translation.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class TimeShift extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> health = sgGeneral.add(new BoolSetting.Builder()
        .name("health")
        .description("Speed up regeneration while you are hurt.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> healthThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("health-threshold")
        .description("Health below which to start. Further below means a bigger burst.")
        .defaultValue(16)
        .range(1, 20)
        .sliderRange(1, 20)
        .visible(health::get)
        .build()
    );

    private final Setting<Boolean> fire = sgGeneral.add(new BoolSetting.Builder()
        .name("fire")
        .description("Burn off faster when you catch fire.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> badEffects = sgGeneral.add(new BoolSetting.Builder()
        .name("bad-effects")
        .description("Run harmful effects down faster.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> maxPackets = sgGeneral.add(new IntSetting.Builder()
        .name("max-packets")
        .description("The largest burst to send in one tick. High values are the visible part of this.")
        .defaultValue(100)
        .min(1)
        .sliderRange(5, 200)
        .build()
    );

    private final Setting<Double> timer = sgGeneral.add(new DoubleSetting.Builder()
        .name("timer")
        .description("Timer speed to hold while a burst is going out.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 5)
        .build()
    );

    private final Setting<Boolean> onlyOnGround = sgGeneral.add(new BoolSetting.Builder()
        .name("only-on-ground")
        .description("Do nothing while airborne, where a burst is far more obvious.")
        .defaultValue(true)
        .build()
    );

    public TimeShift() {
        super(Categories.Player, "time-shift", "Sends bursts of position packets so timed effects run down faster.", "regen", "zoot");
    }

    @Override
    public void onDeactivate() {
        Modules.get().get(Timer.class).setOverride(Timer.OFF);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        Timer timerModule = Modules.get().get(Timer.class);

        if (mc.player.getAbilities().instabuild || mc.player.isDeadOrDying()
            || (onlyOnGround.get() && !mc.player.onGround())) {
            timerModule.setOverride(Timer.OFF);
            return;
        }

        int packets = burstSize();

        if (packets <= 0) {
            timerModule.setOverride(Timer.OFF);
            return;
        }

        timerModule.setOverride(timer.get());

        // Position unchanged and on the ground, so the burst reads as a stationary player rather than
        // as movement the server has to validate.
        for (int i = 0; i < packets; i++) {
            mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(
                mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.onGround(), mc.player.horizontalCollision
            ));
        }
    }

    /** The largest burst any enabled reason asks for, so the reasons do not stack into something absurd. */
    private int burstSize() {
        int packets = 0;

        if (health.get()) {
            // Scaled by how far below the threshold you are, so a scratch costs almost nothing and being
            // nearly dead spends the full budget.
            packets = Math.max(packets, (int) ((healthThreshold.get() - mc.player.getHealth()) * 20));
        }

        if (fire.get() && mc.player.isOnFire() && !mc.player.isInWaterOrRain()) packets = Math.max(packets, 9);

        if (badEffects.get()) {
            for (MobEffectInstance effect : mc.player.getActiveEffects()) {
                if (effect.getEffect().value().getCategory() != MobEffectCategory.HARMFUL) continue;
                if (effect.isInfiniteDuration() || effect.getEffect() == MobEffects.REGENERATION) continue;

                packets = Math.max(packets, effect.getDuration() / 20);
            }
        }

        return Math.min(packets, maxPackets.get());
    }
}
