/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Replaces the vanilla crosshair with a plain cross you can size and colour.
 *
 * <p>The vanilla one inverts whatever is behind it, which makes it disappear against roughly half the
 * blocks in the game. A flat colour with a gap in the middle stays visible everywhere, and the gap is
 * what keeps it from hiding the thing you are aiming at.
 *
 * <p>The colour can also carry one piece of information: whether what you are pointed at is an entity.
 * That is the same hit result the game already computed for its own use, so it costs nothing and is
 * exactly as accurate as the game's own aim.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class Crosshair extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> length = sgGeneral.add(new IntSetting.Builder()
        .name("length")
        .description("Length of each arm, in pixels.")
        .defaultValue(6)
        .min(1)
        .sliderRange(1, 30)
        .build()
    );

    private final Setting<Integer> thickness = sgGeneral.add(new IntSetting.Builder()
        .name("thickness")
        .defaultValue(2)
        .min(1)
        .sliderRange(1, 8)
        .build()
    );

    private final Setting<Integer> gap = sgGeneral.add(new IntSetting.Builder()
        .name("gap")
        .description("Empty space in the middle, so the crosshair does not cover the target.")
        .defaultValue(3)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Boolean> dot = sgGeneral.add(new BoolSetting.Builder()
        .name("dot")
        .description("Put a dot in the gap.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> highlightTargets = sgGeneral.add(new BoolSetting.Builder()
        .name("highlight-targets")
        .description("Change colour when an entity is under the crosshair.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .defaultValue(new SettingColor(255, 255, 255, 220))
        .build()
    );

    private final Setting<SettingColor> targetColor = sgGeneral.add(new ColorSetting.Builder()
        .name("target-color")
        .defaultValue(new SettingColor(255, 70, 70, 240))
        .visible(highlightTargets::get)
        .build()
    );

    public Crosshair() {
        super(Categories.Render, "crosshair", "Draws a plain sized crosshair in place of the vanilla one.");
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        // Third person aims from the camera rather than from you, so a crosshair there would be a lie.
        if (!mc.options.getCameraType().isFirstPerson() || mc.gui.screen() != null) return;

        double centerX = event.screenWidth / 2.0;
        double centerY = event.screenHeight / 2.0;

        Color drawn = highlightTargets.get() && aimingAtEntity() ? targetColor.get() : color.get();

        double half = thickness.get() / 2.0;
        int arm = length.get();
        int inner = gap.get();

        Renderer2D.COLOR.begin();

        Renderer2D.COLOR.quad(centerX - half, centerY - inner - arm, thickness.get(), arm, drawn);
        Renderer2D.COLOR.quad(centerX - half, centerY + inner, thickness.get(), arm, drawn);
        Renderer2D.COLOR.quad(centerX - inner - arm, centerY - half, arm, thickness.get(), drawn);
        Renderer2D.COLOR.quad(centerX + inner, centerY - half, arm, thickness.get(), drawn);

        if (dot.get()) Renderer2D.COLOR.quad(centerX - half, centerY - half, thickness.get(), thickness.get(), drawn);

        Renderer2D.COLOR.render();
    }

    private boolean aimingAtEntity() {
        HitResult hit = mc.hitResult;

        return hit instanceof EntityHitResult;
    }
}
