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
        add(items, "decomposer_reactor_controller", Role.BREAKDOWN, "industrial_decomposer");
        add(items, "material_synthesizer", Role.SYNTHESIS, "synthesizer");
        add(items, "synthesizer_reactor_controller", Role.SYNTHESIS, "industrial_synthesizer");
        add(items, "chemical_processor", Role.REACTION, "reactor");
        add(items, "chemical_reactor_controller", Role.REACTION, "industrial_reactor");
        add(items, "transmutation_reactor_controller", Role.REACTION, "transmutation_reactor");
        add(items, "primal_reactor_controller", Role.REACTION, "primal_reactor");
        add(items, "energy_matrix_controller", Role.POWER, "energy_matrix");
        add(items, "energy_reactor_controller", Role.POWER, "energy_reactor");
        add(items, "material_preparator", Role.PREPARATION, "preparator");
        add(items, "hand_preparator", Role.PREPARATION, "hand_preparator");
        add(items, "preparation_reactor_controller", Role.PREPARATION, "industrial_preparator");
        add(items, "sinter_furnace", Role.HEAT, "sinter_furnace");
        add(items, "matter_drive", Role.STORAGE, "drive");
        add(items, "cell_transfer_station", Role.STORAGE, "cell_transfer");
        add(items, "atmospheric_collector", Role.STORAGE, "atmospheric_collector");
        add(items, "deep_extractor", Role.STORAGE, "deep_extractor");
        add(items, "matter_terminal", Role.STORAGE, "terminal");
        add(items, "network_interface", Role.LINK, "interface");
        add(items, "primal_converter", Role.REACTION, "converter");
        for(var tier:dev.latrox.rematter.energy.EnergyCubeTier.values())add(items,tier.id(),Role.POWER,"energy_cube");
        items.put("rematter:crystal_generator", new Spec(Role.HEAT, List.of(Hint.of("generator", 768, 2816, 128))));
        items.put("rematter:industrial_crystal_generator", new Spec(Role.HEAT, List.of(Hint.of("generator", 768, 2816, 512))));
        items.put("rematter:matter_cable", cable(128, 4096));
        items.put("rematter:reinforced_matter_cable", cable(1024, 65536));
        items.put("rematter:resonant_matter_cable", cable(8192, 524288));
        add(items, "matter_wrench", Role.LINK, "matter_wrench");
        add(items, "configuration_card", Role.LINK, "configuration_card");
        add(items, "security_card", Role.LINK, "security_card");
        add(items, "wireless_access_point", Role.LINK, "wireless_access_point");
        add(items, "wireless_booster", Role.LINK, "wireless_booster");
        add(items, "wireless_matter_terminal", Role.STORAGE, "wireless_matter_terminal");
        add(items, "energy_core", Role.POWER, "energy_core");
        add(items, "portable_energy_cell", Role.POWER, "portable_energy_cell");
        add(items, "void_matter_cell", Role.STORAGE, "void_matter_cell");
        add(items, "pressurized_canister", Role.STORAGE, "pressurized_canister");
        add(items, "matter_level_emitter", Role.STORAGE, "matter_level_emitter");
        add(items, "matter_cable_relay", Role.LINK, "matter_cable_relay");
        add(items, "entangled_link", Role.LINK, "entangled_link");
        add(items, "dimensional_entangled_link", Role.LINK, "dimensional_entangled_link");
        add(items, "dimensional_card", Role.LINK, "dimensional_card");
        add(items, "anchor_upgrade", Role.LINK, "anchor_upgrade");
        add(items, "spatial_anchor", Role.LINK, "spatial_anchor");
        add(items, "spectrometer_lens", Role.RESEARCH, "spectrometer_lens");
        add(items, "discovery_lens", Role.RESEARCH, "discovery_lens");
        add(items, "speed_upgrade", Role.POWER, "speed_upgrade");
        add(items, "efficiency_upgrade", Role.POWER, "efficiency_upgrade");
        add(items, "parallel_upgrade", Role.POWER, "parallel_upgrade");
        add(items, "pressure_module", Role.SYNTHESIS, "pressure_module");
        add(items, "extraction_focus_upgrade", Role.STORAGE, "extraction_focus_upgrade");
        add(items, "mass_compression_upgrade", Role.POWER, "mass_compression_upgrade");
        add(items, "bore_core", Role.STORAGE, "bore_core");
        add(items, "particle_resonator", Role.REACTION, "particle_resonator");
        add(items, "primal_matrix_core", Role.REACTION, "primal_matrix_core");
        add(items, "grinding_unit", Role.PREPARATION, "grinding");
        add(items, "mixing_basin", Role.PREPARATION, "mixing");
        add(items, "separation_unit", Role.PREPARATION, "precision");
        items.put("rematter:field_coil", new Spec(Role.REACTION, List.of(Hint.of("field", 4, 8))));
        items.put("rematter:reaction_chamber", new Spec(Role.REACTION, List.of(Hint.of("room", 16000))));
        add(items, "particle_emitter", Role.REACTION, "particle_emitter");
        add(items, "primal_condenser_core", Role.REACTION, "primal_condenser_core");
        add(items, "reactor_fuel_core", Role.POWER, "reactor_fuel_core");
        add(items, "reactor_induction_coil", Role.POWER, "reactor_induction_coil");
        add(items, "energy_matrix_casing", Role.POWER, "energy_matrix_shell");
        add(items, "energy_matrix_glass", Role.POWER, "energy_matrix_shell");
        add(items, "energy_matrix_port", Role.POWER, "energy_matrix_shell");
        for (var tier : dev.latrox.rematter.energy.EnergyMatrixCellTier.values()) {
            items.put("rematter:" + tier.id(), new Spec(Role.POWER, List.of(Hint.of("energy_matrix_cell", String.format(Locale.US, "%,d", tier.capacity())))));
        }
        for (var tier : dev.latrox.rematter.energy.EnergyMatrixProviderTier.values()) {
            items.put("rematter:" + tier.id(), new Spec(Role.POWER, List.of(Hint.of("energy_matrix_provider", String.format(Locale.US, "%,d", tier.transferRate())))));
        }
        add(items, "energy_reactor_casing", Role.POWER, "energy_reactor_shell");
        add(items, "energy_reactor_glass", Role.POWER, "energy_reactor_shell");
        add(items, "energy_reactor_port", Role.POWER, "energy_reactor_shell");
        add(items, "transmutation_reactor_casing", Role.REACTION, "transmutation_shell");
        add(items, "transmutation_reactor_glass", Role.REACTION, "transmutation_shell");
        add(items, "transmutation_machine_port", Role.REACTION, "transmutation_shell");
        add(items, "primal_reactor_casing", Role.REACTION, "primal_shell");
        add(items, "primal_reactor_glass", Role.REACTION, "primal_shell");
        add(items, "primal_machine_port", Role.REACTION, "primal_shell");
        Map.of("ice", Role.RESEARCH, "copper", Role.BREAKDOWN, "leaf", Role.SYNTHESIS, "violet", Role.REACTION,
                "amber", Role.PREPARATION, "ember", Role.HEAT, "blue", Role.LINK, "mint", Role.STORAGE)
                .forEach((colour, role) -> add(items, colour + "_matrix_crystal", role, "key_crystal"));
        return Map.copyOf(items);
    }
    private static Spec cable(int matter, int energy) { return new Spec(Role.LINK, List.of(Hint.of("cable_rates", matter, energy), Hint.of("shared_budget"))); }
    private static void add(Map<String, Spec> items, String item, Role role, String key) { items.put("rematter:" + item, new Spec(role, List.of(Hint.of(key)))); }
}
