/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Puts the extra knockback of a sprint hit onto every hit instead of the occasional one.
 *
 * <p>Attacking at full charge while the server thinks you are sprinting adds knockback on top of the
 * usual amount. The same attack then clears your sprint, so the hit after it lands without the bonus
 * unless sprinting starts again first - which is what tapping W by hand is really doing.
 *
 * <p>Doing it with packets is the same trick without touching movement: stop and start sprinting
 * immediately ahead of the attack, and the server has you sprinting again by the time it arrives.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class SuperKnockback extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("""
                How to get the sprint back before the hit:
                - Packet:    Tell the server you stopped and started sprinting. Costs no movement.
                - SprintTap: Actually drop sprint for a tick and pick it back up.
            """)
        .defaultValue(Mode.Packet)
        .build()
    );

    private final Setting<Integer> maxHurtTime = sgGeneral.add(new IntSetting.Builder()
        .name("max-hurt-time")
        .description("Only bother when the target has at most this much hurt time left. 10 is every hit.")
        .defaultValue(10)
        .min(0)
        .max(10)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<Boolean> onlyOnGround = sgGeneral.add(new BoolSetting.Builder()
        .name("only-on-ground")
        .description("Only while standing on something.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> notInWater = sgGeneral.add(new BoolSetting.Builder()
        .name("not-in-water")
        .description("Skip it in water, where the sprint bonus does not apply anyway.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> onlyWhenMoving = sgGeneral.add(new BoolSetting.Builder()
        .name("only-when-moving")
        .description("Only while actually moving, so standing still does not spam sprint packets.")
        .defaultValue(true)
        .build()
    );

    private boolean restoreSprint;

    public SuperKnockback() {
        super(Categories.Combat, "super-knockback", "Lands the extra knockback of a sprint hit on every hit.");
    }

    @Override
    public void onActivate() {
        restoreSprint = false;
    }

    @Override
    public void onDeactivate() {
        restoreSprint();
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (!(event.packet instanceof ServerboundAttackPacket(int entityId))) return;
        if (!mc.player.isSprinting()) return;

        // The bonus only rides on a full charge attack, so a hit thrown early cannot collect it.
        if (mc.player.getAttackStrengthScale(0.5f) <= 0.9f) return;

        if (onlyOnGround.get() && !mc.player.onGround()) return;
        if (notInWater.get() && mc.player.isInWater()) return;
        if (onlyWhenMoving.get() && mc.player.getDeltaMovement().horizontalDistanceSqr() < 1.0E-6) return;

        Entity target = mc.level.getEntity(entityId);
        if (target instanceof LivingEntity living && living.hurtTime > maxHurtTime.get()) return;

        switch (mode.get()) {
            case Packet -> {
                // Both go out ahead of the attack, so the server reads them in order and has us sprinting
                // again by the time the hit lands.
                sendSprint(false);
                sendSprint(true);
            }
            case SprintTap -> {
                if (!restoreSprint) {
                    sendSprint(false);
                    mc.player.setSprinting(false);
                    restoreSprint = true;
                }
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        // The attack has gone out by now, so the sprint we dropped for it can come back.
        restoreSprint();
    }

    private void restoreSprint() {
        if (!restoreSprint) return;

        if (mc.player != null && mc.getConnection() != null) {
            mc.player.setSprinting(true);
            sendSprint(true);
        }

        restoreSprint = false;
    }

    private void sendSprint(boolean sprinting) {
        mc.getConnection().send(new ServerboundPlayerCommandPacket(
            mc.player,
            sprinting ? ServerboundPlayerCommandPacket.Action.START_SPRINTING : ServerboundPlayerCommandPacket.Action.STOP_SPRINTING
        ));
    }

    @Override
    public String getInfoString() {
        return mode.get().name();
    }

    public enum Mode {
        Packet,
        SprintTap
    }
}
