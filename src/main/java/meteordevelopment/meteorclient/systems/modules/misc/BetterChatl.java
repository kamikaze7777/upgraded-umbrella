/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Filters chat by pattern and collapses the messages that repeat.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code better-chat}, kept alongside it
 * under the {@code l} suffix the rest of this port uses for name collisions. Meteor's covers a great
 * deal more of the chat experience; the two pieces here are about a chat that is too full to read.
 *
 * <p><b>Patterns</b> drop a message outright. On a server that advertises in chat every thirty seconds
 * that is the difference between chat being usable and not.
 *
 * <p><b>Collapsing</b> is the subtler one: rather than dropping a repeat, it is counted and the count
 * shown on the message already there. Nothing is lost - you still see that it happened, and how many
 * times - but ten identical lines become one.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class BetterChatl extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<String>> patterns = sgGeneral.add(new StringListSetting.Builder()
        .name("patterns")
        .description("Regular expressions. A message matching any of them is dropped.")
        .build()
    );

    private final Setting<Boolean> collapse = sgGeneral.add(new BoolSetting.Builder()
        .name("collapse")
        .description("Count repeats instead of printing them again.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> depth = sgGeneral.add(new IntSetting.Builder()
        .name("depth")
        .description("How many recent messages to compare against.")
        .defaultValue(6)
        .min(1)
        .sliderRange(1, 40)
        .visible(collapse::get)
        .build()
    );

    /** The last few messages seen, for the repeat test. */
    private final Deque<String> recent = new ArrayDeque<>();

    /** How many times the most recent message has repeated, so the count can be appended. */
    private int repeats;

    public BetterChatl() {
        super(Categories.Misc, "better-chatl", "Filters chat by pattern and collapses repeated messages.");
    }

    @Override
    public void onActivate() {
        recent.clear();
        repeats = 0;
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        String text = event.getMessage().getString();

        if (matchesPattern(text)) {
            event.cancel();
            return;
        }

        if (!collapse.get()) return;

        if (recent.contains(text)) {
            // Already on screen. Replace it with the same line plus a count rather than adding another.
            repeats++;

            event.setMessage(Component.empty()
                .append(event.getMessage())
                .append(Component.literal(" (" + (repeats + 1) + ")").withStyle(ChatFormatting.GRAY)));

            return;
        }

        repeats = 0;
        recent.addLast(text);

        while (recent.size() > depth.get()) recent.removeFirst();
    }

    /**
     * A pattern that will not compile is treated as matching nothing rather than throwing - the list is
     * typed by hand and a half-written expression should not break chat.
     */
    private boolean matchesPattern(String text) {
        for (String pattern : patterns.get()) {
            try {
                if (Pattern.compile(pattern).matcher(text).find()) return true;
            } catch (PatternSyntaxException ignored) {
            }
        }

        return false;
    }
}
