/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Drives you into a spear thrust so it lands for its full damage.
 *
 * <p>A spear does not deal damage for being pointed at something. Its damage window tests the speed
 * you are travelling along your own look direction, and the damage it deals scales with your speed
 * relative to what you hit - hence a spear being a charge weapon. Walking into a target barely clears
 * the threshold, and standing still misses it entirely.
 *
 * <p>The speed the check reads is the client's own reported movement. So a run-up can be replaced by
 * a burst: set the movement straight at the target for as many ticks as the distance needs, then the
 * exact negative of it for as many again. The thrust lands at the top of the arc and the round trip
 * ends where it started, which is what keeps the server's per-tick movement allowance from noticing.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class SpearKill extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Double> maxDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-distance")
        .description("How far away a target may be to charge at it.")
        .defaultValue(50)
        .min(3)
        .sliderRange(3, 200)
        .build()
    );

    private final Setting<Double> maxSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-speed")
        .description("Blocks per tick the charge may claim. Higher closes the gap in fewer ticks and is far more visible.")
        .defaultValue(7)
        .min(2)
        .sliderRange(2, 10)
        .build()
    );

    private final Setting<Boolean> requireAttackKey = sgGeneral.add(new BoolSetting.Builder()
        .name("require-attack-key")
        .description("Only charge while the attack key is held, so a charged spear can be carried without firing.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Draw a box on the target that would be charged.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .defaultValue(ShapeMode.Both)
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .defaultValue(new SettingColor(255, 0, 0, 60))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .defaultValue(new SettingColor(255, 255, 255, 160))
        .visible(render::get)
        .build()
    );

    /** The movement to apply, one entry per tick: out, then back, then a stop. */
    private final Deque<Vec3> charge = new ArrayDeque<>();
    private LivingEntity target;

    public SpearKill() {
        super(Categories.Combat, "spear-kill", "Charges you into a spear thrust so it lands for full damage.", "auto-spear");
    }

    @Override
    public void onDeactivate() {
        cancelCharge();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        KineticWeapon weapon = chargedWeapon();
        if (weapon == null) {
            cancelCharge();
            return;
        }

        // Nothing before the delay counts, and movement spent there is wasted - it also resets the
        // window the damage conditions are measured against.
        if (mc.player.getTicksUsingItem() <= weapon.delayTicks()) {
            charge.clear();
            return;
        }

        if (!charge.isEmpty()) {
            mc.player.setDeltaMovement(charge.removeFirst());
            return;
        }

        target = findTarget();
        if (target == null) return;

        if (requireAttackKey.get() && !mc.options.keyAttack.isDown()) return;

        // Past the damage window there is nothing left to land, so a charge started now would only be
        // a visible teleport for no damage.
        if (mc.player.getTicksUsingItem() >= weapon.computeDamageUseDuration()) return;

        buildCharge(target);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || target == null || !target.isAlive() || chargedWeapon() == null) return;

        event.renderer.box(target.getBoundingBox(), sideColor.get(), lineColor.get(), shapeMode.get(), 0);
    }

    /** The spear component of the item being used, or null if a spear is not being held into a charge. */
    private KineticWeapon chargedWeapon() {
        if (mc.player == null || mc.level == null || !mc.player.isUsingItem()) return null;

        return mc.player.getUseItem().get(DataComponents.KINETIC_WEAPON);
    }

    /**
     * The nearest living thing along the look direction with a clear path to it. Aim is deliberately
     * not taken over - the charge follows where you are already pointing, so a miss is yours.
     */
    private LivingEntity findTarget() {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 end = eye.add(mc.player.getLookAngle().scale(maxDistance.get()));

        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;

        for (LivingEntity entity : mc.level.getEntitiesOfClass(LivingEntity.class, mc.player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1))) {
            if (entity == mc.player || !entity.isAlive()) continue;
            if (entity.getBoundingBox().clip(eye, end).isEmpty()) continue;

            double distance = mc.player.distanceToSqr(entity);
            if (distance >= bestDistance) continue;

            best = entity;
            bestDistance = distance;
        }

        return best;
    }

    /**
     * Fills the queue with the out-and-back pair. Both halves use the same per-tick step, so whatever
     * the outward leg claims the return leg gives back exactly.
     */
    private void buildCharge(LivingEntity to) {
        Vec3 eye = mc.player.getEyePosition();
        double distance = Math.sqrt(mc.player.distanceToSqr(to));

        int ticks = Math.max(1, (int) Math.ceil(distance / maxSpeed.get()));

        Vec3 direction = to.getBoundingBox().getCenter().subtract(eye);
        direction = direction.lengthSqr() > 0 ? direction.normalize() : mc.player.getLookAngle().normalize();

        Vec3 step = direction.scale(distance / ticks);

        for (int i = 0; i < ticks; i++) charge.addLast(step);
        for (int i = 0; i < ticks; i++) charge.addLast(step.scale(-1));
        charge.addLast(Vec3.ZERO);
    }

    private void cancelCharge() {
        // Abandoning mid-flight would leave the claimed velocity running, which walks straight into a
        // correction from the server.
        if (!charge.isEmpty() && mc.player != null) mc.player.setDeltaMovement(Vec3.ZERO);

        charge.clear();
        target = null;
    }
}
