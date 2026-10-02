/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.profiles;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.macros.Macros;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.waypoints.Waypoints;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.ISerializable;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

public class Profile implements ISerializable<Profile> {
    public final Settings settings = new Settings();

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSave = settings.createGroup("Save");
    private final SettingGroup sgBinds = settings.createGroup("Binds");

    public Setting<String> name = sgGeneral.add(new StringSetting.Builder()
        .name("name")
        .description("The name of the profile.")
        .filter(Utils::fileNameFilter)
        .build()
    );

    public Setting<List<String>> loadOnJoin = sgGeneral.add(new StringListSetting.Builder()
        .name("load-on-join")
        .description("Which servers to set this profile as active when joining.")
        .filter(Utils::ipFilter)
        .build()
    );

    public Setting<Boolean> hud = sgSave.add(new BoolSetting.Builder()
        .name("hud")
        .description("Whether the profile should save hud.")
        .defaultValue(false)
        .build()
    );

    public Setting<Boolean> macros = sgSave.add(new BoolSetting.Builder()
        .name("macros")
        .description("Whether the profile should save macros.")
        .defaultValue(false)
        .build()
    );

    public Setting<Boolean> modules = sgSave.add(new BoolSetting.Builder()
        .name("modules")
        .description("Whether the profile should save modules.")
        .defaultValue(true)
        .build()
    );

    public Setting<Boolean> waypoints = sgSave.add(new BoolSetting.Builder()
        .name("waypoints")
        .description("Whether the profile should save waypoints.")
        .defaultValue(false)
        .build()
    );

    public Setting<Keybind> loadBind = sgBinds.add(new KeybindSetting.Builder()
        .name("load-bind")
        .description("Load this profile without opening a menu.")
        .build()
    );

    public Setting<Keybind> saveBind = sgBinds.add(new KeybindSetting.Builder()
        .name("save-bind")
        .description("Overwrite this profile with your current config, without opening a menu.")
        .build()
    );

    public Setting<Boolean> autoSave = sgBinds.add(new BoolSetting.Builder()
        .name("auto-save")
        .description("Save this profile automatically when you leave a world, while it is the loaded profile.")
        .defaultValue(false)
        .build()
    );

    public Profile() {
    }

    public Profile(Tag tag) {
        fromTag((CompoundTag) tag);
    }

    public void load() {
        File folder = getFile();

        if (hud.get()) Hud.get().load(folder);
        if (macros.get()) Macros.get().load(folder);
        if (modules.get()) Modules.get().load(folder);
        if (waypoints.get()) Waypoints.get().load(folder);

        Profiles.get().setLoaded(this);
    }

    public void save() {
        File folder = getFile();

        if (hud.get()) Hud.get().save(folder);
        if (macros.get()) Macros.get().save(folder);
        if (modules.get()) Modules.get().save(folder);
        if (waypoints.get()) Waypoints.get().save(folder);

        Profiles.get().setLoaded(this);
    }

    /** Whether this profile saves anything at all - one that saves nothing is a silent no-op. */
    public boolean savesAnything() {
        return hud.get() || macros.get() || modules.get() || waypoints.get();
    }

    /** What this profile covers, for chat feedback: "modules, hud". */
    public String describeContents() {
        StringBuilder sb = new StringBuilder();

        if (modules.get()) sb.append("modules");
        if (hud.get()) sb.append(sb.isEmpty() ? "hud" : ", hud");
        if (macros.get()) sb.append(sb.isEmpty() ? "macros" : ", macros");
        if (waypoints.get()) sb.append(sb.isEmpty() ? "waypoints" : ", waypoints");

        return sb.isEmpty() ? "nothing" : sb.toString();
    }

    public void delete() {
        try {
            FileUtils.deleteDirectory(getFile());
        } catch (IOException e) {
            MeteorClient.LOG.error("Error deleting profile {}", name.get(), e);
        }
    }

    public File getFile() {
        return new File(Profiles.FOLDER, name.get());
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();

        tag.put("settings", settings.toTag());

        return tag;
    }

    @Override
    public Profile fromTag(CompoundTag tag) {
        if (tag.contains("settings")) {
            settings.fromTag(tag.getCompoundOrEmpty("settings"));
        }

        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Profile profile = (Profile) o;
        return Objects.equals(profile.name.get(), this.name.get());
    }
}
