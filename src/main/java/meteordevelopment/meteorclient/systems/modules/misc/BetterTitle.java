/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import org.lwjgl.glfw.GLFW;

/**
 * Writes what you are doing into the operating system's window title.
 *
 * <p>The title bar is the one part of the game visible while the window is not focused, which makes it
 * the right place for the things you would otherwise have to switch back to check - health, the server
 * you are on, the frame rate.
 *
 * <p>Placeholders are substituted rather than the format being fixed, so the bar can hold whatever is
 * worth glancing at without a setting per field.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class BetterTitle extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> format = sgGeneral.add(new StringSetting.Builder()
        .name("format")
        .description("Placeholders: {health} {food} {server} {fps} {x} {y} {z} {players}")
        .defaultValue("Meteor | {server} | {health} HP | {fps} fps")
        .build()
    );

    private final Setting<Boolean> onlyInGame = sgGeneral.add(new BoolSetting.Builder()
        .name("only-in-game")
        .description("Leave the title alone while in a menu.")
        .defaultValue(true)
        .build()
    );

    /** What was there before, so switching off restores it rather than leaving whatever was last written. */
    private String original;

    public BetterTitle() {
        super(Categories.Misc, "better-title", "Writes your state into the window title.");
        runInMainMenu = true;
    }

    @Override
    public void onDeactivate() {
        if (original != null) mc.getWindow().setTitle(original);
        original = null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (onlyInGame.get() && (mc.player == null || mc.level == null)) return;

        // The very first write is the only chance to see what the game itself set.
        if (original == null) original = "Minecraft";

        mc.getWindow().setTitle(substitute(format.get()));
    }

    private String substitute(String template) {
        String server = mc.getCurrentServer() != null ? mc.getCurrentServer().ip
            : mc.hasSingleplayerServer() ? "singleplayer" : "menu";

        String result = template
            .replace("{server}", server)
            .replace("{fps}", Integer.toString(mc.getFps()));

        if (mc.player == null) {
            // Everything below needs a player. Emptying rather than leaving the braces keeps the title
            // readable in the menu.
            return result
                .replace("{health}", "-").replace("{food}", "-")
                .replace("{x}", "-").replace("{y}", "-").replace("{z}", "-")
                .replace("{players}", "-");
        }

        return result
            .replace("{health}", Integer.toString((int) mc.player.getHealth()))
            .replace("{food}", Integer.toString(mc.player.getFoodData().getFoodLevel()))
            .replace("{x}", Integer.toString(mc.player.getBlockX()))
            .replace("{y}", Integer.toString(mc.player.getBlockY()))
            .replace("{z}", Integer.toString(mc.player.getBlockZ()))
            .replace("{players}", Integer.toString(mc.getConnection() == null ? 0 : mc.getConnection().getOnlinePlayers().size()));
    }
}
