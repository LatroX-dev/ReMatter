package dev.latrox.rematter.guide;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** One player-facing pattern for every device, cable, module and coloured key crystal. */
public final class ItemGuidance {
    public enum Role {
        RESEARCH, BREAKDOWN, SYNTHESIS, REACTION, PREPARATION, HEAT, LINK, STORAGE, POWER;
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
        add(items, "industrial_decomposer_controller", Role.BREAKDOWN, "industrial_decomposer");
        add(items, "material_synthesizer", Role.SYNTHESIS, "synthesizer");
        add(items, "industrial_synthesizer_controller", Role.SYNTHESIS, "industrial_synthesizer");
        add(items, "chemical_processor", Role.REACTION, "reactor");
        add(items, "chemical_reactor_controller", Role.REACTION, "industrial_reactor");
        add(items, "material_preparator", Role.PREPARATION, "preparator");
        add(items, "hand_preparator", Role.PREPARATION, "hand_preparator");
        add(items, "preparation_reactor_controller", Role.PREPARATION, "industrial_preparator");
        add(items, "sinter_furnace", Role.HEAT, "sinter_furnace");
        add(items, "matter_drive", Role.STORAGE, "drive");
        add(items, "cell_transfer_station", Role.STORAGE, "cell_transfer");
        add(items, "atmospheric_collector", Role.STORAGE, "atmospheric_collector");
        add(items, "matter_terminal", Role.STORAGE, "terminal");
        add(items, "network_interface", Role.LINK, "interface");
        add(items, "primal_converter", Role.REACTION, "converter");
        for(var tier:dev.latrox.rematter.energy.EnergyCubeTier.values())add(items,tier.id(),Role.POWER,"energy_cube");
        items.put("rematter:crystal_generator", new Spec(Role.HEAT, List.of(Hint.of("generator", 768, 2816, 128))));
        items.put("rematter:industrial_crystal_generator", new Spec(Role.HEAT, List.of(Hint.of("generator", 768, 2816, 512))));
        items.put("rematter:matter_cable", cable(128, 2048));
        items.put("rematter:reinforced_matter_cable", cable(1024, 16384));
        items.put("rematter:resonant_matter_cable", cable(8192, 131072));
        add(items, "matter_wrench", Role.LINK, "matter_wrench");
        add(items, "configuration_card", Role.LINK, "configuration_card");
        add(items, "wireless_access_point", Role.LINK, "wireless_access_point");
        add(items, "wireless_booster", Role.LINK, "wireless_booster");
        add(items, "wireless_matter_terminal", Role.STORAGE, "wireless_matter_terminal");
        add(items, "energy_core", Role.POWER, "energy_core");
        add(items, "portable_energy_cell", Role.POWER, "portable_energy_cell");
        add(items, "void_matter_cell", Role.STORAGE, "void_matter_cell");
        add(items, "pressurized_canister", Role.STORAGE, "pressurized_canister");
        add(items, "matter_level_emitter", Role.STORAGE, "matter_level_emitter");
        add(items, "entangled_link", Role.LINK, "entangled_link");
        add(items, "spectrometer_lens", Role.RESEARCH, "spectrometer_lens");
        add(items, "discovery_lens", Role.RESEARCH, "discovery_lens");
        add(items, "speed_upgrade", Role.POWER, "speed_upgrade");
        add(items, "efficiency_upgrade", Role.POWER, "efficiency_upgrade");
        add(items, "parallel_upgrade", Role.POWER, "parallel_upgrade");
        add(items, "pressure_module", Role.SYNTHESIS, "pressure_module");
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
