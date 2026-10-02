/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

import java.util.List;

/**
 * Presses the recipe book's own craft button for you, over and over, until it stops working.
 *
 * <p>Filling the grid by hand is nine clicks per craft and easy to get wrong. The recipe book already
 * knows how to gather the ingredients out of your inventory in a single request, and that request is
 * exactly what a player clicking a recipe produces - so this is the same interaction repeated, not a
 * way to make something the server would otherwise refuse.
 *
 * <p>What can actually be made is answered by the game rather than guessed at: the ingredients you are
 * holding are tallied and each recipe is asked whether that is enough. So it stops on its own when the
 * materials run out, which is what makes it safe to leave running on a stack of logs.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class AutoCrafter extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<Item>> results = sgGeneral.add(new ItemListSetting.Builder()
        .name("results")
        .description("What to craft. The first one that can be made is the one that gets made.")
        .build()
    );

    private final Setting<Boolean> craftAll = sgGeneral.add(new BoolSetting.Builder()
        .name("craft-all")
        .description("Take as many as the ingredients allow per press, instead of one.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks between presses.")
        .defaultValue(3)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );

    /** Reused rather than rebuilt each press; it is cleared and refilled from the inventory. */
    private final StackedItemContents contents = new StackedItemContents();

    private int timer;

    public AutoCrafter() {
        super(Categories.Player, "auto-crafter", "Repeatedly crafts a chosen recipe from the recipe book.");
    }

    @Override
    public void onActivate() {
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        // Both screens carry a grid the recipe book can fill; anything else has nothing to craft in.
        if (!(mc.gui.screen() instanceof CraftingScreen) && !(mc.gui.screen() instanceof InventoryScreen)) return;
        if (results.get().isEmpty() || mc.player == null || mc.level == null) return;

        if (timer > 0) {
            timer--;
            return;
        }

        // What you are holding, tallied the way the recipe book tallies it, so "can this be made" is
        // answered by the game rather than reimplemented here.
        contents.clear();
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            contents.accountSimpleStack(mc.player.getInventory().getItem(i));
        }

        ContextMap context = SlotDisplayContext.fromLevel(mc.level);

        for (RecipeCollection collection : mc.player.getRecipeBook().getCollections()) {
            for (RecipeDisplayEntry entry : collection.getRecipes()) {
                if (!entry.canCraft(contents)) continue;
                if (!wanted(entry, context)) continue;

                mc.gameMode.handlePlaceRecipe(mc.player.containerMenu.containerId, entry.id(), craftAll.get());
                timer = delay.get();
                return;
            }
        }
    }

    /** Matches on what the recipe produces, which the recipe book has already worked out. */
    private boolean wanted(RecipeDisplayEntry entry, ContextMap context) {
        for (ItemStack result : entry.resultItems(context)) {
            if (results.get().contains(result.getItem())) return true;
        }

        return false;
    }
}
