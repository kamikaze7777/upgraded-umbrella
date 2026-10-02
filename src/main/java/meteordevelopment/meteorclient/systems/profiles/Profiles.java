/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.profiles;

import com.mojang.blaze3d.platform.InputConstants;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.meteor.KeyInputEvent;
import meteordevelopment.meteorclient.events.meteor.MouseClickEvent;
import meteordevelopment.meteorclient.systems.System;
import meteordevelopment.meteorclient.systems.Systems;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.NbtUtils;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.nbt.CompoundTag;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class Profiles extends System<Profiles> implements Iterable<Profile> {
    public static final File FOLDER = new File(MeteorClient.FOLDER, "profiles");

    private List<Profile> profiles = new ArrayList<>();

    /**
     * The profile last loaded or saved this session.
     *
     * <p>Deliberately not serialised: it means "the config in front of you came from here", which is
     * only true for as long as nothing else has been changed, and that does not survive a restart.
     */
    private @Nullable Profile loaded;

    public Profiles() {
        super("profiles");
    }

    public static Profiles get() {
        return Systems.get(Profiles.class);
    }

    public void add(Profile profile) {
        if (!profiles.contains(profile)) profiles.add(profile);
        profile.save();
        save();
    }

    public void remove(Profile profile) {
        if (profiles.remove(profile)) profile.delete();
        if (profile.equals(loaded)) loaded = null;
        save();
    }

    /**
     * Create a profile that actually holds a config, and write it out immediately.
     *
     * <p>The GUI path leaves you to tick what to save; this one is for {@code .profiles create}, where
     * the whole point is one command and no menu, so it takes a full snapshot bar waypoints - those
     * are per-world and loading someone else's over yours is rarely what you meant.
     *
     * @return the new profile, or null if the name is empty or already taken
     */
    public @Nullable Profile create(String name) {
        Profile profile = new Profile();
        if (!profile.name.set(name) || profile.name.get().isEmpty()) return null;
        if (get(profile.name.get()) != null) return null;

        profile.modules.set(true);
        profile.hud.set(true);
        profile.macros.set(true);
        profile.waypoints.set(false);

        add(profile);

        return profile;
    }

    /** The profile last loaded or saved this session, if any. */
    public @Nullable Profile getLoaded() {
        return loaded;
    }

    public void setLoaded(@Nullable Profile profile) {
        loaded = profile;
    }

    public Profile get(String name) {
        for (Profile profile : this) {
            if (profile.name.get().equalsIgnoreCase(name)) {
                return profile;
            }
        }

        return null;
    }

    public List<Profile> getAll() {
        return profiles;
    }

    @Override
    public File getFile() {
        return new File(FOLDER, "profiles.nbt");
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        for (Profile profile : this) {
            if (profile.loadOnJoin.get().contains(Utils.getWorldName())) {
                profile.load();
            }
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        Profile profile = loaded;
        if (profile != null && profile.autoSave.get() && profiles.contains(profile)) profile.save();
    }

    // Binds
    //
    // Handled here rather than through KeybindSetting's own action so a deleted profile's binds die
    // with it - a KeybindSetting stays subscribed to the event bus for the life of the client.

    @EventHandler(priority = EventPriority.HIGH)
    private void onKey(KeyInputEvent event) {
        if (event.action != KeyAction.Press) return;
        onBind(true, event.key(), event.modifiers());
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onMouseClick(MouseClickEvent event) {
        if (event.action != KeyAction.Press) return;
        onBind(false, event.button(), 0);
    }

    private void onBind(boolean isKey, int value, int modifiers) {
        // Same gate the module binds use: not while a screen is eating input, not while F3 is held.
        if (mc.gui.screen() != null || Input.isKeyPressed(InputConstants.KEY_F3)) return;

        for (Profile profile : List.copyOf(profiles)) {
            if (profile.loadBind.get().matches(isKey, value, modifiers)) {
                profile.load();
                ChatUtils.infoPrefix("Profiles", "Loaded (highlight)%s(default) (%s).", profile.name.get(), profile.describeContents());
            }

            if (profile.saveBind.get().matches(isKey, value, modifiers)) {
                profile.save();
                ChatUtils.infoPrefix("Profiles", "Saved (highlight)%s(default) (%s).", profile.name.get(), profile.describeContents());
            }
        }
    }

    public boolean isEmpty() {
        return profiles.isEmpty();
    }

    @Override
    public @NonNull Iterator<Profile> iterator() {
        return profiles.iterator();
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.put("profiles", NbtUtils.listToTag(profiles));
        return tag;
    }

    @Override
    public Profiles fromTag(CompoundTag tag) {
        profiles = NbtUtils.listFromTag(tag.getListOrEmpty("profiles"), Profile::new);

        for (File file : FOLDER.listFiles()) {
            if (file.isDirectory() && get(file.getName()) == null) {
                Profile p = new Profile();
                p.name.set(file.getName());

                boolean add = false;
                for (File f : file.listFiles()) {
                    if (f.getName().equals("hud.nbt")) p.hud.set(add = true);
                    else if (f.getName().equals("macros.nbt")) p.macros.set(add = true);
                    else if (f.getName().equals("modules.nbt")) p.modules.set(add = true);
                    else if (f.getName().endsWith(".nbt")) p.waypoints.set(add = true);
                }

                if (add) add(p);
            }
        }

        return this;
    }
}
