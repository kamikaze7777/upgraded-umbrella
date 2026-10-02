/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.meteor.ActiveModulesChangedEvent;
import meteordevelopment.meteorclient.events.meteor.KeyInputEvent;
import meteordevelopment.meteorclient.events.meteor.ModuleBindChangedEvent;
import meteordevelopment.meteorclient.events.meteor.MouseClickEvent;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.System;
import meteordevelopment.meteorclient.systems.Systems;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.modules.combat.*;
import meteordevelopment.meteorclient.systems.modules.misc.*;
import meteordevelopment.meteorclient.systems.modules.misc.swarm.Swarm;
import meteordevelopment.meteorclient.systems.modules.movement.*;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFly;
import meteordevelopment.meteorclient.systems.modules.movement.speed.Speed;
import meteordevelopment.meteorclient.systems.modules.player.*;
import meteordevelopment.meteorclient.systems.modules.render.*;
import meteordevelopment.meteorclient.systems.modules.render.blockesp.BlockESP;
import meteordevelopment.meteorclient.systems.modules.render.marker.Marker;
import meteordevelopment.meteorclient.systems.modules.world.*;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.misc.ValueComparableMap;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jspecify.annotations.Nullable;
import com.mojang.blaze3d.platform.InputConstants;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class Modules extends System<Modules> {
    private static final List<Category> CATEGORIES = new ArrayList<>();

    private final Map<Class<? extends Module>, Module> moduleInstances = new Reference2ReferenceOpenHashMap<>();
    private final Map<Category, List<Module>> groups = new Reference2ReferenceOpenHashMap<>();

    private final Set<Module> active = new ReferenceOpenHashSet<>();
    private @Nullable Module moduleToBind;
    private boolean awaitingKeyRelease = false;

    public Modules() {
        super("modules");
    }

    public static Modules get() {
        return Systems.get(Modules.class);
    }

    @Override
    public void init() {
        initCombat();
        initPlayer();
        initMovement();
        initRender();
        initWorld();
        initMisc();
    }

    @Override
    public void load(File folder) {
        for (Module module : getAll()) {
            for (SettingGroup group : module.settings) {
                for (Setting<?> setting : group) setting.reset();
            }
        }

        super.load(folder);
    }

    public void sortModules() {
        for (List<Module> modules : groups.values()) {
            modules.sort(Comparator.comparing(o -> o.title));
        }
    }

    public static void registerCategory(Category category) {
        if (!Categories.REGISTERING)
            throw new RuntimeException("Modules.registerCategory - Cannot register category outside of onRegisterCategories callback.");

        CATEGORIES.add(category);
    }

    public static Iterable<Category> loopCategories() {
        return CATEGORIES;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public <T extends Module> T get(Class<T> klass) {
        return (T) moduleInstances.get(klass);
    }

    @SuppressWarnings("unused")
    public <T extends Module> Optional<T> getOptional(Class<T> klass) {
        return Optional.ofNullable(get(klass));
    }

    @Nullable
    public Module get(String name) {
        for (Module module : moduleInstances.values()) {
            if (module.name.equalsIgnoreCase(name)) return module;
        }

        return null;
    }

    public boolean isActive(Class<? extends Module> klass) {
        Module module = get(klass);
        return module != null && module.isActive();
    }

    public List<Module> getGroup(Category category) {
        return groups.computeIfAbsent(category, _ -> new ArrayList<>());
    }

    /** A category's modules, narrowed to one origin. Returns a copy - the caller must not mutate the group. */
    public List<Module> getGroup(Category category, ModuleOrigin origin) {
        List<Module> modules = new ArrayList<>();
        for (Module module : getGroup(category)) {
            if (module.origin == origin) modules.add(module);
        }
        return modules;
    }

    public Collection<Module> getAll() {
        return moduleInstances.values();
    }

    /** Every module from one origin, sorted by title, for the origin listings in chat. */
    public List<Module> getAll(ModuleOrigin origin) {
        List<Module> modules = new ArrayList<>();
        for (Module module : moduleInstances.values()) {
            if (module.origin == origin) modules.add(module);
        }
        modules.sort(Comparator.comparing(module -> module.title));
        return modules;
    }

    public int getCount() {
        return moduleInstances.size();
    }

    public int getCount(ModuleOrigin origin) {
        int count = 0;
        for (Module module : moduleInstances.values()) {
            if (module.origin == origin) count++;
        }
        return count;
    }

    public Collection<Module> getActive() {
        return active;
    }

    public List<Pair<Module, String>> searchTitles(String text) {
        Object2IntMap<Pair<Module, String>> modules = new Object2IntOpenHashMap<>();

        for (Module module : this.moduleInstances.values()) {
            String title = module.title;
            int score = Utils.searchLevenshteinDefault(title, text, false);

            if (Config.get().moduleAliases.get()) {
                for (String alias : module.aliases) {
                    int aliasScore = Utils.searchLevenshteinDefault(alias, text, false);
                    if (aliasScore < score) {
                        title = module.title + " (" + alias + ")";
                        score = aliasScore;
                    }
                }
            }

            modules.put(Pair.of(module, title), score);
        }

        List<Pair<Module, String>> l = new ArrayList<>(modules.keySet());
        l.sort(Comparator.comparingInt(modules::getInt));

        return l;
    }

    public Set<Module> searchSettingTitles(String text) {
        Map<Module, Integer> modules = new ValueComparableMap<>(Comparator.naturalOrder());

        for (Module module : this.moduleInstances.values()) {
            int lowest = Integer.MAX_VALUE;
            for (SettingGroup sg : module.settings) {
                for (Setting<?> setting : sg) {
                    int score = Utils.searchLevenshteinDefault(setting.title, text, false);
                    if (score < lowest) lowest = score;
                }
            }
            modules.put(module, modules.getOrDefault(module, 0) + lowest);
        }

        return modules.keySet();
    }

    void addActive(Module module) {
        synchronized (active) {
            if (active.add(module)) {
                MeteorClient.EVENT_BUS.post(ActiveModulesChangedEvent.get());
            }
        }
    }

    void removeActive(Module module) {
        synchronized (active) {
            if (active.remove(module)) {
                MeteorClient.EVENT_BUS.post(ActiveModulesChangedEvent.get());
            }
        }
    }

    // Binding

    public void setModuleToBind(@Nullable Module moduleToBind) {
        this.moduleToBind = moduleToBind;
    }

    /***
     * @see meteordevelopment.meteorclient.commands.commands.BindCommand
     * For ensuring we don't instantly bind the module to the enter key.
     */
    public void awaitKeyRelease() {
        this.awaitingKeyRelease = true;
    }

    public boolean isBinding() {
        return moduleToBind != null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onKeyBinding(KeyInputEvent event) {
        if (event.action == KeyAction.Release && onBinding(true, event.key(), event.modifiers())) event.cancel();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onButtonBinding(MouseClickEvent event) {
        if (event.action == KeyAction.Release && onBinding(false, event.button(), 0)) event.cancel();
    }

    private boolean onBinding(boolean isKey, int value, int modifiers) {
        if (!isBinding()) return false;

        if (awaitingKeyRelease) {
            if (!isKey || (value != InputConstants.KEY_RETURN && value != InputConstants.KEY_NUMPADENTER)) return false;

            awaitingKeyRelease = false;
            return false;
        }

        if (moduleToBind.keybind.canBindTo(isKey, value, modifiers)) {
            moduleToBind.keybind.set(isKey, value, modifiers);
            moduleToBind.info("Bound to (highlight)%s(default).", moduleToBind.keybind);
        } else if (value == InputConstants.KEY_ESCAPE) {
            moduleToBind.keybind.set(Keybind.none());
            moduleToBind.info("Removed bind.");
        } else return false;

        MeteorClient.EVENT_BUS.post(ModuleBindChangedEvent.get(moduleToBind));
        moduleToBind = null;

        return true;
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onKey(KeyInputEvent event) {
        if (event.action == KeyAction.Repeat) return;
        onAction(true, event.key(), event.modifiers(), event.action == KeyAction.Press);
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onMouseClick(MouseClickEvent event) {
        if (event.action == KeyAction.Repeat) return;
        onAction(false, event.button(), 0, event.action == KeyAction.Press);
    }

    private void onAction(boolean isKey, int value, int modifiers, boolean isPress) {
        if (mc.gui.screen() != null || Input.isKeyPressed(InputConstants.KEY_F3)) return;

        for (Module module : moduleInstances.values()) {
            if (module.keybind.matches(isKey, value, modifiers) && (isPress || (module.toggleOnBindRelease && module.isActive()))) {
                module.toggle();
                module.sendToggledMsg();
            }
        }
    }

    // End of binding

    @EventHandler(priority = EventPriority.HIGHEST + 1)
    private void onOpenScreen(OpenScreenEvent event) {
        if (!Utils.canUpdate()) return;

        for (Module module : moduleInstances.values()) {
            if (module.toggleOnBindRelease && module.isActive()) {
                module.toggle();
                module.sendToggledMsg();
            }
        }
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        synchronized (active) {
            for (Module module : getAll()) {
                if (module.isActive() && !module.runInMainMenu) {
                    MeteorClient.EVENT_BUS.subscribe(module);
                    module.onActivate();
                }
            }
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        synchronized (active) {
            for (Module module : getAll()) {
                if (module.isActive() && !module.runInMainMenu) {
                    MeteorClient.EVENT_BUS.unsubscribe(module);
                    module.onDeactivate();
                }
            }
        }
    }

    public void disableAll() {
        synchronized (active) {
            for (Module module : getAll()) {
                module.disable();
            }
        }
    }

    /**
     * Turn off every active module from one origin, and report how many that was.
     *
     * <p>The point of this is bisecting: with both module sets loaded, "is this the port or is it
     * Meteor's" is the first question a misbehaving client raises, and this answers it in one key.
     */
    public int disableAll(ModuleOrigin origin) {
        int disabled = 0;

        synchronized (active) {
            for (Module module : List.copyOf(active)) {
                if (module.origin == origin) {
                    module.disable();
                    disabled++;
                }
            }
        }

        return disabled;
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();

        ListTag modulesTag = new ListTag();
        for (Module module : getAll()) {
            CompoundTag moduleTag = module.toTag();
            if (moduleTag != null) modulesTag.add(moduleTag);
        }
        tag.put("modules", modulesTag);

        return tag;
    }

    @Override
    public Modules fromTag(CompoundTag tag) {
        disableAll();

        ListTag modulesTag = tag.getListOrEmpty("modules");
        for (Tag moduleTagI : modulesTag) {
            CompoundTag moduleTag = (CompoundTag) moduleTagI;
            Module module = get(moduleTag.getStringOr("name", ""));
            if (module != null) module.fromTag(moduleTag);
        }

        return this;
    }

    // INIT MODULES

    public void add(Module module) {
        // Check if the module's category is registered
        if (!CATEGORIES.contains(module.category)) {
            throw new RuntimeException("Modules.addModule - Module's category was not registered.");
        }

        // Remove the previous module with the same name
        AtomicReference<Module> removedModule = new AtomicReference<>();
        if (moduleInstances.values().removeIf(module1 -> {
            if (module1.name.equals(module.name)) {
                removedModule.set(module1);
                module1.settings.unregisterColorSettings();

                return true;
            }

            return false;
        })) {
            getGroup(removedModule.get().category).remove(removedModule.get());
        }

        // Add the module
        moduleInstances.put(module.getClass(), module);
        getGroup(module.category).add(module);

        // Register color settings for the module
        module.settings.registerColorSettings(module);
    }

    private void initCombat() {
        add(new Aimbot());
        add(new AnchorAura());
        add(new AntiAnvil());
        add(new AntiBed());
        add(new ArrowDodge());
        add(new AttributeSwap());
        add(new AutoAnvil());
        add(new AutoArmor());
        add(new AutoArmorl());
        add(new AutoCity());
        add(new AutoEXP());
        add(new AutoLog());
        add(new AutoPearl());
        add(new AutoRod());
        add(new AutoTotem());
        add(new AutoTrap());
        add(new AutoTrapl());
        add(new AutoWeapon());
        add(new AutoWeaponl());
        add(new AutoWeb());
        add(new BedAura());
        add(new Backtrack());
        add(new BowAimbot());
        add(new BowSpam());
        add(new Burrow());
        add(new Criticals());
        add(new Criticalsl());
        add(new CrystalAura());
        add(new CrystalAural());
        add(new FakeLag());
        add(new Hitboxes());
        add(new ElytraTarget());
        add(new HoleFiller());
        add(new HoleFillerl());
        add(new KeepSprint());
        add(new KillAura());
        add(new KillAural());
        add(new MaceKill());
        add(new NoMissCooldown());
        add(new Offhand());
        add(new Offhandl());
        add(new ProjectileAimbot());
        add(new Quiver());
        add(new SelfAnvil());
        add(new SelfTrap());
        add(new SelfWeb());
        add(new SuperKnockback());
        add(new SpearKill());
        add(new Surround());
        add(new Surroundl());
        add(new TargetLock());
        add(new TickBase());
        add(new TimerRange());
        add(new TpAura());
        add(new VehicleOneHit());
    }

    private void initPlayer() {
        add(new AirPlace());
        add(new AirPlacel());
        add(new AntiAFK());
        add(new AntiAFKl());
        add(new AntiExploit());
        add(new AntiHunger());
        add(new AutoEat());
        add(new AutoBuff());
        add(new AutoCrafter());
        add(new AutoClicker());
        add(new AutoClickerl());
        add(new AutoFish());
        add(new AutoFishl());
        add(new AutoGap());
        add(new AutoMend());
        add(new AutoReplenish());
        add(new AutoQueue());
        add(new AutoRespawn());
        add(new AutoTool());
        add(new AutoTooll());
        add(new BreakDelay());
        add(new ChestCleaner());
        add(new ChestStealer());
        add(new ChestSwap());
        add(new Eagle());
        add(new Damage());
        add(new EXPThrower());
        add(new FakePlayer());
        add(new FastPlace());
        add(new FastUse());
        add(new FastUsel());
        add(new GhostHand());
        add(new InstantRebreak());
        add(new LiquidInteract());
        add(new MiddleClickExtra());
        add(new MultiActions());
        add(new Multitask());
        add(new NameProtect());
        add(new NameProtectl());
        add(new NoInteract());
        add(new MoreCarry());
        add(new AutoBreak());
        add(new AutoWindCharge());
        add(new ResetVL());
        add(new SleepWalker());
        add(new NoPitchLimit());
        add(new NoSlotSet());
        add(new NoMiningTrace());
        add(new NoRotate());
        add(new NoStatusEffects());
        add(new Portals());
        add(new PotionSaver());
        add(new PotionSpoof());
        add(new Reach());
        add(new TimeShift());
        add(new Rotation());
        add(new SpeedMine());
    }

    private void initMovement() {
        add(new AirJump());
        add(new AirJumpl());
        add(new Anchor());
        add(new Anchorl());
        add(new AntiBounce());
        add(new AntiVoid());
        add(new AntiVoidl());
        add(new AutoJump());
        add(new AutoWalk());
        add(new AvoidHazards());
        add(new AutoWasp());
        add(new Blink());
        add(new Blinkl());
        add(new ClickTP());
        add(new Clip());
        add(new ElytraBoost());
        add(new ElytraRecast());
        add(new ExtendedFirework());
        add(new ElytraFly());
        add(new ElytraFlyl());
        add(new EntityControl());
        add(new EntityControll());
        add(new FastClimb());
        add(new Freeze());
        add(new Flight());
        add(new GUIMove());
        add(new HighJump());
        add(new HighJumpl());
        add(new Jesus());
        add(new LongJump());
        add(new NoFall());
        add(new BlockBounce());
        add(new NoClip());
        add(new NoJumpDelay());
        add(new NoPose());
        add(new NoSlow());
        add(new Parkour());
        add(new Parkourl());
        add(new ReverseStep());
        add(new ReverseStepl());
        add(new SafeWalk());
        add(new SafeWalkl());
        add(new Scaffold());
        add(new Scaffoldl());
        add(new SnapTap());
        add(new Slippy());
        add(new Sneak());
        add(new Speed());
        add(new Speedl());
        add(new Spider());
        add(new Spiderl());
        add(new Strafel());
        add(new Sprint());
        add(new Sprintl());
        add(new TargetStrafe());
        add(new VehicleBoost());
        add(new Step());
        add(new Stepl());
        add(new TridentBoost());
        add(new Velocity());
        add(new Velocityl());
    }

    private void initRender() {
        add(new Aspect());
        add(new AutoF5());
        add(new BetterTab());
        add(new BetterTabl());
        add(new BedPlates());
        add(new BetterTooltips());
        add(new BlockESP());
        add(new BlockESPl());
        add(new BlockSelection());
        add(new Blur());
        add(new BossStack());
        add(new Breadcrumbs());
        add(new Breadcrumbsl());
        add(new BreakIndicators());
        add(new CameraTweaks());
        add(new Chams());
        add(new Chamsl());
        add(new CityESP());
        add(new EntityOwner());
        add(new CombineMobs());
        add(new Crosshair());
        add(new CrystalView());
        add(new DamageParticles());
        add(new ESP());
        add(new ESPl());
        add(new Freecam());
        add(new FreeCaml());
        add(new FreeLook());
        add(new Fullbright());
        add(new HandView());
        add(new HitFX());
        add(new HoleESP());
        add(new HoleESPl());
        add(new ItemPhysics());
        add(new JumpEffect());
        add(new ItemHighlight());
        add(new LightOverlay());
        add(new LogoutSpots());
        add(new Marker());
        add(new Nametags());
        add(new Nametagsl());
        add(new NewChunks());
        add(new NoBob());
        add(new NoHurtCam());
        add(new NoFov());
        add(new NoRender());
        add(new NoSwing());
        add(new MurderMystery());
        add(new PopChams());
        add(new ProphuntESP());
        add(new Radar());
        add(new Rotations());
        add(new QuickPerspectiveSwap());
        add(new SmoothCamera());
        add(new StorageESP());
        add(new StorageESPl());
        add(new TimeChanger());
        add(new TNTTimer());
        add(new TotemEffect());
        add(new Tracers());
        add(new Tracersl());
        add(new Trail());
        add(new Trajectories());
        add(new Trajectoriesl());
        add(new TunnelESP());
        add(new VoidESP());
        add(new VoidESPl());
        add(new WallHack());
        add(new WaypointsModule());
        add(new WeatherChanger());
        add(new Xray());
        add(new XRayl());
        add(new Zoom());
    }

    private void initWorld() {
        add(new AbortBreaking());
        add(new Ambience());
        add(new AutoBreed());
        add(new AutoBuild());
        add(new AutoFarm());
        add(new AutoDisable());
        add(new BedDefender());
        add(new AutoBrewer());
        add(new AutoMount());
        add(new AutoNametag());
        add(new AutoShearer());
        add(new AutoSign());
        add(new AutoSmelter());
        add(new BuildHeight());
        add(new Collisions());
        add(new EChestFarmer());
        add(new EndermanLook());
        add(new Extinguish());
        add(new Flamethrower());
        add(new HighwayBuilder());
        add(new LiquidFiller());
        add(new LiquidFillerl());
        add(new NoGhostBlocks());
        add(new NoInterpolation());
        add(new NoSlowBreak());
        add(new Nuker());
        add(new PacketMine());
        add(new PacketMinel());
        add(new StashFinder());
        add(new ProjectilePuncher());
        add(new SpawnProofer());
        add(new StrongholdFinder());
        add(new Timer());
        add(new Timerl());
        add(new VeinMiner());

        if (BaritoneUtils.IS_AVAILABLE) {
            add(new Excavator());
            add(new InfinityMiner());
        }
    }

    private void initMisc() {
        add(new AntiBot());
        add(new AntiPacketKick());
        add(new GUICloser());
        add(new AutoReconnect());
        add(new BetterBeacons());
        add(new AntiReducedDebugInfo());
        add(new BetterChat());
        add(new BetterChatl());
        add(new BetterTitle());
        add(new BookBot());
        add(new BookBotl());
        add(new Kick());
        add(new NameCollector());
        add(new DankBobbing());
        add(new Derp());
        add(new DiscordPresence());
        add(new HandDerp());
        add(new FlagCheck());
        add(new InventoryTracker());
        add(new InventoryTweaks());
        add(new ItemScroller());
        add(new MessageAura());
        add(new Notebot());
        add(new Notifier());
        add(new Notifierl());
        add(new PingSpoof());
        add(new PacketCanceller());
        add(new Plugins());
        add(new PacketLogger());
        add(new PacketLoggerl());
        add(new ServerSpoof());
        add(new SkinDerp());
        add(new SoundBlocker());
        add(new Teams());
        add(new TextFieldProtect());
        add(new Twerk());
        add(new Vomit());
        add(new Spam());
        add(new Swarm());
    }
}
