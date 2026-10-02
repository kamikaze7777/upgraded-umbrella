/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Draws one of each pile of crammed-together mobs, with a count, instead of all of them.
 *
 * <p>Hundreds of entities stacked in the same block is a deliberate lag trap, and the cost is almost
 * entirely in drawing them - the client will happily spend a whole frame on a pile it renders as a
 * single silhouette anyway. Keeping one per pile and writing the number above it loses nothing you
 * could see and gives the frames back.
 *
 * <p>Piles are counted per block position and per entity type, so a stack of chickens and a stack of
 * cows in the same spot stay two piles rather than becoming one wrong number.
 *
 * <p>Ported from LiquidBounce (GPL-3.0), reimplemented against Meteor's API.
 */
public class CombineMobs extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> threshold = sgGeneral.add(new IntSetting.Builder()
        .name("threshold")
        .description("How many have to share a block before the pile is collapsed.")
        .defaultValue(3)
        .min(2)
        .sliderRange(2, 20)
        .build()
    );

    private final Setting<Boolean> armorStands = sgGeneral.add(new BoolSetting.Builder()
        .name("armor-stands")
        .description("Collapse piles of armour stands too.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> minecarts = sgGeneral.add(new BoolSetting.Builder()
        .name("minecarts")
        .description("Collapse piles of minecarts too.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> showCount = sgGeneral.add(new BoolSetting.Builder()
        .name("show-count")
        .description("Write how many are in each pile above it.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Count text size.")
        .defaultValue(1.5)
        .min(0.5)
        .sliderRange(0.5, 4)
        .visible(showCount::get)
        .build()
    );

    private final Setting<SettingColor> textColor = sgGeneral.add(new ColorSetting.Builder()
        .name("text-color")
        .defaultValue(new SettingColor(255, 200, 60, 255))
        .visible(showCount::get)
        .build()
    );

    /**
     * For each entity type, how many sit at each block position, and which entity was chosen to stand
     * for that pile. Rebuilt on the tick so the renderer only ever reads it.
     */
    private final Map<EntityType<?>, Long2IntMap> counts = new Object2ObjectOpenHashMap<>();
    private final Map<EntityType<?>, Long2IntMap> keepers = new Object2ObjectOpenHashMap<>();
    private final List<Pile> piles = new ArrayList<>();

    private static final Vector3d screen = new Vector3d();

    public CombineMobs() {
        super(Categories.Render, "combine-mobs", "Draws one of each crammed pile of mobs, with a count.");
    }

    @Override
    public void onDeactivate() {
        counts.clear();
        keepers.clear();
        piles.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        counts.clear();
        keepers.clear();
        piles.clear();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!canCombine(entity)) continue;

            long pos = entity.blockPosition().asLong();

            counts.computeIfAbsent(entity.getType(), _ -> new Long2IntOpenHashMap()).mergeInt(pos, 1, Integer::sum);

            // The first one seen at a position is the one kept, so the choice is stable between ticks
            // as long as the pile does not change.
            keepers.computeIfAbsent(entity.getType(), _ -> new Long2IntOpenHashMap()).putIfAbsent(pos, entity.getId());
        }

        if (!showCount.get()) return;

        for (Map.Entry<EntityType<?>, Long2IntMap> byType : counts.entrySet()) {
            for (Long2IntMap.Entry entry : byType.getValue().long2IntEntrySet()) {
                if (entry.getIntValue() < threshold.get()) continue;

                Entity keeper = mc.level.getEntity(keepers.get(byType.getKey()).get(entry.getLongKey()));
                if (keeper != null) piles.add(new Pile(keeper, entry.getIntValue()));
            }
        }
    }

    /** Whether this entity should be drawn, given what else is standing in the same block. */
    public boolean shouldRender(Entity entity) {
        if (!isActive() || !canCombine(entity)) return true;

        Long2IntMap byPosition = counts.get(entity.getType());
        if (byPosition == null) return true;

        long pos = entity.blockPosition().asLong();
        if (byPosition.get(pos) < threshold.get()) return true;

        Long2IntMap keeper = keepers.get(entity.getType());

        return keeper != null && keeper.get(pos) == entity.getId();
    }

    private boolean canCombine(Entity entity) {
        if (entity instanceof ArmorStand) return armorStands.get();
        if (entity instanceof AbstractMinecart) return minecarts.get();

        return entity instanceof Mob;
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!showCount.get()) return;

        TextRenderer text = TextRenderer.get();

        for (Pile pile : piles) {
            if (!pile.entity.isAlive()) continue;

            screen.set(pile.entity.getX(), pile.entity.getY() + pile.entity.getBbHeight() + 0.4, pile.entity.getZ());
            if (!NametagUtils.to2D(screen, scale.get())) continue;

            String label = "x" + pile.count;

            NametagUtils.begin(screen);
            text.beginBig(event.graphics);
            text.render(label, -text.getWidth(label) / 2, -text.getHeight(), textColor.get(), true);
            text.end();
            NametagUtils.end();
        }
    }

    @Override
    public String getInfoString() {
        return piles.isEmpty() ? null : piles.size() + " piles";
    }

    private record Pile(Entity entity, int count) {
    }
}
