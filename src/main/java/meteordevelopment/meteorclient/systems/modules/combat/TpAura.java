/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IServerboundMovePlayerPacket;
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
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;

/**
 * Hits targets past reach by standing next to them for exactly one attack. The player is moved to the
 * target, the position is reported, the attack goes out, and the player is put back, all inside one
 * tick and without the client ever rendering a frame in between.
 *
 * <p>The point is that the attack is never asked to reach further than vanilla allows. The server
 * range checks it against the last position it accepted, so by the time it arrives we are genuinely
 * standing next to the target as far as the server is concerned, and a normal reach check passes.
 *
 * <p>The server will only take a position within about ten blocks of where the player started the
 * tick, and only if the swept path to it is clear and the player fits at the end of it. A round trip
 * that starts and ends where the player really is costs nothing against that budget, so the hop is
 * free as long as the way there is open.
 *
 * <p>This makes no attempt to look like legitimate movement and no anticheat will miss it.
 */
public class TpAura extends Module {
    // How far the server lets a position stray before calling it "moved too quickly" and rubber-banding
    // us depends on how many movement packets it has already taken this tick: the allowance is
    // max(100, (10 * packets * speed)^2) against squared distance, and walking speed works out to 1. So
    // the first packet of a tick buys ten blocks and the second buys twenty. Nothing here is ever the
    // first, because the flat packet that opens the hop primes exactly this. Twenty is the wall.
    private static final double TICK_BUDGET = 19;

    // Directions to try when the way straight in is blocked or occupied.
    private static final int APPROACH_ATTEMPTS = 8;

    private static final double EPSILON = 1.0E-4;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargeting = settings.createGroup("Targeting");
    private final SettingGroup sgTiming = settings.createGroup("Timing");

