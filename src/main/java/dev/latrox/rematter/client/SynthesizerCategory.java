package dev.latrox.rematter.client;

import dev.latrox.rematter.chemistry.SubstanceKey;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Client-side presentation categories, responsive picker grid layout, and network-stock craftability checks for
 * the Material Synthesizer recipe picker. Never grants permissions or alters server-side recipes.
 */
public enum SynthesizerCategory {
    ALL("cat_all", "cat_all_hint", UiIcon.FAMILIES),
    METALS("cat_metals", "cat_metals_hint", UiIcon.SOLID),
    CRYSTALS("cat_crystals", "cat_crystals_hint", UiIcon.STAR),
    STONE("cat_stone", "cat_stone_hint", UiIcon.STRUCTURE),
    ORGANIC("cat_organic", "cat_organic_hint", UiIcon.SLOT_SAMPLE),
    FLUIDS("cat_fluids", "cat_fluids_hint", UiIcon.LIQUID);

    public enum StockState { SYNCING, NO_STORAGE, PARTIAL, COMPLETE }

    public record CategoryLayout(int buttonWidth, int buttonHeight, int stepX, int stepY, int perRow, int rows) {
        public int xOffset(int index) { return (index % perRow) * stepX; }
        public int yOffset(int index) { return (index / perRow) * stepY; }
        public int totalWidth() { return perRow == 0 ? 0 : (perRow - 1) * stepX + buttonWidth; }
        public int totalHeight() { return rows == 0 ? 0 : rows * stepY; }
    }

    private static final Set<String> ORGANIC_SUBSTANCES = Set.of(
            "rematter:cellulose", "rematter:starch", "rematter:sucrose", "rematter:protein",
            "rematter:beeswax", "rematter:calcium_phosphate", "rematter:seashell");
    private static final Set<String> METAL_SUBSTANCES = Set.of(
            "rematter:iron", "rematter:copper", "rematter:gold", "rematter:tin", "rematter:lead",
            "rematter:silver", "rematter:nickel", "rematter:zinc", "rematter:aluminum", "rematter:osmium",
            "rematter:uranium", "rematter:platinum", "rematter:iridium", "rematter:titanium",
            "rematter:chromium", "rematter:magnesium", "rematter:lithium", "rematter:beryllium",
            "rematter:boron", "rematter:mercury", "rematter:sodium", "rematter:plutonium",
            "rematter:silicon", "rematter:netherium");
    private static final Set<String> CRYSTAL_SUBSTANCES = Set.of(
            "rematter:diamond", "rematter:emerald", "rematter:amethyst", "rematter:lapis",
            "rematter:crystalline_silica", "rematter:certus_quartz", "rematter:charged_certus_quartz",
            "rematter:ruby", "rematter:sapphire", "rematter:peridot", "rematter:amber",
            "rematter:fluorite", "rematter:cinnabar", "rematter:prismarine_shard", "rematter:prismarine_crystal",
            "rematter:redstone_crystal", "rematter:charged_redstone", "rematter:arcanium",
            "rematter:arcane_crystal", "rematter:corrupted_arcane_crystal", "rematter:dimensional_shard",
            "rematter:entro", "rematter:echo", "rematter:enderion", "rematter:fluxite",
            "rematter:hextech", "rematter:stellarite", "rematter:quantium", "rematter:vitalium",
            "rematter:cinderium", "rematter:ember", "rematter:astrion", "rematter:uraninite",
            "rematter:inferium_essence", "rematter:prudentium_essence", "rematter:tertium_essence",
            "rematter:imperium_essence", "rematter:supremium_essence", "rematter:insanium_essence",
            "rematter:awakened_supremium_essence", "rematter:prosperity_essence", "rematter:soulium_essence");
    private static final Set<String> STONE_SUBSTANCES = Set.of(
            "rematter:silica", "rematter:alumina", "rematter:calcium_oxide", "rematter:mineral_mix",
            "rematter:soil", "rematter:clay", "rematter:sky_stone", "rematter:burnt_otherstone",
            "rematter:burnt_otherrock", "rematter:salt", "rematter:sulfur", "rematter:carbon",
            "rematter:coal", "rematter:pigment");
    private static final String[] ORGANIC_PATH_TOKENS = {
            "petal", "flower", "dye", "wool", "carpet", "log", "wood", "plank", "sapling", "leaf", "leaves",
            "berry", "berries", "slime", "ink_sac", "feather", "string", "leather", "mushroom", "shroom", "wart",
            "chorus", "bamboo", "kelp", "moss_block", "moss_carpet", "pale_moss", "vine", "fern", "coral", "sponge",
            "grass", "crop", "seed", "wheat", "apple", "melon", "pumpkin", "potato", "carrot", "beetroot"
    };
    private static final String[] CRYSTAL_PATH_TOKENS = {
            "crystal", "gem", "shard", "diamond", "emerald", "amethyst", "quartz", "ruby", "sapphire",
            "peridot", "fluorite", "cinnabar", "lapis", "redstone", "glowstone", "essence", "ender_dust",
            "ender_pearl", "flux_dust", "amber", "dimensionalshard"
    };

