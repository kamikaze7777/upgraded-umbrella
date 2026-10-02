/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.world.entity.player.Input;

/**
 * Makes the last movement key pressed win instead of cancelling against the one already held.
 *
 * <p>Holding left and right together resolves to standing still, because the two impulses sum to
 * zero. Changing direction therefore costs the time it takes to let go of the old key. Resolving the
 * pair in favour of whichever was pressed most recently removes that gap, so a counter-strafe takes
 * effect on the tick the new key goes down.
 *
 * <p>The keys themselves are untouched; only the movement the client derives from them is rewritten,
 * on the same tick, before anything reads it.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class SnapTap extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> horizontal = sgGeneral.add(new BoolSetting.Builder()
        .name("horizontal")
        .description("Resolve left against right.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> vertical = sgGeneral.add(new BoolSetting.Builder()
        .name("vertical")
        .description("Resolve forwards against backwards.")
        .defaultValue(true)
        .build()
    );

    private final Axis leftRight = new Axis();
    private final Axis forwardBack = new Axis();

    public SnapTap() {
        super(Categories.Movement, "snap-tap", "Makes the last pressed movement key win instead of cancelling out.", "null-move", "socd");
    }

    @Override
    public void onActivate() {
        leftRight.reset();
        forwardBack.reset();
    }

    /**
     * Rewrites an opposed pair in favour of the key pressed later. Called once per input tick with the
     * keys as the keyboard actually reported them, so the press order is tracked here rather than off
     * key events - a key already down when the module is switched on still has to be accounted for.
     */
    public Input resolve(Input keys) {
        boolean left = keys.left(), right = keys.right();
        boolean forward = keys.forward(), backward = keys.backward();

        if (horizontal.get()) {
            leftRight.update(right, left);
            if (leftRight.opposed()) {
                right = leftRight.positiveWins();
                left = !right;
            }
        }

        if (vertical.get()) {
            forwardBack.update(forward, backward);
            if (forwardBack.opposed()) {
                forward = forwardBack.positiveWins();
                backward = !forward;
            }
        }

        return new Input(forward, backward, left, right, keys.jump(), keys.shift(), keys.sprint());
    }

    /** One opposed key pair, remembering which of the two went down last. */
    private static class Axis {
        private boolean positive, negative;
        private long positiveAt, negativeAt;

        void reset() {
            positive = negative = false;
            positiveAt = negativeAt = 0;
        }

        void update(boolean positiveDown, boolean negativeDown) {
            // Only a fresh press moves the timestamp, so holding one key while tapping the other keeps
            // handing the axis to the tap.
            if (positiveDown && !positive) positiveAt = System.nanoTime();
            if (negativeDown && !negative) negativeAt = System.nanoTime();

            positive = positiveDown;
            negative = negativeDown;
        }

        boolean opposed() {
            return positive && negative;
        }

        boolean positiveWins() {
            return positiveAt >= negativeAt;
        }
    }
}
