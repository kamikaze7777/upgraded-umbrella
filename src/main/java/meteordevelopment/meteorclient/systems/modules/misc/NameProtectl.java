/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.client.multiplayer.PlayerInfo;

/**
 * Replaces other players' names wherever text is drawn, not only your own.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code name-protect}, kept alongside it
 * under the {@code l} suffix the rest of this port uses for name collisions. Meteor's hides <i>your</i>
 * name and skin, which is what protects you when you are the one being recorded.
 *
 * <p>This is the other case: you are recording, and the names on screen belong to people who did not
 * agree to appear. It substitutes in the same place Meteor's does - the point every piece of text
 * passes through on its way to being drawn - so chat, nametags, the player list and scoreboards are
 * all covered by one change rather than one hook each.
 *
 * <p>Every substitution is local. The server and every other player still see the real names, and
 * anything you type is unaffected.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class NameProtectl extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> hideFriends = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-friends")
        .description("Replace the names of people on your friends list.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> friendReplacement = sgGeneral.add(new StringSetting.Builder()
        .name("friend-replacement")
        .description("What to show instead. A number is appended so they stay apart.")
        .defaultValue("Friend")
        .visible(hideFriends::get)
        .build()
    );

    private final Setting<Boolean> hideOthers = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-others")
        .description("Replace everyone else's names too.")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> otherReplacement = sgGeneral.add(new StringSetting.Builder()
        .name("other-replacement")
        .defaultValue("Player")
        .visible(hideOthers::get)
        .build()
    );

    public NameProtectl() {
        super(Categories.Misc, "name-protectl", "Replaces other players' names wherever they are drawn.");
    }

    /**
     * Substitutes every name this module is hiding out of a piece of text on its way to the screen.
     *
     * <p>Called for every string the game draws, so the cheap rejections come first - most text
     * contains no player name at all, and building a replacement map for those would cost far more
     * than the feature is worth.
     */
    public String replaceNames(String text) {
        if (!isActive() || text == null || text.isEmpty()) return text;
        if (!hideFriends.get() && !hideOthers.get()) return text;
        if (mc.getConnection() == null) return text;

        String own = mc.player == null ? null : mc.player.getName().getString();

        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            String name = info.getProfile().name();

            // Your own name belongs to Meteor's name-protect, not this one.
            if (name.equals(own) || !text.contains(name)) continue;

            boolean friend = Friends.get().isFriend(info);

            if (friend && hideFriends.get()) text = text.replace(name, friendReplacement.get() + suffix(name));
            else if (!friend && hideOthers.get()) text = text.replace(name, otherReplacement.get() + suffix(name));
        }

        return text;
    }

    /**
     * Two digits off the name's own hash. A counter would give the same player a different label in
     * every frame, which is unreadable; a hash is stable and still keeps two players apart.
     */
    private String suffix(String name) {
        return Integer.toString(Math.abs(name.hashCode()) % 100);
    }
}
