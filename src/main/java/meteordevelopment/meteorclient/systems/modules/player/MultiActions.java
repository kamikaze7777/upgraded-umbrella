/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;

/**
 * Lifts the client's rule that you may only do one thing at a time.
 *
 * <p>The client refuses to start a right click while a block is being broken, and refuses to start
 * breaking while an item is in use. Both are client-side gates in the input handler; the server has no
 * equivalent and processes the resulting packets in whatever order they arrive. So eating while mining,
 * or placing mid-break, is a restriction that only ever existed locally.
 *
 * <p>Attacking while using an item is deliberately not here - {@code multitask} already covers it, and
 * a second switch for the same behaviour would only make the two disagree.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class MultiActions extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> placeWhileBreaking = sgGeneral.add(new BoolSetting.Builder()
        .name("place-while-breaking")
        .description("Allows right clicks to start while you are breaking a block.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> breakWhileUsing = sgGeneral.add(new BoolSetting.Builder()
        .name("break-while-using")
        .description("Allows breaking to start while an item is in use.")
        .defaultValue(true)
        .build()
    );

    public MultiActions() {
        super(Categories.Player, "multi-actions", "Lets you place while breaking and break while using an item.");
    }

    public boolean mayPlaceWhileBreaking() {
        return isActive() && placeWhileBreaking.get();
    }

    public boolean mayBreakWhileUsing() {
        return isActive() && breakWhileUsing.get();
    }
}
