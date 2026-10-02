/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.VehicleEntity;

/**
 * Breaks boats and minecarts in a single click.
 *
 * <p>Both take a fixed number of hits to destroy and the server counts hits, not time. Sending the rest
 * of them behind the first turns one click into the whole job, which matters when the thing you are
 * trying to break is carrying someone away.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class VehicleOneHit extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> hits = sgGeneral.add(new IntSetting.Builder()
        .name("extra-hits")
        .description("How many more hits to send behind the one you threw.")
        .defaultValue(19)
        .min(1)
        .sliderRange(1, 40)
        .build()
    );

    public VehicleOneHit() {
        super(Categories.Combat, "vehicle-one-hit", "Breaks boats and minecarts in one click.");
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (!(event.packet instanceof ServerboundAttackPacket(int entityId))) return;

        // The shared base of boats and minecarts, so rafts, chest boats and every minecart variant
        // are all covered without naming them one at a time.
        Entity target = mc.level.getEntity(entityId);
        if (!(target instanceof VehicleEntity)) return;

        // The original attack is still on its way out behind us; these ride along after it.
        for (int i = 0; i < hits.get(); i++) {
            mc.getConnection().send(new ServerboundAttackPacket(entityId));
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
    }
}
