/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.Target;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Set;

/**
 * Holds onto one target instead of picking a fresh one every tick.
 *
 * <p>Target selection normally re-runs constantly, so anyone who steps closer or turns up at a better
 * angle steals your aura mid-fight. Locking keeps the one you committed to until it dies, leaves range,
 * or you let it go, which is what makes chasing a single player through a crowd work.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class TargetLock extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargeting = settings.createGroup("Targeting");

    private final Setting<LockMode> lockMode = sgGeneral.add(new EnumSetting.Builder<LockMode>()
        .name("lock-mode")
        .description("""
                How the target gets chosen:
                - Automatic: Lock the first thing that matches, then hold it.
                - OnClick:   Lock whatever is under your crosshair when you attack.
            """)
        .defaultValue(LockMode.Automatic)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Keep facing the locked target.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> releaseOnDeath = sgGeneral.add(new BoolSetting.Builder()
        .name("release-on-death")
        .description("Let go once the target is dead, so the next one can be locked.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Set<EntityType<?>>> entities = sgTargeting.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Entities that may be locked.")
        .onlyAttackable()
        .defaultValue(EntityTypes.PLAYER)
        .build()
    );

    private final Setting<SortPriority> priority = sgTargeting.add(new EnumSetting.Builder<SortPriority>()
        .name("priority")
        .description("How to pick the target to lock, in automatic mode.")
        .defaultValue(SortPriority.LowestHealth)
        .visible(() -> lockMode.get() == LockMode.Automatic)
        .build()
    );

    private final Setting<Double> range = sgTargeting.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far the target may get before the lock is dropped.")
        .defaultValue(16)
        .min(0)
        .sliderRange(4, 64)
        .build()
    );

    private final Setting<Boolean> ignoreNamed = sgTargeting.add(new BoolSetting.Builder()
        .name("ignore-named")
        .description("Whether or not to lock mobs with a name.")
        .defaultValue(false)
        .build()
    );

    private Entity locked;

    public TargetLock() {
        super(Categories.Combat, "target-lock", "Holds one target instead of re-picking every tick.");
    }

    @Override
    public void onActivate() {
        locked = null;
    }

    @Override
    public void onDeactivate() {
        locked = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (locked != null && !stillValid(locked)) locked = null;

        if (locked == null) locked = acquire();
        if (locked == null) return;

        if (rotate.get()) {
            Rotations.rotate(Rotations.getYaw(locked), Rotations.getPitch(locked, Target.Body));
        }
    }

    private Entity acquire() {
        if (lockMode.get() == LockMode.OnClick) {
            // Only commit on a deliberate swing, so passing the crosshair over someone does not take.
            if (!mc.options.keyAttack.isDown()) return null;

            Entity aimed = mc.crosshairPickEntity;
            return aimed != null && canLock(aimed) ? aimed : null;
        }

        return TargetUtils.get(this::canLock, priority.get());
    }

    /** Whether the lock should survive another tick. Deliberately looser than the test to acquire one. */
    private boolean stillValid(Entity entity) {
        if (!entity.isAlive()) return false;
        if (releaseOnDeath.get() && entity instanceof LivingEntity living && living.isDeadOrDying()) return false;

        return PlayerUtils.isWithin(entity, range.get());
    }

    private boolean canLock(Entity entity) {
        if (entity.equals(mc.player) || entity.equals(mc.getCameraEntity())) return false;
        if ((entity instanceof LivingEntity living && living.isDeadOrDying()) || !entity.isAlive()) return false;
        if (!entities.get().contains(entity.getType())) return false;
        if (ignoreNamed.get() && entity.hasCustomName()) return false;
        if (!PlayerUtils.isWithin(entity, range.get())) return false;

        if (entity instanceof Player player) {
            if (player.isCreative()) return false;
            if (!Friends.get().shouldAttack(player)) return false;
        }

        return true;
    }

    /** The held target, for other modules that would rather follow this than pick their own. */
    public Entity getTarget() {
        return isActive() ? locked : null;
    }

    @Override
    public String getInfoString() {
        if (locked != null) return EntityUtils.getName(locked);
        return null;
    }

    public enum LockMode {
        Automatic,
        OnClick
    }
}
