package dev.latrox.rematter.client;

import dev.latrox.rematter.chemistry.ReservedSubstances;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.compat.SubstanceInformation;
import dev.latrox.rematter.network.ClientChemistry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Complete substance facts and optional viewer actions; loading this class never loads an optional mod. */
public final class SubstanceDetails {
    public record Hover(SubstanceKey key, long amount) {
        public Hover { if (key == null || amount < 0) throw new IllegalArgumentException("Invalid hovered substance"); }
    }

    /** Installed by the native viewer plugin only when that viewer is present. */
    public interface Viewer {
        boolean available();
        Component recipesKey();
        Component usesKey();
        void recipes(Hover value);
        void uses(Hover value);
    }

    private static volatile Viewer viewer;
    private SubstanceDetails() {}
    public static void installViewer(Viewer value) { viewer = java.util.Objects.requireNonNull(value); }
    public static boolean viewerAvailable() { return viewer != null && viewer.available(); }
    public static Component tr(String key, Object... args) { return Component.translatable("screen.rematter.substance." + key, args); }

    public static List<Component> tooltip(Hover hover, boolean detailsAction) {
        var info = SubstanceInformation.inspect(ClientChemistry.current(), hover.key(), hover.amount());
        var lines = new ArrayList<Component>();
        lines.add(SubstanceNames.labelled(hover.key()));
        lines.add(tr("amount", Quantities.matter(Quantities.number(hover.amount()))));
        lines.add(tr("phase", SubstanceNames.phase(hover.key().phase())));
        info.definition().ifPresent(definition -> lines.add(tr("kind", kind(definition.kind()))));
        if (info.definition().isEmpty()) lines.add(Component.translatable("screen.rematter.unknown_substance_hint"));
        lines.add(tr("identity", hover.key().id().toString()));
        if (detailsAction) lines.add(tr("details_hint"));
        if (detailsAction && viewerAvailable()) lines.add(tr("viewer_keys", viewer.recipesKey(), viewer.usesKey()));
        return lines;
    }

    public static List<Component> information(Hover hover) {
        var info = SubstanceInformation.inspect(ClientChemistry.current(), hover.key(), hover.amount());
        var lines = new ArrayList<Component>(tooltip(hover, false));
        if (ReservedSubstances.reserved(hover.key())) {
            lines.add(tr("primal_storage"));
        } else info.definition().ifPresent(definition -> {
            lines.add(tr("formula", definition.symbol().isBlank() ? tr("not_defined") : Component.literal(definition.symbol())));
            var phases = Component.empty();
            definition.phases().stream().sorted().forEach(phase -> {
                if (!phases.getSiblings().isEmpty()) phases.append(", ");
                phases.append(SubstanceNames.phase(phase));
            });
            lines.add(tr("phases", phases));
            lines.add(tr(definition.kind() == dev.latrox.rematter.chemistry.SubstanceKind.MIXTURE ? "composition_mixture" : "composition"));
            for (var element : info.elements()) {
                Component name = element.definition().map(SubstanceNames::name).orElseGet(() -> Component.literal(element.id().toString()));
                Component symbol = element.definition().filter(value -> !value.symbol().isBlank())
                        .<Component>map(value -> Component.literal(value.symbol())).orElseGet(() -> Component.literal(element.id().toString()));
                lines.add(tr("element", symbol, name, Quantities.number(element.count())));
            }
        });
        lines.add(tr("read_only"));
        return lines;
    }

    private static Component kind(dev.latrox.rematter.chemistry.SubstanceKind kind) { return switch(kind) {
        case ELEMENT -> tr("kind.element");
        case ALLOTROPE -> tr("kind.allotrope");
        case COMPOUND -> tr("kind.compound");
        case MIXTURE -> tr("kind.mixture");
    }; }
    public static MutableComponent joined(List<Component> lines) {
        var result = Component.empty();
        for (var line : lines) { if (!result.getSiblings().isEmpty()) result.append("\n"); result.append(line); }
        return result;
    }
    public static void open(Hover hover, int accent) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player != null) minecraft.pushGuiLayer(new SubstanceDetailsScreen(hover, minecraft.player.containerMenu, accent));
    }
    public static void recipes(Hover hover) { if (viewerAvailable()) viewer.recipes(hover); }
    public static void uses(Hover hover) { if (viewerAvailable()) viewer.uses(hover); }
}
