/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;

import java.util.List;

/**
 * Masks what you type in chat once it starts with a command that takes a password.
 *
 * <p>Login and register commands on servers that use them are typed into the chat box in the open,
 * which is a problem for anyone recording or sharing a screen. Replacing the drawn characters with
 * asterisks leaves the actual text alone - what is sent is exactly what was typed, only the drawing
 * is different.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class TextFieldProtect extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<String>> prefixes = sgGeneral.add(new StringListSetting.Builder()
        .name("prefixes")
        .description("Mask anything typed that begins with one of these.")
        .defaultValue("/login", "/register", "/l ", "/reg ", "/changepassword", "/email")
        .build()
    );

    public TextFieldProtect() {
        super(Categories.Misc, "text-field-protect", "Masks passwords typed into the chat box.");
    }

    /** The text to draw in place of what was typed. */
    public String mask(String typed) {
        if (!isActive() || typed.isEmpty()) return typed;

        String lower = typed.toLowerCase();
        boolean matches = false;

        for (String prefix : prefixes.get()) {
            if (lower.startsWith(prefix.toLowerCase())) {
                matches = true;
                break;
            }
        }

        if (!matches) return typed;

        // Only what follows the command itself is hidden - masking the command name too would leave you
        // unable to see whether the right one is being typed.
        int space = typed.indexOf(' ');
        if (space < 0) return typed;

        return typed.substring(0, space + 1) + "*".repeat(typed.length() - space - 1);
    }
}
