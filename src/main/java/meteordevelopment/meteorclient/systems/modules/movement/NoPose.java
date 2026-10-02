/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityAttachments;
import net.minecraft.world.entity.EntityDimensions;

/**
 * Holds the player in an older version's shape.
 *
 * <p>Swimming and crawling are poses that did not exist before 1.13, and the crouching box has been
 * three different heights since. A server running an older protocol behind a translation layer only
 * knows the shape its own version defines, so a pose it cannot represent gets guessed at - which
 * shows up as the hitbox the server hits being somewhere other than the one you see.
 *
 * <p>Both halves are client side. The pose is refused before it is ever set, and the crouch box is
 * swapped for the older one, so what the server is told stays a plain position.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class NoPose extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> noSwim = sgGeneral.add(new BoolSetting.Builder()
        .name("no-swim")
        .description("Refuses the swimming pose, so sprint-swimming keeps you upright.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SneakHeight> sneakHeight = sgGeneral.add(new EnumSetting.Builder<SneakHeight>()
        .name("sneak-height")
        .description("Which version's crouching box to use.")
        .defaultValue(SneakHeight.Vanilla)
        .build()
    );

    public NoPose() {
        super(Categories.Movement, "no-pose", "Keeps you in an older version's poses, for servers behind a translation layer.", "no-swim", "no-crawl");
    }

    public boolean shouldCancelSwim() {
        return isActive() && noSwim.get();
    }

    /** The crouching box to stand in, or null to leave vanilla's alone. */
    public EntityDimensions crouchingDimensions() {
        return isActive() ? sneakHeight.get().dimensions : null;
    }

    public enum SneakHeight {
        /** 1.8 and earlier - sneaking did not shrink the box at all, only the eye height. */
        Old(1.8f, 1.54f),
        /** 1.9 through 1.13. */
        Medium(1.65f, 1.54f),
        /** 1.14 onwards, which is what this client already does. */
        Vanilla(1.5f, 1.27f);

        public final EntityDimensions dimensions;

        SneakHeight(float height, float eyeHeight) {
            // The vehicle attachment has to be carried over by hand - building dimensions from scratch
            // drops it, and a player without one sits in the wrong place in a boat.
            this.dimensions = EntityDimensions.scalable(0.6f, height)
                .withEyeHeight(eyeHeight)
                .withAttachments(EntityAttachments.builder().attach(EntityAttachment.VEHICLE, Avatar.DEFAULT_VEHICLE_ATTACHMENT));
        }
    }
}