    // General

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far away a target can be. The hop covers the distance, so this goes well past reach.")
        .defaultValue(16)
        .min(0)
        .sliderRange(4, TICK_BUDGET + 2)
        .build()
    );

    private final Setting<Double> standoff = sgGeneral.add(new DoubleSetting.Builder()
        .name("standoff")
        .description("How far from the target hitbox to stand for the hit. Well inside reach so the attack is never a stretch.")
        .defaultValue(1.5)
        .min(0)
        .sliderRange(0, 3)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Face the target in the packet sent from over there.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> smashAttack = sgGeneral.add(new BoolSetting.Builder()
        .name("smash-attack")
        .description("Spoof a fall at the far end when holding a mace, so the hit lands as a smash attack.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> fallDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("fall-distance")
        .description("The fall distance to spoof at the far end. Shares the tick budget with the hop, so a far target leaves less of it.")
        .defaultValue(8)
        .min(MaceItem.SMASH_ATTACK_FALL_THRESHOLD)
        .max(TICK_BUDGET)
        .sliderRange(MaceItem.SMASH_ATTACK_FALL_THRESHOLD, TICK_BUDGET)
        .visible(smashAttack::get)
        .build()
    );

    private final Setting<Boolean> onlyOnClick = sgGeneral.add(new BoolSetting.Builder()
        .name("only-on-click")
        .description("Only attacks when holding left click.")
        .defaultValue(false)
        .build()
    );

    // Targeting

    private final Setting<Set<EntityType<?>>> entities = sgTargeting.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Entities to attack.")
        .onlyAttackable()
        .defaultValue(EntityTypes.PLAYER)
        .build()
    );

    private final Setting<SortPriority> priority = sgTargeting.add(new EnumSetting.Builder<SortPriority>()
        .name("priority")
        .description("How to filter targets within range.")
        .defaultValue(SortPriority.LowestHealth)
        .build()
    );

    private final Setting<Boolean> ignoreNamed = sgTargeting.add(new BoolSetting.Builder()
        .name("ignore-named")
        .description("Whether or not to attack mobs with a name.")
        .defaultValue(false)
        .build()
    );

    // Timing

    private final Setting<Boolean> pauseOnLag = sgTiming.add(new BoolSetting.Builder()
        .name("pause-on-lag")
        .description("Pauses if the server is lagging. A hop that lands a tick late is a hop the server throws out.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> customDelay = sgTiming.add(new BoolSetting.Builder()
        .name("custom-delay")
        .description("Use a custom delay instead of the vanilla cooldown. Hitting early costs you the charge multiplier.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> hitDelay = sgTiming.add(new IntSetting.Builder()
        .name("hit-delay")
        .description("How fast you hit the entity in ticks.")
        .defaultValue(11)
        .min(0)
        .sliderMax(60)
        .visible(customDelay::get)
        .build()
    );

    /**
     * Set while the hop is in flight. Criticals reads this so its own smash spoof does not fire from
     * where the player really stands, which would drag the server back there and leave the attack
     * behind it out of range.
     */
    public boolean teleporting;

    private Entity target;
    private int hitTimer;

    public TpAura() {
        super(Categories.Combat, "tp-aura", "Steps to targets to hit them and returns in the same tick.");
    }

    @Override
    public void onActivate() {
        target = null;
        hitTimer = 0;
        teleporting = false;
    }

    @Override
    public void onDeactivate() {
        target = null;
        teleporting = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        target = null;

        if (!mc.player.isAlive() || PlayerUtils.getGameMode() == GameType.SPECTATOR) return;
        if (onlyOnClick.get() && !mc.options.keyAttack.isDown()) return;
        if (pauseOnLag.get() && TickRate.INSTANCE.getTimeSinceLastTick() >= 1f) return;

        // The hop rests on the server trusting a position we hand it, and it stops doing that while it
        // is already correcting us or while something else owns our position.
        if (mc.player.isPassenger() || mc.player.isSleeping()) return;

        Entity found = TargetUtils.get(this::entityCheck, priority.get());
        if (found == null) return;

        target = found;

        if (!delayCheck()) return;

        Vec3 origin = mc.player.position();
        Vec3 destination = findDestination(found, origin);

        // Nowhere to stand that the server would accept means the hit would be thrown out, and
        // swinging anyway just flails at the target. Wait for a tick that works instead.
        if (destination == null) return;

        hop(found, origin, destination);

        hitTimer = 0;
    }

    /**
     * Moves the player over, hits, and moves them back. The client is moved for real rather than only
     * described in a packet, so everything that reads our position during the attack - the client's own
     * hit prediction included - agrees with what the server was just told.
     */
    private void hop(Entity entity, Vec3 origin, Vec3 destination) {
        double rise = riseFor(origin, destination);

        float yaw = rotate.get() ? (float) Rotations.getYaw(entity) : mc.player.getYRot();
        float pitch = rotate.get() ? (float) Rotations.getPitch(entity, Target.Body) : mc.player.getXRot();

        boolean onGround = mc.player.onGround();

        teleporting = true;

        try {
            // Standing still with no rotation refills the server's packet allowance for this tick, which
            // is what keeps the hop from reading as moving too quickly.
            send(origin, yaw, pitch, false);

            if (rise > 0) {
                // Arrive high and drop in. The server reads the drop as a fall of exactly the rise and
                // hands the mace its smash bonus for it.
                send(destination.add(0, rise, 0), yaw, pitch, false);
            }

            move(destination);
            send(destination, yaw, pitch, false);

            mc.gameMode.attack(mc.player, entity);
            mc.player.swing(InteractionHand.MAIN_HAND);
        } finally {
            // Back before anything renders, so the hop is never drawn and the client's own movement
            // packet next tick agrees with where we left the server.
            move(origin);
            send(origin, mc.player.getYRot(), mc.player.getXRot(), onGround);

            teleporting = false;
        }
    }

    /** Puts the player somewhere without running any physics or firing a move event. */
    private void move(Vec3 position) {
        mc.player.setPos(position.x, position.y, position.z);

        // Both previous positions travel with us: the render one so nothing can smear the player across
        // the hop for a frame, and the tick one so anything reading our movement this tick - the hit
        // prediction behind the attack included - does not see the hop as a lunge we just made.
        mc.player.xOld = position.x;
        mc.player.yOld = position.y;
        mc.player.zOld = position.z;

        mc.player.xo = position.x;
        mc.player.yo = position.y;
        mc.player.zo = position.z;
    }

    /**
     * A spot next to the target the server would accept: close enough that the attack passes its range
     * check, reachable in a straight line, clear of blocks, and inside the tick budget. Straight in
     * first, then swinging around the target.
     */
    private Vec3 findDestination(Entity entity, Vec3 origin) {
        AABB hitbox = entity.getBoundingBox();
        Vec3 center = hitbox.getCenter();

        double radius = Math.max(hitbox.getXsize(), hitbox.getZsize()) / 2 + standoff.get();

        Vec3 approach = new Vec3(origin.x - center.x, 0, origin.z - center.z);
        if (approach.lengthSqr() < EPSILON) approach = new Vec3(1, 0, 0);
        approach = approach.normalize();

        for (int i = 0; i < APPROACH_ATTEMPTS; i++) {
            // Fan out from the direct line, alternating sides, so the closest approach wins.
            double angle = ((i + 1) / 2) * (Math.PI / 4) * ((i % 2 == 0) ? 1 : -1);
            Vec3 offset = approach.yRot((float) angle).scale(radius);
            Vec3 candidate = new Vec3(center.x + offset.x, entity.getY(), center.z + offset.z);

            if (isViable(entity, origin, candidate)) return candidate;
        }

        return null;
    }

    private boolean isViable(Entity entity, Vec3 origin, Vec3 candidate) {
        if (origin.distanceToSqr(candidate) > Mth.square(TICK_BUDGET)) return false;
        if (!inAttackRange(entity, candidate)) return false;
        if (!fits(origin, candidate)) return false;

        return isClear(origin, candidate);
    }

    /**
     * Whether the server would accept an attack on the target from here. It measures eyes to hitbox
     * against the interaction range attribute; servers usually allow slack on top of that, but there is
     * no way to read theirs, so ask for none and stay inside the part everyone honours.
     */
    private boolean inAttackRange(Entity entity, Vec3 from) {
        double reach = mc.player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
        Vec3 eyes = from.add(0, mc.player.getEyeHeight(), 0);

        return entity.getBoundingBox().distanceToSqr(eyes) <= Mth.square(reach);
    }

    /** Whether the player would fit here, which the server checks on arrival. */
    private boolean fits(Vec3 origin, Vec3 position) {
        return mc.level.noCollision(mc.player, boxAt(origin, position));
    }

    /** Whether the swept path is clear, which the server checks by replaying the move with collision. */
    private boolean isClear(Vec3 origin, Vec3 destination) {
        Vec3 movement = destination.subtract(origin);
        Vec3 allowed = Entity.collideBoundingBox(mc.player, movement, boxAt(origin, origin), mc.level, List.of());

        return allowed.distanceToSqr(movement) < EPSILON;
    }

    private AABB boxAt(Vec3 origin, Vec3 position) {
        return mc.player.getBoundingBox().move(position.subtract(origin));
    }

    /**
     * How far to arrive above the destination before dropping onto it, which is the fall distance the
     * mace scores its smash bonus from. Zero unless we are holding a mace with budget and room to spare.
     */
    private double riseFor(Vec3 origin, Vec3 destination) {
        if (!smashAttack.get()) return 0;
        if (!(mc.player.getMainHandItem().getItem() instanceof MaceItem)) return 0;
        if (mc.player.isFallFlying() || mc.player.isInWater()) return 0;

        // The high point is claimed from where the player really stands, so the hop has first call on
        // the budget and the rise takes whatever vertical room is left inside it.
        double dx = destination.x - origin.x;
        double dy = destination.y - origin.y;
        double dz = destination.z - origin.z;

        double vertical = Mth.square(TICK_BUDGET) - dx * dx - dz * dz;
        if (vertical <= 0) return 0;

        double rise = Math.min(fallDistance.get(), Math.sqrt(vertical) - dy);
        if (rise <= MaceItem.SMASH_ATTACK_FALL_THRESHOLD) return 0;

        // The drop is collision checked like any other move, so it has to start in open air.
        Vec3 allowed = Entity.collideBoundingBox(mc.player, new Vec3(0, rise, 0), boxAt(origin, destination), mc.level, List.of());
        rise = Math.max(0, allowed.y - EPSILON);

        // Under the threshold the server does not count it as a smash at all, so there is nothing to buy
        // with the desync.
        if (rise <= MaceItem.SMASH_ATTACK_FALL_THRESHOLD) return 0;

        // Arriving high moves the hop itself, which now runs diagonally rather than flat. Clearing the
        // flat leg and the drop separately does not clear that diagonal past an overhang.
        Vec3 apex = destination.add(0, rise, 0);
        if (!fits(origin, apex) || !isClear(origin, apex)) return 0;

        return rise;
    }

    private void send(Vec3 position, float yaw, float pitch, boolean onGround) {
        ServerboundMovePlayerPacket packet = new ServerboundMovePlayerPacket.PosRot(
            position.x, position.y, position.z, yaw, pitch, onGround, mc.player.horizontalCollision
        );

        // NoFall rewrites the on ground flag of anything it does not recognise, which would wipe the
        // fall distance a smash attack rides on.
        ((IServerboundMovePlayerPacket) packet).meteor$setTag(1337);

        mc.player.connection.send(packet);
    }

    private boolean delayCheck() {
        if (!customDelay.get()) return mc.player.getAttackStrengthScale(0.5f) >= 1;

        if (hitTimer < hitDelay.get()) {
            hitTimer++;
            return false;
        }

        return true;
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
}
