/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Fills and signs books without opening one.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code book-bot}, kept alongside it under
 * the {@code l} suffix the rest of this port uses for name collisions. Meteor's has more ways to
 * choose what goes in; the difference here is that the screen is never used.
 *
 * <p>Writing a book is a single packet carrying every page and the title. Sending it directly means a
 * book takes one tick rather than the second or two of opening the screen, typing, and signing - so a
 * stack of books is a few seconds rather than a few minutes.
 *
 * <p>The pages are capped to what the server will accept. A packet over the limit is refused outright,
 * and on most servers refused loudly.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class BookBotl extends Module {
    /** Characters a page holds. Over this and the server refuses the whole packet. */
    private static final int PAGE_LIMIT = 1024;

    /** Pages a book holds. */
    private static final int PAGE_COUNT = 100;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("What to fill the pages with.")
        .defaultValue(Mode.Random)
        .build()
    );

    private final Setting<String> text = sgGeneral.add(new StringSetting.Builder()
        .name("text")
        .description("The text to repeat, when not filling at random.")
        .defaultValue("")
        .visible(() -> mode.get() == Mode.Text)
        .build()
    );

    private final Setting<Integer> pages = sgGeneral.add(new IntSetting.Builder()
        .name("pages")
        .description("Pages to write.")
        .defaultValue(50)
        .range(1, PAGE_COUNT)
        .sliderRange(1, PAGE_COUNT)
        .build()
    );

    private final Setting<Integer> characters = sgGeneral.add(new IntSetting.Builder()
        .name("characters")
        .description("Characters per page.")
        .defaultValue(256)
        .range(1, PAGE_LIMIT)
        .sliderRange(1, PAGE_LIMIT)
        .build()
    );

    private final Setting<Boolean> sign = sgGeneral.add(new BoolSetting.Builder()
        .name("sign")
        .description("Sign the book, which makes it permanent and stackable.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> title = sgGeneral.add(new StringSetting.Builder()
        .name("title")
        .description("The title to sign with. A number is appended so they stay apart.")
        .defaultValue("book")
        .visible(sign::get)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks between books. Too fast and the server's packet rate limit cuts in.")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 100)
        .build()
    );

    private int timer;
    private int written;

    public BookBotl() {
        super(Categories.Misc, "book-botl", "Fills and signs books by packet, without opening the screen.");
    }

    @Override
    public void onActivate() {
        timer = 0;
        written = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        // The screen writes its own book on close, which would fight with this.
        if (mc.gui.screen() instanceof BookEditScreen) return;

        if (timer > 0) {
            timer--;
            return;
        }

        FindItemResult book = InvUtils.findInHotbar(Items.WRITABLE_BOOK);
        if (!book.found()) {
            toggle();
            return;
        }

        InvUtils.swap(book.slot(), false);

        List<String> content = new ArrayList<>(pages.get());
        for (int i = 0; i < pages.get(); i++) content.add(page());

        mc.getConnection().send(new ServerboundEditBookPacket(
            mc.player.getInventory().getSelectedSlot(),
            content,
            sign.get() ? java.util.Optional.of(title.get() + " " + written) : java.util.Optional.empty()
        ));

        written++;
        timer = delay.get();
    }

    private String page() {
        if (mode.get() == Mode.Text) {
            String source = text.get();
            if (source.isEmpty()) return " ";

            StringBuilder builder = new StringBuilder(characters.get());
            while (builder.length() < characters.get()) builder.append(source);

            return builder.substring(0, characters.get());
        }

        // Printable ASCII only. The formatting character and anything outside this range are what get
        // a book refused, or get you kicked for an illegal packet.
        StringBuilder builder = new StringBuilder(characters.get());
        for (int i = 0; i < characters.get(); i++) builder.append((char) (33 + Math.random() * 93));

        return builder.toString();
    }

    @Override
    public String getInfoString() {
        return Integer.toString(written);
    }

    public enum Mode {
        /** Random printable characters. */
        Random,
        /** Your own text, repeated to fill the page. */
        Text
    }
}
