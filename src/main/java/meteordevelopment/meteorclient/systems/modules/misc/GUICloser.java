/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.List;
import java.util.Locale;

/**
 * Refuses screens you never want to see.
 *
 * <p>Servers open menus at you - vote prompts, shop windows, join adverts - and each one takes over
 * the game until you dismiss it. Matching them by title and declining to open them at all keeps you
 * playing through it.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class GUICloser extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<String>> filters = sgGeneral.add(new StringListSetting.Builder()
        .name("titles")
        .description("Screen titles to refuse.")
        .defaultValue("Vote")
        .build()
    );

    private final Setting<Match> match = sgGeneral.add(new EnumSetting.Builder<Match>()
        .name("match")
        .description("""
                How a title has to line up with a filter:
                - Contains: The filter appears anywhere in the title.
                - Exact:    The whole title is the filter.
            """)
        .defaultValue(Match.Contains)
        .build()
    );

    private final Setting<Boolean> caseSensitive = sgGeneral.add(new BoolSetting.Builder()
        .name("case-sensitive")
        .description("Whether the titles have to match in case as well.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> containersOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("containers-only")
        .description("Only refuse container screens, so ordinary menus are left alone.")
        .defaultValue(true)
        .build()
    );

    public GUICloser() {
        super(Categories.Misc, "gui-closer", "Refuses server screens whose title matches a filter.");
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        Screen screen = event.screen;
        if (screen == null) return;
        if (containersOnly.get() && !(screen instanceof AbstractContainerScreen<?>)) return;

        if (matches(screen.getTitle().getString())) event.cancel();
    }

    private boolean matches(String title) {
        String subject = caseSensitive.get() ? title : title.toLowerCase(Locale.ROOT);

        for (String filter : filters.get()) {
            String wanted = caseSensitive.get() ? filter : filter.toLowerCase(Locale.ROOT);

            boolean hit = switch (match.get()) {
                case Contains -> subject.contains(wanted);
                case Exact -> subject.equals(wanted);
            };

            if (hit) return true;
        }

        return false;
    }

    public enum Match {
        Contains,
        Exact
    }
}
