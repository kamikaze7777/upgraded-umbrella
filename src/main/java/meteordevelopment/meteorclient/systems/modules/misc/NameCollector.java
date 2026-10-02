/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the current player list out to a file and switches itself off.
 *
 * <p>The player list is already on your screen; this only saves it, so that who was online at a given
 * moment survives past the session. It is the tab list and nothing more - no lookup is made, nothing
 * is sent anywhere.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class NameCollector extends Module {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    public NameCollector() {
        super(Categories.Misc, "name-collector", "Saves the current player list to a file.");
    }

    @Override
    public void onActivate() {
        if (mc.getConnection() == null) {
            toggle();
            return;
        }

        List<String> names = new ArrayList<>();
        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) names.add(info.getProfile().name());

        if (names.isEmpty()) {
            warning("Nobody is listed.");
            toggle();
            return;
        }

        names.sort(String.CASE_INSENSITIVE_ORDER);

        File folder = new File(MeteorClient.FOLDER, "name-captures");
        folder.mkdirs();

        File file = new File(folder, LocalDateTime.now().format(STAMP) + ".txt");

        try (PrintWriter writer = new PrintWriter(file, java.nio.charset.StandardCharsets.UTF_8)) {
            for (String name : names) writer.println(name);
            info("Saved %d names to %s.", names.size(), file.getName());
        } catch (IOException e) {
            error("Could not write the capture: %s", e.getMessage());
        }

        toggle();
    }
}
