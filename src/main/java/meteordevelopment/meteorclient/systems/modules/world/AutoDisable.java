/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.world;

import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ModuleListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.movement.Flight;
import meteordevelopment.meteorclient.systems.modules.movement.NoClip;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;

import java.util.List;

/**
 * Switches a chosen set of modules off the moment the server pushes back.
 *
 * <p>A position packet arriving unprompted is the server correcting where it thinks you are, which is
 * what a movement check produces when it rejects something. Whatever caused it is still running at
 * that point and will produce another, so the cheapest response is to stop. Death and disconnect are
 * here for the same reason: whatever was on is no longer doing anything useful.
 *
 * <p>The list is deliberately empty of anything that would be dangerous to leave running - it defaults
 * to the movement modules, which are the ones that get corrected.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class AutoDisable extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    @SuppressWarnings("unchecked")
    private final Setting<List<Module>> modules = sgGeneral.add(new ModuleListSetting.Builder()
        .name("modules")
        .description("Modules to switch off.")
        .defaultValue(Flight.class, NoClip.class)
        .build()
    );

    private final Setting<Boolean> onFlag = sgGeneral.add(new BoolSetting.Builder()
        .name("on-flag")
        .description("When the server corrects your position.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> onDeath = sgGeneral.add(new BoolSetting.Builder()
        .name("on-death")
        .description("When you die.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> onDisconnect = sgGeneral.add(new BoolSetting.Builder()
        .name("on-disconnect")
        .description("When you leave the server.")
        .defaultValue(true)
        .build()
    );

    public AutoDisable() {
        super(Categories.World, "auto-disable", "Switches chosen modules off when the server pushes back.");
        runInMainMenu = true;
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        // Receive fires on the network thread, so this is handed to the main thread rather than
        // toggling modules from under whatever is iterating them.
        if (onFlag.get() && event.packet instanceof ClientboundPlayerPositionPacket) mc.execute(() -> disableAll("a position correction"));
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (onDeath.get() && event.screen instanceof DeathScreen) disableAll("your death");
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (onDisconnect.get()) disableAll("disconnecting");
    }

    private void disableAll(String reason) {
        boolean any = false;

        for (Module module : modules.get()) {
            if (module == this || !module.isActive()) continue;

            module.toggle();
            any = true;
        }

        if (any) info("Disabled modules after %s.", reason);
    }
}
