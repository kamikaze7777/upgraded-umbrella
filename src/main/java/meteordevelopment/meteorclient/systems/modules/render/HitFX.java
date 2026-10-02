/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Spawns particles and a click where your hit lands.
 *
 * <p>Whether a swing connected is easy to miss in a fight, and vanilla only tells you through the
 * target's own hurt flash - which is invisible if they are behind you or off screen. Both halves here
 * are client side and local, so nobody else hears or sees any of it.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class HitFX extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Effect> effect = sgGeneral.add(new EnumSetting.Builder<Effect>()
        .name("particle")
        .description("Which particle to burst.")
        .defaultValue(Effect.Crit)
        .build()
    );

    private final Setting<Integer> count = sgGeneral.add(new IntSetting.Builder()
        .name("count")
        .description("Particles per hit.")
        .defaultValue(12)
        .min(0)
        .sliderRange(0, 60)
        .build()
    );

    private final Setting<Double> spread = sgGeneral.add(new DoubleSetting.Builder()
        .name("spread")
        .description("How far the burst scatters.")
        .defaultValue(0.4)
        .min(0)
        .sliderRange(0, 2)
        .build()
    );

    private final Setting<Boolean> sound = sgGeneral.add(new BoolSetting.Builder()
        .name("sound")
        .description("Play a click on a landed hit.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> volume = sgGeneral.add(new DoubleSetting.Builder()
        .name("volume")
        .defaultValue(0.4)
        .min(0)
        .max(1)
        .sliderRange(0, 1)
        .visible(sound::get)
        .build()
    );

    public HitFX() {
        super(Categories.Render, "hit-fx", "Bursts particles and plays a click where your hit lands.");
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        Vec3 at = event.entity.getBoundingBox().getCenter();
        ParticleOptions particle = effect.get().particle;

        for (int i = 0; i < count.get(); i++) {
            // Scattered around the centre of the target rather than at a single point, so the burst
            // reads as a hit on a body rather than as a dot in mid air.
            mc.level.addParticle(particle,
                at.x + (Math.random() - 0.5) * spread.get(),
                at.y + (Math.random() - 0.5) * spread.get(),
                at.z + (Math.random() - 0.5) * spread.get(),
                0, 0, 0
            );
        }

        // Played locally at the player rather than at the target, so it is heard at full volume however
        // far away the hit landed.
        if (sound.get()) {
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, volume.get().floatValue(), 1.6f, false);
        }
    }

    public enum Effect {
        Crit(ParticleTypes.CRIT),
        MagicCrit(ParticleTypes.ENCHANTED_HIT),
        Flame(ParticleTypes.FLAME),
        Smoke(ParticleTypes.SMOKE),
        Heart(ParticleTypes.HEART),
        Lava(ParticleTypes.LAVA);

        public final ParticleOptions particle;

        Effect(ParticleOptions particle) {
            this.particle = particle;
        }
    }
}
