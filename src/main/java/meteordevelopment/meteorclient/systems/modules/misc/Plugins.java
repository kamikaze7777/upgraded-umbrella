/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

/**
 * Asks the server what plugins it is running, and hides the answer from chat.
 *
 * <p>Most servers leave the plugins command open to everyone; some block the plain name but not the
 * namespaced one that Bukkit registers alongside it, which is why both are tried. Knowing what is
 * running is how you find out which anti-cheat you are up against.
 *
 * <p>Nothing here is an exploit - these are commands the server chose to expose. It only sends them and
 * reads the reply, and switches itself off once it has one.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class Plugins extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> hideResponse = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-response")
        .description("Keep the server's reply out of chat and print it as module output instead.")
        .defaultValue(true)
        .build()
    );

    /** Commands to try, in order. The second is the namespaced form some servers forget to block. */
    private static final String[] COMMANDS = {"plugins", "bukkit:plugins"};

    private int attempt;
    private int waited;

    public Plugins() {
        super(Categories.Misc, "plugins", "Asks the server which plugins it is running.");
    }

    @Override
    public void onActivate() {
        if (mc.getConnection() == null || mc.hasSingleplayerServer()) {
            error("Only useful on a multiplayer server.");
            toggle();
            return;
        }

        attempt = 0;
        waited = 0;
        send();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        // Five seconds per attempt: long enough for a reply on a busy server, short enough that trying
        // both forms does not take all minute.
        if (++waited < 100) return;
        waited = 0;

        if (++attempt >= COMMANDS.length) {
            warning("No reply. The command is probably blocked.");
            toggle();
            return;
        }

        send();
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        String message = event.getMessage().getString();

        // The reply always names itself, whichever plugin answered it.
        if (!message.startsWith("Plugins (") && !message.startsWith("Server Plugins")) return;

        if (hideResponse.get()) {
            event.cancel();
            info(message);
        }

        toggle();
    }

    private void send() {
        info("Asking with /%s.", COMMANDS[attempt]);
        mc.getConnection().sendCommand(COMMANDS[attempt]);
    }
}
