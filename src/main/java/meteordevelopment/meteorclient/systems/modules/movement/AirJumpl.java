/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.meteor.KeyInputEvent;
import meteordevelopment.meteorclient.events.world.CollisionShapeEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * Lets you jump without standing on anything.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code air-jump}, kept alongside it
 * under the {@code l} suffix the rest of this port uses for name collisions. What it adds is the
 * choice of how, and the third option is the interesting one:
 *
 * <ul>
 * <li><b>Free</b> jumps whenever you press it. Fast, and the most obvious.
 * <li><b>Double</b> allows one extra jump per landing, which is a shape a player could plausibly have.
 * <li><b>Ghost block</b> does not fake a jump at all - it gives the air directly below you a collision
 * box while jump is held, so the jump that follows is an ordinary jump off a surface, taken by the
 * game's own code with nothing skipped.
 * </ul>
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class AirJumpl extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("How the jump is allowed.")
        .defaultValue(Mode.Double)
        .build()
    );

    /** Whether the one extra jump is still available, in double mode. */
    private boolean charged = true;

    public AirJumpl() {
        super(Categories.Movement, "air-jumpl", "Lets you jump in the air. LiquidBounce's, alongside Meteor's own.");
    }

    @Override
    public void onActivate() {
        charged = true;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player.onGround()) charged = true;
    }

    @EventHandler
    private void onKey(KeyInputEvent event) {
        if (mode.get() == Mode.GhostBlock) return;
        if (event.action != KeyAction.Press || mc.gui.screen() != null) return;
        if (mc.player.onGround() || !mc.options.keyJump.matches(event.input)) return;

        if (mode.get() == Mode.Double) {
            if (!charged) return;
            charged = false;
        }

        mc.player.jumpFromGround();
    }

    @EventHandler
    private void onCollisionShape(CollisionShapeEvent event) {
        if (mode.get() != Mode.GhostBlock || mc.player == null) return;
        if (!mc.options.keyJump.isDown() || mc.player.onGround()) return;

        // Only the air directly beneath, and only while jump is held, so this is a floor for the moment
        // of the jump rather than a world you could walk around on.
        if (!event.state.isAir()) return;
        if (event.pos.getY() != mc.player.blockPosition().getY() - 1) return;
        if (event.pos.getX() != mc.player.blockPosition().getX() || event.pos.getZ() != mc.player.blockPosition().getZ()) return;

        event.shape = Shapes.block();
    }

    public enum Mode {
        /** Jump whenever you press it. */
        Free,
        /** One extra jump per landing. */
        Double,
        /** Give the air below you a floor while jump is held, and let the game jump off it. */
        GhostBlock
    }
}
