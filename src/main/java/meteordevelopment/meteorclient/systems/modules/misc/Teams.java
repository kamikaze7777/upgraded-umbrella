/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.TeamColor;

import java.util.Optional;

/**
 * Treats players on your own scoreboard team as friends, so nothing swings at them.
 *
 * <p>Team games hand out the roster through the scoreboard, which the client already has because it
 * needs it to colour names. Reading it means the friends list does not have to be maintained by hand
 * every time the teams are shuffled.
 *
 * <p>Not every server uses real teams. Some only recolour names, so matching on the colour is offered
 * as well - looser, and occasionally wrong on a server that colours by rank instead.
 *
 * <p>This answers Meteor's own "should I attack this player" question, which every combat module goes
 * through, rather than being wired into any one of them.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class Teams extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> byTeam = sgGeneral.add(new BoolSetting.Builder()
        .name("by-team")
        .description("Match on the scoreboard team itself.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> byColor = sgGeneral.add(new BoolSetting.Builder()
        .name("by-name-color")
        .description("Also match on name colour, for servers that colour names without real teams.")
        .defaultValue(true)
        .build()
    );

    public Teams() {
        super(Categories.Misc, "teams", "Treats players on your own scoreboard team as friends.");
    }

    /** Whether this player is on your side. */
    public boolean isTeammate(Player player) {
        if (!isActive() || mc.player == null || player == mc.player) return false;

        PlayerTeam mine = (PlayerTeam) mc.player.getTeam();
        PlayerTeam theirs = (PlayerTeam) player.getTeam();

        if (mine == null || theirs == null) return false;

        if (byTeam.get() && mine.getName().equals(theirs.getName())) return true;

        if (!byColor.get()) return false;

        // A team with no colour set carries none at all, and matching two of those would make every
        // uncoloured team on the server read as one team.
        Optional<TeamColor> color = mine.getColor();

        return color.isPresent() && color.equals(theirs.getColor());
    }
}
