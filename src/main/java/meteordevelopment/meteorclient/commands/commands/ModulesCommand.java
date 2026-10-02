/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.commands.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.ModuleOrigin;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;
import java.util.function.Predicate;

public class ModulesCommand extends Command {
    public ModulesCommand() {
        super("modules", "Displays a list of all modules.", "features");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(_ -> list(null));

        // This fork runs two module sets side by side, so "which half is this" is worth one word
        // in chat rather than a scroll through the clickgui.
        builder.then(literal("meteor").executes(_ -> list(ModuleOrigin.Meteor)));
        builder.then(literal("liquidbounce").executes(_ -> list(ModuleOrigin.LiquidBounce)));
        builder.then(literal("lb").executes(_ -> list(ModuleOrigin.LiquidBounce)));

        builder.then(literal("disable")
            .then(literal("all").executes(_ -> disable(null)))
            .then(literal("meteor").executes(_ -> disable(ModuleOrigin.Meteor)))
            .then(literal("liquidbounce").executes(_ -> disable(ModuleOrigin.LiquidBounce)))
            .then(literal("lb").executes(_ -> disable(ModuleOrigin.LiquidBounce)))
        );
    }

    /** @param origin null lists everything, and then breaks the total down by origin. */
    private int list(ModuleOrigin origin) {
        if (origin == null) {
            ChatUtils.info("--- Modules ((highlight)%d(default): (highlight)%d(default) Meteor, (highlight)%d(default) LiquidBounce) ---",
                Modules.get().getCount(), Modules.get().getCount(ModuleOrigin.Meteor), Modules.get().getCount(ModuleOrigin.LiquidBounce));
        } else {
            ChatUtils.info("--- %s modules ((highlight)%d(default)) ---", origin.title, Modules.get().getCount(origin));
        }

        Predicate<Module> shown = origin == null ? _ -> true : module -> module.origin == origin;

        for (Category category : Modules.loopCategories()) {
            List<Module> modules = Modules.get().getGroup(category).stream().filter(shown).toList();
            if (modules.isEmpty()) continue;

            MutableComponent categoryMessage = Component.literal("");
            for (int i = 0; i < modules.size(); i++) {
                categoryMessage.append(getModuleText(modules.get(i), i == modules.size() - 1));
            }

            ChatUtils.sendMsg(category.name, categoryMessage);
        }

        return SINGLE_SUCCESS;
    }

    /** @param origin null disables everything, as a panic key that also reports what it caught. */
    private int disable(ModuleOrigin origin) {
        if (origin == null) {
            int active = Modules.get().getActive().size();
            Modules.get().disableAll();
            info("Disabled (highlight)%d(default) modules.", active);
        } else {
            info("Disabled (highlight)%d(default) active %s modules.", Modules.get().disableAll(origin), origin.title);
        }

        return SINGLE_SUCCESS;
    }

    private MutableComponent getModuleText(Module module, boolean last) {
        // Hover tooltip
        MutableComponent tooltip = Component.literal("");

        tooltip.append(Component.literal(module.title).withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD)).append("\n");
        tooltip.append(Component.literal(module.name).withStyle(ChatFormatting.GRAY)).append("\n");
        tooltip.append(Component.literal(module.origin.title).withStyle(ChatFormatting.DARK_GRAY)).append("\n\n");
        tooltip.append(Component.literal(module.description).withStyle(ChatFormatting.WHITE));

        MutableComponent finalModule = Component.literal(module.title);
        if (!module.isActive()) finalModule.withStyle(ChatFormatting.GRAY);
        if (!last) finalModule.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
        finalModule.setStyle(finalModule.getStyle().withHoverEvent(new HoverEvent.ShowText(tooltip)));

        return finalModule;
    }

}
