/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reorders the player list and hides the names you do not want on it.
 *
 * <p>This is the half of LiquidBounce's {@code BetterTab} that Meteor's own {@code better-tab} does
 * not cover, kept alongside it under the {@code l} suffix the rest of this port uses for name
 * collisions. Meteor's already raises the size limit, sets the column height, highlights you and your
 * friends and shows latency in milliseconds - all of which is deliberately left to it rather than
 * duplicated here.
 *
 * <p>What is left is sorting and hiding, and both exist for the same reason: on a server with a full
 * list, finding one name means scanning several hundred. Sorting by latency groups the players who
 * are near you geographically; sorting alphabetically means a name can be found by eye. Hiding takes
 * out the entries that are never what you are looking for - the bots and the placeholder rows some
 * servers pad the list with.
 *
 * <p>All of it is drawing. The list the server sent is unchanged and nothing is reported back.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class BetterTabl extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Sorting> sorting = sgGeneral.add(new EnumSetting.Builder<Sorting>()
        .name("sorting")
        .description("The order to list players in.")
        .defaultValue(Sorting.Alphabetical)
        .build()
    );

    private final Setting<List<String>> hidden = sgGeneral.add(new StringListSetting.Builder()
        .name("hidden")
        .description("Names containing any of these are left off the list entirely.")
        .build()
    );

    public BetterTabl() {
        super(Categories.Render, "better-tabl", "Sorts the player list and hides names matching a filter.");
    }

    /**
     * The list to draw, given the one the game built. Returns the original untouched when there is
     * nothing to do, so the common case allocates nothing.
     */
    public List<PlayerInfo> apply(List<PlayerInfo> original) {
        if (!isActive()) return original;

        boolean filtering = !hidden.get().isEmpty();
        Comparator<PlayerInfo> comparator = comparator();

        if (!filtering && comparator == null) return original;

        List<PlayerInfo> result = new ArrayList<>(original.size());

        for (PlayerInfo info : original) {
            if (filtering && isHidden(info)) continue;
            result.add(info);
        }

        if (comparator != null) result.sort(comparator);

        return result;
    }

    private boolean isHidden(PlayerInfo info) {
        String name = info.getProfile().name().toLowerCase();

        for (String pattern : hidden.get()) {
            if (name.contains(pattern.toLowerCase())) return true;
        }

        return false;
    }

    private Comparator<PlayerInfo> comparator() {
        return switch (sorting.get()) {
            case Vanilla -> null;
            case Ping -> Comparator.comparingInt(PlayerInfo::getLatency);
            case Alphabetical -> Comparator.comparing(info -> info.getProfile().name(), String.CASE_INSENSITIVE_ORDER);
            case ReverseAlphabetical -> Comparator.comparing((PlayerInfo info) -> info.getProfile().name(), String.CASE_INSENSITIVE_ORDER).reversed();
            case NameLength -> Comparator.comparingInt(info -> info.getProfile().name().length());
        };
    }

    public enum Sorting {
        /** Whatever order the server sent. */
        Vanilla,
        /** Lowest latency first, which groups the players near you together. */
        Ping,
        Alphabetical,
        ReverseAlphabetical,
        NameLength
    }
}