    private final String labelKey;
    private final String hintKey;
    private final UiIcon icon;

    SynthesizerCategory(String labelKey, String hintKey, UiIcon icon) {
        this.labelKey = labelKey;
        this.hintKey = hintKey;
        this.icon = icon;
    }

    public String labelKey() { return labelKey; }
    public String hintKey() { return hintKey; }
    public UiIcon icon() { return icon; }

    public boolean matches(ProcessPresentation.Product product) {
        return this == ALL || classify(product) == this;
    }

    /** Wraps the category button grid cleanly inside {@code availableWidth} so it never overlaps adjacent panels. */
    public static CategoryLayout layoutForWidth(int availableWidth) {
        int buttonWidth = 18, buttonHeight = 16, stepX = 20, stepY = 18;
        int count = values().length;
        int perRow = Math.max(1, Math.min(count, (Math.max(buttonWidth, availableWidth) + (stepX - buttonWidth)) / stepX));
        int rows = (count + perRow - 1) / perRow;
        return new CategoryLayout(buttonWidth, buttonHeight, stepX, stepY, perRow, rows);
    }

    /** Assigns every synthesis output to exactly one concrete category (never {@link #ALL}). */
    public static SynthesizerCategory classify(ProcessPresentation.Product product) {
        if (product == null) return STONE;
        if ("fluid".equals(product.outputKind())) return FLUIDS;

        String outputId = product.outputId().toLowerCase(Locale.ROOT);
        int colon = outputId.indexOf(':');
        String path = colon >= 0 ? outputId.substring(colon + 1) : outputId;

        boolean hasOrganic = false;
        boolean hasMetal = false;
        long crystalMass = 0L;
        long stoneMass = 0L;
        for (var entry : product.inputs().entrySet()) {
            String sub = entry.getKey().id().toString();
            long amount = entry.getValue();
            if (ORGANIC_SUBSTANCES.contains(sub)) hasOrganic = true;
            if (METAL_SUBSTANCES.contains(sub)) hasMetal = true;
            if (CRYSTAL_SUBSTANCES.contains(sub)) crystalMass += amount;
            if (STONE_SUBSTANCES.contains(sub)) stoneMass += amount;
        }
        if (hasOrganic || containsAny(path, ORGANIC_PATH_TOKENS)) return ORGANIC;
        if (hasMetal) return METALS;
        if (crystalMass > 0L && (crystalMass >= stoneMass || containsAny(path, CRYSTAL_PATH_TOKENS))) return CRYSTALS;
        return STONE;
    }

    /**
     * Evaluates whether {@code product} should stay visible under the "In stock only" filter:
     * - {@link StockState#COMPLETE}: exact full-batch check across all required inputs.
     * - {@link StockState#PARTIAL}: hides only recipes with a known shortage in the partial map; unknown keys stay visible.
     * - {@link StockState#NO_STORAGE} / {@link StockState#SYNCING}: false (UI displays the matching status message).
     */
    public static boolean isCraftable(ProcessPresentation.Product product, Map<SubstanceKey, Long> available, StockState state) {
        if (product == null || product.inputs().isEmpty()) return false;
        if (state == StockState.NO_STORAGE || state == StockState.SYNCING) return false;
        for (var entry : product.inputs().entrySet()) {
            Long have = available.get(entry.getKey());
            if (have == null) {
                if (state == StockState.COMPLETE) return false;
                continue;
            }
            if (have < entry.getValue()) return false;
        }
        return true;
    }

    /** Convenience overload for complete-stock callers and tests. */
    public static boolean isCraftable(ProcessPresentation.Product product, Map<SubstanceKey, Long> available, boolean hasStorage) {
        return isCraftable(product, available, hasStorage ? StockState.COMPLETE : StockState.NO_STORAGE);
    }

    private static boolean containsAny(String value, String[] tokens) {
        for (String token : tokens) {
            if (value.contains(token)) return true;
        }
        return false;
    }
}
