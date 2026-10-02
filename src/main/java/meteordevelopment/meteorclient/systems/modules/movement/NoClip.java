/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.phys.Vec3;

/**
 * Turns off your own collision so you can move through blocks.
 *
 * <p>Only the client stops obeying walls. The server replays every claimed position against its own
 * collision and refuses one that ends inside a block, so on any ordinary server this holds until it
 * notices and then drags you back. It is genuinely free in spectator, and worth having on a server
 * that does not check - it is not a way through a wall on one that does.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class NoClip extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("How fast to drift while clipping.")
        .defaultValue(0.32)
        .min(0)
        .sliderRange(0, 1)
        .build()
    );

    private final Setting<Boolean> onlyInVehicle = sgGeneral.add(new BoolSetting.Builder()
        .name("only-in-vehicle")
        .description("Only clip while riding something, which some servers police less closely.")
        .defaultValue(false)
        .build()
    );

    public NoClip() {
        super(Categories.Movement, "no-clip", "Ignores block collision on the client. The server still checks.");
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null) mc.player.noPhysics = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (onlyInVehicle.get() && !mc.player.isPassenger()) {
            mc.player.noPhysics = false;
            return;
        }

        mc.player.noPhysics = true;

        // Inside a block there is nothing to fall onto and nothing to have fallen from, so keep the
        // fall counter at zero rather than banking damage for whenever we come out.
        mc.player.fallDistance = 0;

        // Hold height unless we are asked to climb or drop, so clipping does not sink through the floor.
        double y = 0;
        if (mc.options.keyJump.isDown()) y = speed.get();
        else if (mc.options.keyShift.isDown()) y = -speed.get();

        Vec3 movement = mc.player.getDeltaMovement();
        mc.player.setDeltaMovement(new Vec3(movement.x, y, movement.z));
    }
}
