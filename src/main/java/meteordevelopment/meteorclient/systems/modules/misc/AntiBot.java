/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * Recognises the fake players an anti-cheat spawns to see whether you attack them.
 *
 * <p>A real player has an entry in the player list, one entry, and a profile that matches the entity.
 * A spawned decoy usually breaks one of those: it is not listed at all, or it borrows a name that is
 * already taken, or it appears twice. None of the three is proof on its own - a vanished player and a
 * player on a lobby server both look like the first - so each is a separate switch.
 *
 * <p>What this exposes is a test, not an action. Other modules ask it whether an entity is worth
 * treating as a player; nothing here attacks or hides anything by itself.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class AntiBot extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> notInTabList = sgGeneral.add(new BoolSetting.Builder()
        .name("not-in-tab-list")
        .description("Treat a player with no entry in the player list as fake.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> duplicateName = sgGeneral.add(new BoolSetting.Builder()
        .name("duplicate-name")
        .description("Treat a player whose name is already taken by a different account as fake.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> noProfileId = sgGeneral.add(new BoolSetting.Builder()
        .name("no-profile")
        .description("Treat a player with no matching profile in the list as fake.")
        .defaultValue(false)
        .build()
    );

    /** How many list entries carry each name, rebuilt only when asked so it costs nothing while idle. */
    private final Object2IntMap<String> nameCounts = new Object2IntOpenHashMap<>();
    private int countsTick = -1;

    public AntiBot() {
        super(Categories.Misc, "anti-bot", "Recognises fake players spawned to test whether you attack them.");
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        nameCounts.clear();
        countsTick = -1;
    }

    /** Whether this entity looks like a decoy rather than a real player. */
    public boolean isBot(Entity entity) {
        if (!isActive() || entity == mc.player) return false;
        if (!(entity instanceof Player player) || mc.getConnection() == null) return false;

        PlayerInfo info = mc.getConnection().getPlayerInfo(player.getUUID());

        if (notInTabList.get() && !isListed(player.getUUID())) return true;
        if (noProfileId.get() && info == null) return true;

        return duplicateName.get() && info != null && nameCount(info.getProfile().name()) > 1;
    }

    private boolean isListed(UUID id) {
        return mc.getConnection().getListedOnlinePlayers().stream().anyMatch(info -> info.getProfile().id().equals(id));
    }

    /**
     * Names are counted once per tick and reused, because this is called for every entity in range and
     * the list does not change within a tick.
     */
    private int nameCount(String name) {
        if (mc.player != null && countsTick != mc.player.tickCount) {
            countsTick = mc.player.tickCount;
            nameCounts.clear();

            for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                nameCounts.mergeInt(info.getProfile().name(), 1, Integer::sum);
            }
        }

        return nameCounts.getInt(name);
    }
}
