/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Flips which hand you appear to hold things in, over and over.
 *
 * <p>Which arm is your main one is part of the client settings you send the server on join, and the
 * server passes it on to everyone else so they draw you correctly. It can be re-sent at any time,
 * which is why this is visible to other players rather than only to you.
 *
 * <p>The setting is put back when the module is switched off, so it does not leave you left-handed on
 * every server you join afterwards.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class HandDerp extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks between switches.")
        .defaultValue(2)
        .min(1)
        .sliderRange(1, 40)
        .build()
    );

    /** What the hand was before, so switching off restores it rather than leaving it wherever it stopped. */
    private HumanoidArm original;
    private HumanoidArm current;
    private int timer;

    public HandDerp() {
        super(Categories.Misc, "hand-derp", "Flips which hand you appear to hold things in.");
    }

    @Override
    public void onActivate() {
        original = mc.options.mainHand().get();
        current = original;
        timer = 0;
    }

    @Override
    public void onDeactivate() {
        if (original == null) return;

        current = original;
        send();
        original = null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (++timer < delay.get()) return;
        timer = 0;

        current = current == HumanoidArm.RIGHT ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
        send();
    }

    /**
     * Client settings go out as one packet, so the other values have to be carried along unchanged -
     * sending defaults here would quietly reset the player's render distance and chat options.
     */
    private void send() {
        if (mc.player == null || mc.getConnection() == null) return;

        mc.options.mainHand().set(current);
        mc.getConnection().send(new ServerboundClientInformationPacket(mc.options.buildPlayerInformation()));
    }
}
