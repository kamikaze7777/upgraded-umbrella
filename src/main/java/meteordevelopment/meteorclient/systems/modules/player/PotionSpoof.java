/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StatusEffectAmplifierMapSetting;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Gives you effects locally that the server never granted.
 *
 * <p>Anything an effect changes that the client decides for itself follows: speed, jump height, mining
 * speed, night vision, the tint on the screen. Anything the server decides does not - damage taken and
 * damage dealt are still resolved server side against the effects it has for you, so strength here
 * changes nothing about a hit.
 *
 * <p>Useful for seeing in the dark and for testing what a movement value does. Not a way to hit harder.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class PotionSpoof extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Reference2IntMap<MobEffect>> effects = sgGeneral.add(new StatusEffectAmplifierMapSetting.Builder()
        .name("effects")
        .description("The effects to hold, and at what level.")
        .build()
    );

    public PotionSpoof() {
        super(Categories.Player, "potion-spoof", "Applies status effects locally, without the server granting them.");
    }

    @Override
    public void onDeactivate() {
        if (!Utils.canUpdate()) return;

        for (MobEffect effect : effects.get().keySet()) {
            // Only take back the ones that were ours. A spoof is written with no duration left, so an
            // effect with time on it came from the server and has to be left where it is.
            MobEffectInstance held = mc.player.getEffect(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect));
            if (held != null && held.getDuration() == 0) mc.player.removeEffect(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect));
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        for (Reference2IntMap.Entry<MobEffect> entry : effects.get().reference2IntEntrySet()) {
            int level = entry.getIntValue();
            if (level <= 0) continue;

            // Re-applied every tick with a zero duration, so it expires on its own the moment this
            // stops. Nothing is left behind if the client is closed mid-flight.
            mc.player.addEffect(new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(entry.getKey()), 0, level - 1, false, false, true));
        }
    }
}
