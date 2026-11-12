package dev.latrox.rematter.chemistry;

/** Resources owned by a separate operation system, never by ordinary chemistry. */
public final class ReservedSubstances {
    public static final SubstanceId PRIMAL_MATTER = SubstanceId.parse("rematter:primal_matter");
    private static final java.util.Set<String> PRIMAL_ITEMS = java.util.Set.of("rematter:primal_shard", "rematter:deepslate_primal_ore", "rematter:nether_primal_ore",
            "rematter:end_primal_ore", "rematter:primal_crystal", "rematter:discovery_lens", "rematter:entangled_link",
            "rematter:primal_cell_16k", "rematter:primal_cell_64k", "rematter:primal_cell_256k");
    private ReservedSubstances() {}
    public static boolean reserved(SubstanceId id) { return PRIMAL_MATTER.equals(id); }
    public static boolean reserved(SubstanceKey key) { return reserved(key.id()); }
    public static SubstanceDefinition primalDefinition() {
        return new SubstanceDefinition(PRIMAL_MATTER, "substance.rematter.primal_matter", "Ur", SubstanceKind.ELEMENT,
                new Composition(java.util.Map.of(PRIMAL_MATTER, 1L)), java.util.Set.of(Phase.SOLID));
    }
    public static boolean primalItem(String id) {
        return PRIMAL_ITEMS.contains(id);
    }
    public static java.util.Set<String> primalItems() { return PRIMAL_ITEMS; }
}
