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
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * Aims thrown and shot projectiles at whatever you are targeting, arcing for the drop on the way.
 *
 * <p>Where {@link BowAimbot} only covers the bow, this solves for whatever is in your hand - arrows,
 * tridents, pearls, snowballs, potions - each of which leaves your hand at its own speed and falls at
 * its own rate. Given those two numbers the launch angle that lands on a point is a closed form solve,
 * the same one the bow already uses here.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class ProjectileAimbot extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargeting = settings.createGroup("Targeting");

    private final Setting<Boolean> onlyWhenUsing = sgGeneral.add(new BoolSetting.Builder()
        .name("only-when-using")
        .description("Only aim while you are actually drawing or holding the projectile.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> predictMovement = sgGeneral.add(new BoolSetting.Builder()
        .name("predict-movement")
        .description("Lead a moving target by where it is heading rather than where it stands.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Set<EntityType<?>>> entities = sgTargeting.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Entities to aim at.")
        .onlyAttackable()
        .defaultValue(EntityTypes.PLAYER)
        .build()
    );

    private final Setting<SortPriority> priority = sgTargeting.add(new EnumSetting.Builder<SortPriority>()
        .name("priority")
        .description("How to pick between targets in range.")
        .defaultValue(SortPriority.ClosestAngle)
        .build()
    );

    private final Setting<Double> range = sgTargeting.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far away a target can be.")
        .defaultValue(40)
        .min(0)
        .sliderRange(5, 100)
        .build()
    );

    private final Setting<Boolean> ignoreNamed = sgTargeting.add(new BoolSetting.Builder()
        .name("ignore-named")
        .description("Whether or not to aim at mobs with a name.")
        .defaultValue(false)
        .build()
    );

    private Entity target;

    public ProjectileAimbot() {
        super(Categories.Combat, "projectile-aimbot", "Aims whatever projectile you are holding at your target.");
    }

    @Override
    public void onDeactivate() {
        target = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        target = null;

        Launch launch = launchFor(mc.player.getMainHandItem());
        if (launch == null) return;
        if (onlyWhenUsing.get() && !mc.player.isUsingItem()) return;

        Entity found = TargetUtils.get(this::entityCheck, priority.get());
        if (found == null) return;

        target = found;

        Vec3 aim = aimPoint(found);
        Double pitch = solvePitch(aim, launch);
        if (pitch == null) return;

        Rotations.rotate(Rotations.getYaw(aim), pitch);
    }

    /** Where to aim: the target's midriff, led by its current drift if asked for. */
    private Vec3 aimPoint(Entity entity) {
        Vec3 point = entity.position().add(0, entity.getBbHeight() / 2, 0);
        if (!predictMovement.get()) return point;

        // Nothing fancy: carry the target forward by the step it just took. Good enough for the flight
        // times involved, and it degrades to standing still when the target is.
        Vec3 drift = entity.position().subtract(entity.xOld, entity.yOld, entity.zOld);

        return point.add(drift.scale(flightTicks(point)));
    }

    /** A rough flight time in ticks, used only to decide how far to lead the target. */
    private double flightTicks(Vec3 aim) {
        return Math.sqrt(mc.player.getEyePosition().distanceToSqr(aim)) / 2;
    }

    /**
     * The launch angle that drops the projectile onto the point, for a projectile that leaves at a known
     * speed and falls at a known rate. Null when the point is simply out of reach of the throw.
     */
    private Double solvePitch(Vec3 aim, Launch launch) {
        Vec3 eyes = mc.player.getEyePosition();

        double dx = aim.x - eyes.x;
        double dy = aim.y - eyes.y;
        double dz = aim.z - eyes.z;

        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 1.0E-4) return null;

        double g = launch.gravity();
        double vSq = launch.speed() * launch.speed();

        // Negative here means no angle reaches that far; the throw cannot cover the distance.
        double discriminant = vSq * vSq - g * (g * horizontal * horizontal + 2 * dy * vSq);
        if (discriminant < 0) return null;

        return -Math.toDegrees(Math.atan((vSq - Math.sqrt(discriminant)) / (g * horizontal)));
    }

    /**
     * How fast the held item throws and how hard its projectile falls. A bow is the odd one out, since
     * how far it is drawn decides the speed.
     */
    private Launch launchFor(ItemStack stack) {
        return switch (stack.getItem()) {
            case BowItem ignored -> {
                float charge = BowItem.getPowerForTime(mc.player.getTicksUsingItem());
                yield charge <= 0.1f ? null : new Launch(charge * 3, 0.05);
            }
            case CrossbowItem ignored -> new Launch(3.15, 0.05);
            case TridentItem ignored -> new Launch(2.5, 0.05);
            case SnowballItem ignored -> new Launch(1.5, 0.03);
            case EggItem ignored -> new Launch(1.5, 0.03);
            case EnderpearlItem ignored -> new Launch(1.5, 0.03);
            case ExperienceBottleItem ignored -> new Launch(0.7, 0.07);
            case SplashPotionItem ignored -> new Launch(0.5, 0.05);
            case LingeringPotionItem ignored -> new Launch(0.5, 0.05);
            default -> null;
        };
    }

    private boolean entityCheck(Entity entity) {
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

    @Override
    public String getInfoString() {
        if (target != null) return EntityUtils.getName(target);
        return null;
    }

    /** The two numbers that decide a projectile's arc: how fast it leaves, and how hard it falls. */
    private record Launch(double speed, double gravity) {}
}
