/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Says in chat when something happens that you would otherwise have to be watching for.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code notifier}, kept alongside it
 * under the {@code l} suffix the rest of this port uses for name collisions. Meteor's covers more
 * events; what this adds is the running count.
 *
 * <p>A pop on its own is a message. Three pops from the same person is information - it says how close
 * they are to dying and whether the fight is worth staying in. Counting per player and reporting the
 * total is the difference between a log and something you can act on.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class Notifierl extends Module {
    /** The entity event id the server sends for a totem saving someone. */
    private static final byte TOTEM_OF_UNDYING = 35;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> totemPops = sgGeneral.add(new BoolSetting.Builder()
        .name("totem-pops")
        .description("Report when someone pops a totem, with how many they have used.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> visualRange = sgGeneral.add(new BoolSetting.Builder()
        .name("visual-range")
        .description("Report when a player comes into or leaves render distance.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreFriends = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Say nothing about people on your friends list.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far away to report at.")
        .defaultValue(64)
        .min(1)
        .sliderRange(8, 256)
        .visible(visualRange::get)
        .build()
    );

    /** Pops seen per entity id this session, which is what makes the count worth reporting. */
    private final Int2IntMap pops = new Int2IntOpenHashMap();

    /**
     * Who was in range last tick, by name. The name is kept rather than looked up on departure -
     * by the time someone has left, the entity that knew their name has gone with them.
     */
    private final Map<UUID, String> inRange = new HashMap<>();

    public Notifierl() {
        super(Categories.Misc, "notifierl", "Reports totem pops with a running count, and who comes into range.");
    }

    @Override
    public void onActivate() {
        pops.clear();
        inRange.clear();
    }

    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        if (!totemPops.get()) return;
        if (!(event.packet instanceof ClientboundEntityEventPacket packet) || packet.getEventId() != TOTEM_OF_UNDYING) return;

        // Resolving the entity needs the world, so it waits for the main thread.
        mc.execute(() -> {
            if (mc.level == null) return;

            Entity entity = packet.getEntity(mc.level);
            if (!(entity instanceof Player player) || player == mc.player) return;
            if (ignoreFriends.get() && !Friends.get().shouldAttack(player)) return;

            int count = pops.mergeInt(player.getId(), 1, Integer::sum);

            info("%s popped a totem (%d).", player.getName().getString(), count);
        });
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!visualRange.get()) return;

        Set<UUID> present = new HashSet<>();

        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;
            if (mc.player.distanceTo(player) > range.get()) continue;
            if (ignoreFriends.get() && !Friends.get().shouldAttack(player)) continue;

            present.add(player.getUUID());

            if (inRange.put(player.getUUID(), player.getName().getString()) == null) {
                info("%s came into range.", player.getName().getString());
            }
        }

        inRange.entrySet().removeIf(entry -> {
            if (present.contains(entry.getKey())) return false;

            info("%s left range.", entry.getValue());
            return true;
        });
    }

    @Override
    public String getInfoString() {
        return pops.isEmpty() ? null : pops.size() + " tracked";
    }
}
