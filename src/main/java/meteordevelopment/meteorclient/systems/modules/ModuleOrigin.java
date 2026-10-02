/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules;

/**
 * Where a module came from.
 *
 * <p>This fork carries two module sets in one client: Meteor's own, and everything ported over
 * from LiquidBounce (GPL-3.0). They are mixed together in the same categories and a fair number
 * of them share a name, so without a marker there is no way to tell at a glance - or in the
 * ClickGUI - which half of the client you are looking at.
 *
 * <p>The set is resolved from {@link ModuleOrigins}, which is generated off the attribution
 * javadoc on each ported module by {@code notes/scan_module_origins.py}.
 */
public enum ModuleOrigin {
    Meteor("Meteor", ""),
    LiquidBounce("LiquidBounce", "LB");

    /** Full name, for the module screen and chat. */
    public final String title;

    /** Short marker appended to module names in the ClickGUI. Empty for Meteor's own. */
    public final String tag;

    ModuleOrigin(String title, String tag) {
        this.title = title;
        this.tag = tag;
    }

    @Override
    public String toString() {
        return title;
    }

    /**
     * Which origins the ClickGUI and the module commands should show.
     *
     * <p>Constants avoid the bare names {@code Meteor} / {@code LiquidBounce} so a filter is never
     * mistaken for an origin, and contain no spaces so they round-trip through the {@code .setting}
     * command.
     */
    public enum Filter {
        All,
        MeteorOnly,
        LiquidBounceOnly;

        public boolean test(ModuleOrigin origin) {
            return switch (this) {
                case All -> true;
                case MeteorOnly -> origin == ModuleOrigin.Meteor;
                case LiquidBounceOnly -> origin == ModuleOrigin.LiquidBounce;
            };
        }

        public boolean test(Module module) {
            return test(module.origin);
        }
    }
}
