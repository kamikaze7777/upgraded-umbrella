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

/**
 * Finishes eating and drinking in a fraction of the time, on servers that count it in packets.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code fast-use}, kept alongside it under
 * the {@code l} suffix the rest of this port uses for name collisions. Meteor's shortens the client's
 * own use cooldown, which is a local timer. This is the other half of the problem.
 *
 * <p>An older server - and the translation layers that front one - advances how long you have been
 * using an item once per position packet rather than once per tick. So a burst of standing-still
 * packets ages the use to completion in a single tick. A modern server keeps its own clock and ignores
 * the duplicates entirely, which is why this is worth switching on only behind a version translation.
 *
 * <p>The timer setting is here because the two work together: the burst ages the server's copy, and a
 * fast timer ages the client's, so what you see finishes at the same moment the server thinks it did.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class FastUsel extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> packets = sgGeneral.add(new IntSetting.Builder()
        .name("packets")
        .description("Position packets to send per tick while an item is in use.")
        .defaultValue(20)
        .min(1)
        .sliderRange(1, 35)
        .build()
    );

    private final Setting<Double> timer = sgGeneral.add(new DoubleSetting.Builder()
        .name("timer")
        .description("Timer speed to hold while using, so the local animation keeps up.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 5)
        .build()
    );

    private final Setting<Boolean> onlyOnGround = sgGeneral.add(new BoolSetting.Builder()
        .name("only-on-ground")
        .description("Do nothing while airborne, where a burst of packets is far more visible.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> onlyConsumable = sgGeneral.add(new BoolSetting.Builder()
        .name("only-consumable")
        .description("Only speed up food and drink, not bows or shields.")
        .defaultValue(true)
        .build()
    );

    public FastUsel() {
        super(Categories.Player, "fast-usel", "Sends position bursts so item use finishes early on legacy servers.", "fast-eat");
    }

    @Override
    public void onDeactivate() {
        Modules.get().get(Timer.class).setOverride(Timer.OFF);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        Timer timerModule = Modules.get().get(Timer.class);

        if (!shouldAccelerate()) {
            timerModule.setOverride(Timer.OFF);
            return;
        }

        timerModule.setOverride(timer.get());

        // Position unchanged and on the ground, so the burst reads as a stationary player rather than
        // as movement the server has to validate.
        for (int i = 0; i < packets.get(); i++) {
            mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(
                mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.onGround(), mc.player.horizontalCollision
            ));
        }
    }

    private boolean shouldAccelerate() {
        if (!mc.player.isUsingItem()) return false;
        if (onlyOnGround.get() && !mc.player.onGround()) return false;

        // Eating and drinking are what this actually shortens; a bow's draw is decided by the client
        // and gains nothing from ageing the server's copy.
        return !onlyConsumable.get() || mc.player.getUseItem().has(net.minecraft.core.component.DataComponents.CONSUMABLE);
    }
}
