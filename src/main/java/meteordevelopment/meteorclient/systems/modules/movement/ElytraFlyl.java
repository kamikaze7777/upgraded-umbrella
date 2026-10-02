/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Flies on an elytra at a speed you set, and can do it without spending durability.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code elytra-fly}, kept alongside it
 * under the {@code l} suffix the rest of this port uses for name collisions. Meteor's has far more
 * flight modes; the piece here that it does not have is the durability exploit.
 *
 * <p>An elytra is charged one point of durability per second of <i>continuous</i> gliding, counted by
 * the server while the gliding flag is set. Stopping and restarting the glide every tick means the
 * flag is never set for a whole second at a time, so the count never reaches one - and the movement in
 * between is unaffected, because the client is what applies it.
 *
 * <p>The cost is a stream of start-gliding commands, which is not subtle. It is worth it on a
 * near-broken elytra and nowhere else.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class ElytraFlyl extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> horizontal = sgGeneral.add(new DoubleSetting.Builder()
        .name("horizontal-speed")
        .description("Blocks per tick to fly forwards at.")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 8)
        .build()
    );

    private final Setting<Double> vertical = sgGeneral.add(new DoubleSetting.Builder()
        .name("vertical-speed")
        .description("Blocks per tick to climb or dive at while jump or sneak is held.")
        .defaultValue(0.5)
        .min(0)
        .sliderRange(0, 5)
        .build()
    );

    private final Setting<Boolean> durabilityExploit = sgGeneral.add(new BoolSetting.Builder()
        .name("durability-exploit")
        .description("Restart the glide every tick, so the server never counts a full second of it.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> stopOnGround = sgGeneral.add(new BoolSetting.Builder()
        .name("stop-on-ground")
        .description("Stop gliding when you touch the ground while sneaking.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> notInLiquid = sgGeneral.add(new BoolSetting.Builder()
        .name("not-in-liquid")
        .description("Stop gliding in water or lava, where it does not work anyway.")
        .defaultValue(true)
        .build()
    );

    public ElytraFlyl() {
        super(Categories.Movement, "elytra-flyl", "Elytra flight at a set speed, optionally without spending durability.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) return;

        if (shouldStop()) {
            if (mc.player.isFallFlying()) stopGliding();
            return;
        }

        if (!mc.player.isFallFlying()) return;

        // Restarted every tick, so the server's continuous-glide timer never reaches the second it
        // charges a point of durability for.
        if (durabilityExploit.get()) {
            stopGliding();
            startGliding();
        }

        fly();
    }

    /** Holds a flat speed along your look direction, with jump and sneak deciding the climb. */
    private void fly() {
        Vec3 look = mc.player.getLookAngle();

        double y = mc.player.getDeltaMovement().y;

        if (mc.options.keyJump.isDown()) y = vertical.get();
        else if (mc.options.keyShift.isDown()) y = -vertical.get();
        else y = 0;

        double length = Math.sqrt(look.x * look.x + look.z * look.z);
        if (length < 1.0E-4) length = 1;

        mc.player.setDeltaMovement(look.x / length * horizontal.get(), y, look.z / length * horizontal.get());
    }

    private boolean shouldStop() {
        if (notInLiquid.get() && (mc.player.isInWater() || mc.player.isInLava())) return true;

        return stopOnGround.get() && mc.player.onGround() && mc.options.keyShift.isDown();
    }

    private void startGliding() {
        mc.player.startFallFlying();
        mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
    }

    private void stopGliding() {
        mc.player.stopFallFlying();
    }
}
