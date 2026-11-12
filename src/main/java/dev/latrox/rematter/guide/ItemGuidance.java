package dev.latrox.rematter.guide;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** One player-facing pattern for every device, cable, module and coloured key crystal. */
public final class ItemGuidance {
    public enum Role {
        RESEARCH, BREAKDOWN, SYNTHESIS, REACTION, PREPARATION, HEAT, LINK, STORAGE;
        public String key() { return "tooltip.rematter.role." + name().toLowerCase(Locale.ROOT); }
    }
    public record Hint(String key, List<Object> arguments) {
        public Hint { arguments = List.copyOf(arguments); }
        static Hint of(String key, Object... arguments) { return new Hint("tooltip.rematter.guidance." + key, List.of(arguments)); }
    }
    public record Spec(Role role, List<Hint> hints) { public Spec { hints = List.copyOf(hints); } }
    private static final Map<String, Spec> ITEMS = create();
    private ItemGuidance() {}
    public static Optional<Spec> forItem(String itemId) { return Optional.ofNullable(ITEMS.get(itemId)); }
    public static java.util.Set<String> items() { return ITEMS.keySet(); }
    private static Map<String, Spec> create() {
        var items = new HashMap<String, Spec>();
        add(items, "analyzer", Role.RESEARCH, "analyzer");
        add(items, "decomposer", Role.BREAKDOWN, "decomposer");
        add(items, "material_synthesizer", Role.SYNTHESIS, "synthesizer");
        add(items, "chemical_reactor", Role.REACTION, "reactor");
        add(items, "industrial_reactor", Role.REACTION, "industrial_reactor");
        add(items, "material_preparator", Role.PREPARATION, "preparator");
        add(items, "hand_preparator", Role.PREPARATION, "hand_preparator");
        add(items, "industrial_preparator", Role.PREPARATION, "industrial_preparator");
        add(items, "sinter_furnace", Role.HEAT, "sinter_furnace");
        add(items, "matter_drive", Role.STORAGE, "drive");
        add(items, "matter_terminal", Role.STORAGE, "terminal");
        add(items, "network_interface", Role.LINK, "interface");
        add(items, "primal_converter", Role.REACTION, "converter");
        items.put("rematter:crystal_generator", new Spec(Role.HEAT, List.of(Hint.of("generator", 1024, 64))));
        items.put("rematter:industrial_crystal_generator", new Spec(Role.HEAT, List.of(Hint.of("generator", 1024, 256))));
        items.put("rematter:matter_cable", cable(128, 2048));
        items.put("rematter:reinforced_matter_cable", cable(1024, 16384));
        items.put("rematter:resonant_matter_cable", cable(8192, 131072));
        add(items, "grinding_unit", Role.PREPARATION, "grinding");
        add(items, "mixing_basin", Role.PREPARATION, "mixing");
        add(items, "separation_unit", Role.PREPARATION, "precision");
        items.put("rematter:field_coil", new Spec(Role.REACTION, List.of(Hint.of("field", 4, 8))));
        items.put("rematter:reaction_chamber", new Spec(Role.REACTION, List.of(Hint.of("room", 16000))));
        Map.of("ice", Role.RESEARCH, "copper", Role.BREAKDOWN, "leaf", Role.SYNTHESIS, "violet", Role.REACTION,
                "amber", Role.PREPARATION, "ember", Role.HEAT, "blue", Role.LINK, "mint", Role.STORAGE)
                .forEach((colour, role) -> add(items, colour + "_matrix_crystal", role, "key_crystal"));
        return Map.copyOf(items);
    }
    private static Spec cable(int matter, int energy) { return new Spec(Role.LINK, List.of(Hint.of("cable_rates", matter, energy), Hint.of("shared_budget"))); }
    private static void add(Map<String, Spec> items, String item, Role role, String key) { items.put("rematter:" + item, new Spec(role, List.of(Hint.of(key)))); }
}
