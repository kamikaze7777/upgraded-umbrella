/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Items;

/**
 * Reels in when a fish bites, and casts again.
 *
 * <p>This is LiquidBounce's take on the same idea as Meteor's {@code auto-fish}, kept alongside it
 * under the {@code l} suffix the rest of this port uses for name collisions. Both listen for the
 * splash sound, because that is the only signal a bite produces. The difference is what happens next.
 *
 * <p>The splash packet carries a position, and a bite on <i>your</i> bobber plays it at your bobber.
 * Anyone else fishing nearby plays the same sound somewhere else, and a client that only listens for
 * the sound reels in on their bite instead of yours - which on a busy dock means reeling in nothing,
 * over and over. Comparing the sound's position against where your own hook is costs nothing and
 * removes the whole class of false trigger.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class AutoFishl extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> checkDistance = sgGeneral.add(new BoolSetting.Builder()
        .name("check-distance")
        .description("Only react to a splash played at your own bobber.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> maxDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-distance")
        .description("How far the splash may be from your hook. It is normally within a tenth of a block.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(checkDistance::get)
        .build()
    );

    private final Setting<Integer> reelDelay = sgGeneral.add(new IntSetting.Builder()
        .name("reel-delay")
        .description("Ticks to wait before reeling in, so the reaction is not instant.")
        .defaultValue(6)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Boolean> recast = sgGeneral.add(new BoolSetting.Builder()
        .name("recast")
        .description("Throw the line back out after reeling in.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> recastDelay = sgGeneral.add(new IntSetting.Builder()
        .name("recast-delay")
        .description("Ticks between the reel and the next cast.")
        .defaultValue(18)
        .min(1)
        .sliderRange(1, 40)
        .visible(recast::get)
        .build()
    );

    private final Setting<Boolean> autoCast = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-cast")
        .description("Cast when there is no line out at all, so it starts itself.")
        .defaultValue(false)
        .build()
    );

    /** Set from the network thread when a splash lands on our hook, read and cleared on the tick. */
    private volatile boolean bite;

    /** Ticks until the next action. */
    private int timer;

    /** Set while the reel is waiting out its delay, so the timer means "reel" rather than "cast". */
    private boolean reeling;

    public AutoFishl() {
        super(Categories.Player, "auto-fishl", "Reels in when a fish bites, matched to your own bobber.");
    }

    @Override
    public void onActivate() {
        bite = false;
        reeling = false;
        timer = 0;
    }

    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        if (!(event.packet instanceof ClientboundSoundPacket packet)) return;
        if (packet.getSound().value() != SoundEvents.FISHING_BOBBER_SPLASH) return;

        // Reading the packet is safe on this thread; the hook lookup needs the world, so it waits.
        mc.execute(() -> {
            FishingHook hook = mc.player == null ? null : mc.player.fishing;
            if (hook == null || hook.isRemoved()) return;

            if (checkDistance.get()) {
                double distanceSq = hook.position().distanceToSqr(packet.getX(), packet.getY(), packet.getZ());
                if (distanceSq > maxDistance.get() * maxDistance.get()) return;
            }

            bite = true;
        });
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        FindItemResult rod = InvUtils.findInHotbar(Items.FISHING_ROD);
        if (!rod.found()) return;

        if (timer > 0) {
            timer--;
            return;
        }

        // The delay between the bite and the reel is the whole of what makes this look like a person
        // rather than a packet, so it is spent before the click rather than after it.
        if (reeling) {
            reeling = false;

            use(rod.slot());
            timer = recastDelay.get();
            return;
        }

        if (bite) {
            bite = false;
            reeling = true;
            timer = reelDelay.get();
            return;
        }

        FishingHook hook = mc.player.fishing;

        // Nothing in the water. That is either the moment after a reel, or a line that was never cast.
        if ((hook == null || hook.isRemoved()) && (autoCast.get() || recast.get())) {
            use(rod.slot());
            timer = recastDelay.get();
        }
    }

    private void use(int slot) {
        InvUtils.swap(slot, true);
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        InvUtils.swapBack();
    }
}
