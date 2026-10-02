/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Restarts elytra flight the moment it is possible, for as long as jump is held.
 *
 * <p>Anything that touches the ground ends a glide, and starting one again by hand costs a second
 * jump press timed against a fall you cannot see the end of. Holding jump instead and letting this
 * fire the start on the first tick the server would accept it turns a bounce off terrain back into
 * continuous flight.
 *
 * <p>The start is both applied locally and sent, because the server tracks gliding itself and only
 * one half of that would desync.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class ElytraRecast extends Module {
    public ElytraRecast() {
        super(Categories.Movement, "elytra-recast", "Restarts elytra flight while jump is held.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!mc.options.keyJump.isDown() || !canRecast()) return;

        mc.player.startFallFlying();
        mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
    }

    /**
     * The conditions the server applies to a glide start, checked here so a refused attempt is never
     * sent - a stream of rejected starts is far more visible than the glide itself.
     */
    private boolean canRecast() {
        if (mc.player.isFallFlying()) return false;
        if (mc.player.getAbilities().flying || mc.player.isPassenger()) return false;
        if (mc.player.onClimbable() || mc.player.isInWater()) return false;
        if (mc.player.hasEffect(MobEffects.LEVITATION)) return false;

        ItemStack chest = mc.player.getItemBySlot(EquipmentSlot.CHEST);

        // One durability from breaking counts as broken here: starting a glide on it drops you out of
        // the sky on the same tick.
        return chest.is(Items.ELYTRA) && !chest.nextDamageWillBreak();
    }
}
