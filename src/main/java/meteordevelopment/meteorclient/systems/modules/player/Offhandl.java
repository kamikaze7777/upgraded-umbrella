/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Decides what is in your offhand from what you are doing, not from a fixed choice.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code offhand}, kept alongside it under
 * the {@code l} suffix the rest of this port uses for name collisions. Meteor's holds one chosen item
 * with conditions for swapping off it; this one reads the situation each tick and picks.
 *
 * <p>The order is what the module is. A totem while your health is low beats everything, because
 * nothing else matters if the next hit kills you. A gapple while holding a sword, because that is a
 * fight and the healing is worth more than the block. A block while bridging, because that is the one
 * time an offhand block is the difference between crossing and falling. A totem otherwise.
 *
 * <p>Every swap is an ordinary inventory move, spaced out, so the server sees a player managing their
 * offhand rather than a client doing it instantly.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class Offhandl extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> totemHealth = sgGeneral.add(new DoubleSetting.Builder()
        .name("totem-health")
        .description("Hold a totem below this much health, whatever else is going on.")
        .defaultValue(12)
        .min(0)
        .max(20)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Boolean> gappleWithSword = sgGeneral.add(new BoolSetting.Builder()
        .name("gapple-with-sword")
        .description("Hold a golden apple while a sword is in your main hand.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> blockWhileBridging = sgGeneral.add(new BoolSetting.Builder()
        .name("block-while-bridging")
        .description("Hold a block while a scaffold module is running.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> totemOtherwise = sgGeneral.add(new BoolSetting.Builder()
        .name("totem-otherwise")
        .description("Fall back to a totem when nothing else applies.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks between swaps.")
        .defaultValue(4)
        .min(0)
        .sliderRange(0, 40)
        .build()
    );

    private int timer;

    public Offhandl() {
        super(Categories.Player, "offhandl", "Chooses your offhand item from what you are doing.", "auto-toteml");
    }

    @Override
    public void onActivate() {
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.gui.screen() != null) return;

        if (timer > 0) {
            timer--;
            return;
        }

        Wanted wanted = decide();
        if (wanted == null) return;

        ItemStack held = mc.player.getOffhandItem();
        if (wanted.matches(held)) return;

        int slot = find(wanted);
        if (slot == -1) return;

        InvUtils.move().from(slot).toOffhand();
        timer = delay.get();
    }

    /**
     * What should be in the offhand, in order of what would kill you first. The first rule that
     * applies wins, which is why the totem test comes before everything else.
     */
    private Wanted decide() {
        if (mc.player.getHealth() <= totemHealth.get()) return Wanted.Totem;

        if (gappleWithSword.get() && mc.player.getMainHandItem().is(net.minecraft.tags.ItemTags.SWORDS)) return Wanted.Gapple;

        if (blockWhileBridging.get() && isBridging()) return Wanted.Block;

        return totemOtherwise.get() ? Wanted.Totem : null;
    }

    private boolean isBridging() {
        return Modules.get().isActive(meteordevelopment.meteorclient.systems.modules.movement.Scaffold.class)
            || Modules.get().isActive(meteordevelopment.meteorclient.systems.modules.movement.Scaffoldl.class)
            || Modules.get().isActive(Eagle.class);
    }

    private int find(Wanted wanted) {
        for (int i = 0; i < 36; i++) {
            if (wanted.matches(mc.player.getInventory().getItem(i))) return i;
        }

        return -1;
    }

    private enum Wanted {
        Totem {
            @Override
            boolean matches(ItemStack stack) {
                return stack.is(Items.TOTEM_OF_UNDYING);
            }
        },
        Gapple {
            @Override
            boolean matches(ItemStack stack) {
                return stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE);
            }
        },
        Block {
            @Override
            boolean matches(ItemStack stack) {
                return stack.getItem() instanceof BlockItem;
            }
        };

        abstract boolean matches(ItemStack stack);
    }
}
