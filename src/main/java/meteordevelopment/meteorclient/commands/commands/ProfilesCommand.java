/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.commands.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.commands.arguments.ProfileArgumentType;
import meteordevelopment.meteorclient.systems.profiles.Profile;
import meteordevelopment.meteorclient.systems.profiles.Profiles;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

public class ProfilesCommand extends Command {

    public ProfilesCommand() {
        super("profiles", "Loads and saves profiles.", "profile");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        // Bare `.profiles` lists what you have and which one you are on, so switching configs mid-game
        // never needs the menu.
        builder.executes(_ -> {
            if (Profiles.get().isEmpty()) {
                info("No profiles. Create one with (highlight)%s(default).", toString("create", "<name>"));
                return SINGLE_SUCCESS;
            }

            Profile loaded = Profiles.get().getLoaded();

            for (Profile profile : Profiles.get()) {
                info(describe(profile, profile.equals(loaded)));
            }

            return SINGLE_SUCCESS;
        });

        builder.then(literal("load").then(argument("profile", ProfileArgumentType.create()).executes(context -> {
            Profile profile = ProfileArgumentType.get(context);

            if (profile != null) {
                profile.load();
                info("Loaded profile (highlight)%s(default) (%s).", profile.name.get(), profile.describeContents());
            }

            return SINGLE_SUCCESS;
        })));

        // `.profiles save` with no argument overwrites whatever you last loaded - the whole point of
        // saving on the go is not having to name it again every time.
        builder.then(literal("save")
            .executes(_ -> {
                Profile profile = Profiles.get().getLoaded();

                if (profile == null) {
                    error("No profile loaded this session. Use (highlight)%s(default) or (highlight)%s(default).", toString("save", "<profile>"), toString("create", "<name>"));
                    return SINGLE_SUCCESS;
                }

                return save(profile);
            })
            .then(argument("profile", ProfileArgumentType.create()).executes(context -> {
                Profile profile = ProfileArgumentType.get(context);
                return profile == null ? SINGLE_SUCCESS : save(profile);
            }))
        );

        builder.then(literal("create").then(argument("name", StringArgumentType.word()).executes(context -> {
            String name = StringArgumentType.getString(context, "name");
            Profile profile = Profiles.get().create(name);

            if (profile == null) {
                error("Could not create (highlight)%s(default) - the name is taken, or holds characters other than letters, digits, - and _.", name);
                return SINGLE_SUCCESS;
            }

            info("Created and saved profile (highlight)%s(default) (%s).", profile.name.get(), profile.describeContents());

            return SINGLE_SUCCESS;
        })));

        builder.then(literal("delete").then(argument("profile", ProfileArgumentType.create()).executes(context -> {
            Profile profile = ProfileArgumentType.get(context);

            if (profile != null) {
                Profiles.get().remove(profile);
                info("Deleted profile (highlight)%s(default).", profile.name.get());
            }

            return SINGLE_SUCCESS;
        })));
    }

    private int save(Profile profile) {
        profile.save();

        if (profile.savesAnything()) info("Saved profile (highlight)%s(default) (%s).", profile.name.get(), profile.describeContents());
        else warning("Profile (highlight)%s(default) is set to save nothing, so nothing was written.", profile.name.get());

        return SINGLE_SUCCESS;
    }

    /** One list row: name, what it covers, its binds, and whether it is the one you are on. */
    private MutableComponent describe(Profile profile, boolean isLoaded) {
        MutableComponent tooltip = Component.literal("");
        tooltip.append(Component.literal("Saves: " + profile.describeContents()).withStyle(ChatFormatting.WHITE)).append("\n");
        tooltip.append(Component.literal("Load bind: " + profile.loadBind.get()).withStyle(ChatFormatting.GRAY)).append("\n");
        tooltip.append(Component.literal("Save bind: " + profile.saveBind.get()).withStyle(ChatFormatting.GRAY));

        MutableComponent line = Component.literal(profile.name.get()).withStyle(isLoaded ? ChatFormatting.GREEN : ChatFormatting.GRAY);
        line.append(Component.literal(" - " + profile.describeContents()).withStyle(ChatFormatting.DARK_GRAY));
        if (isLoaded) line.append(Component.literal(" (loaded)").withStyle(ChatFormatting.DARK_GREEN));
        if (profile.loadBind.get().isSet()) line.append(Component.literal(" [" + profile.loadBind.get() + "]").withStyle(ChatFormatting.DARK_GRAY));

        line.setStyle(line.getStyle().withHoverEvent(new HoverEvent.ShowText(tooltip)));

        return line;
    }
}
