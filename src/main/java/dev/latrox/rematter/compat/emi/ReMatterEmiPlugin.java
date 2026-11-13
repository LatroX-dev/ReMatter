package dev.latrox.rematter.compat.emi;

import com.mojang.logging.LogUtils;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.EmiInitRegistry;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.runtime.EmiReloadManager;
import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.client.MachineScreen;
import dev.latrox.rematter.client.PrimalTargetScreen;
import dev.latrox.rematter.client.ProcessSelectionScreen;
import dev.latrox.rematter.client.SubstanceDetails;
import dev.latrox.rematter.client.SubstanceDetailsScreen;
import dev.latrox.rematter.client.SubstanceHoverProvider;
import dev.latrox.rematter.client.SubstanceScreenLayer;
import dev.latrox.rematter.compat.ChemistryViewer;
import dev.latrox.rematter.content.MachineKind;
import dev.latrox.rematter.content.ModContent;
import dev.latrox.rematter.compat.PreparationDisplay;
import dev.latrox.rematter.content.preparation.PreparationRecipes;
import dev.latrox.rematter.network.ClientChemistry;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Native SEMI/EMI integration; no JEI classes or recipe bridge are required. */
@EmiEntrypoint
public final class ReMatterEmiPlugin implements EmiPlugin {
    private static volatile ChemistryRegistry displayed;
    private static volatile List<PreparationRecipes.Entry> displayedPreparation = List.of();
    private static boolean reloadWarningLogged;
    private static boolean screenHooksInstalled;
    private static ViewerReturn viewerReturn;
    private record ViewerReturn(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> container,
                                List<net.minecraft.client.gui.screens.Screen> layers) {}
    static {
        ClientChemistry.addListener(catalogue -> {
            if (catalogue != displayed) refreshRecipes();
        });
        PreparationRecipes.addClientListener(catalogue -> {
            if (catalogue != displayedPreparation) refreshRecipes();
        });
    }

    private static void refreshRecipes() {
        if (Minecraft.getInstance().level == null) return;
        // SEMI 1.1.24 for 26.1.2 has no public API to rebuild after an extra native recipe sync.
        // The installed reload manager coalesces requests while a reload is already running.
        try {
            if (EmiReloadManager.getStatus() != 0) EmiReloadManager.reload();
        } catch (LinkageError | RuntimeException exception) {
            if (!reloadWarningLogged) {
                reloadWarningLogged = true;
                LogUtils.getLogger().warn("ReMatter cannot refresh this SEMI version after recipe sync; reconnect to refresh recipes", exception);
            }
        }
    }

    @Override public void initialize(EmiInitRegistry registry) {
        registry.addIngredientSerializer(MatterEmiStack.class, new MatterEmiStack.Serializer());
        if (!screenHooksInstalled) {
            screenHooksInstalled = true;
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST,
                    ReMatterEmiPlugin::layerKeyPressed);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ReMatterEmiPlugin::restoreViewerLayers);
        }
    }

    @Override public void register(EmiRegistry registry) {
        SubstanceDetails.installViewer(new SubstanceDetails.Viewer() {
            @Override public boolean available() { return dev.emi.emi.config.EmiConfig.enabled; }
            @Override public java.util.Optional<Component> recipesKey() { return keyboardBindingText(dev.emi.emi.config.EmiConfig.viewRecipes); }
            @Override public java.util.Optional<Component> usesKey() { return keyboardBindingText(dev.emi.emi.config.EmiConfig.viewUses); }
            @Override public void recipes(SubstanceDetails.Hover value) { showMatter(value, false); }
            @Override public void uses(SubstanceDetails.Hover value) { showMatter(value, true); }
        });
        registry.addGenericStackProvider((screen, x, y) -> {
            if (!(screen instanceof SubstanceHoverProvider provider)) return EmiStackInteraction.EMPTY;
            // Not mouse-clickable: left click keeps the machine's favourite/filter action and right click opens details.
            // EMI still discovers this ingredient for its native (including rebound) recipe/use key bindings.
            return provider.substanceAt(x, y).map(value -> new EmiStackInteraction(matterStack(value), null, false)).orElse(EmiStackInteraction.EMPTY);
        });
        registry.addScreenBoundsProvider(ProcessSelectionScreen.class, ReMatterEmiPlugin::substanceBounds);
        registry.addScreenBoundsProvider(PrimalTargetScreen.class, ReMatterEmiPlugin::substanceBounds);
        registry.addScreenBoundsProvider(SubstanceDetailsScreen.class, ReMatterEmiPlugin::substanceBounds);
        registry.addExclusionArea(MachineScreen.class, (screen, consumer) -> {
            for (var area : screen.viewerExclusionAreas())
                consumer.accept(new Bounds(area.getX(), area.getY(), area.getWidth(), area.getHeight()));
        });
        ChemistryRegistry catalogue = ClientChemistry.current();
        displayed = catalogue;
        displayedPreparation = PreparationRecipes.clientRecipes();
        var preparationCategories = new EnumMap<MachineKind, EmiRecipeCategory>(MachineKind.class);
        for (var machine : PreparationDisplay.MACHINES) {
            var preparation = new EmiRecipeCategory(Identifier.fromNamespaceAndPath("rematter", "preparation/" + machine.id()),
                    EmiStack.of(ModContent.MACHINES.get(machine).get())) {
                @Override public Component getName() { return Component.translatable("block.rematter." + machine.id()); }
            };
            preparationCategories.put(machine, preparation);
            registry.addCategory(preparation);
            registry.addWorkstation(preparation, (EmiStack) preparation.icon);
        }
        for (var recipe : PreparationDisplay.from(displayedPreparation))
            registry.addRecipe(new PreparationEmiRecipe(preparationCategories.get(recipe.machine()), recipe));
        var categories = new EnumMap<ChemistryViewer.Category, EmiRecipeCategory>(ChemistryViewer.Category.class);
        for (var value : ChemistryViewer.Category.values()) {
            var machines = value.machines();
            var category = new EmiRecipeCategory(Identifier.fromNamespaceAndPath("rematter", value.id()),
                    EmiStack.of(ModContent.MACHINES.get(machines.getFirst()).get())) {
                @Override public Component getName() { return value.title(); }
            };
            categories.put(value, category);
            registry.addCategory(category);
            for (var machine : machines) registry.addWorkstation(category, EmiStack.of(ModContent.MACHINES.get(machine).get()));
        }
        var entries = ChemistryViewer.entries(catalogue);
        ChemistryViewer.substances(entries).forEach(key -> registry.addEmiStack(new MatterEmiStack(key, 1)));
        for (var entry : entries) {
            var inputs = new ArrayList<EmiIngredient>();
            entry.consumed().ifPresent(resource -> inputs.add(stack(resource)));
            entry.inputs().forEach(matter -> inputs.add(new MatterEmiStack(matter.key(), matter.amount())));
            var outputs = new ArrayList<EmiStack>();
            entry.produced().ifPresent(resource -> outputs.add(stack(resource)));
            entry.outputs().forEach(matter -> outputs.add(new MatterEmiStack(matter.key(), matter.amount())));
            registry.addRecipe(new ChemistryEmiRecipe(categories.get(entry.category()), entry.id(), inputs, outputs,
                    entry.energy(), entry.ticks(), entry.notes(), ChemistryViewer.researchTarget(entry)));
        }
    }

    private static MatterEmiStack matterStack(SubstanceDetails.Hover value) {
        // A ghost filter has zero stored units; recipe lookup still asks about one formula unit of the same phase.
        return new MatterEmiStack(value.key(), Math.max(1, value.amount()));
    }
    /** SEMI's generic label prefers mouse binds; these rows deliberately reserve both mouse buttons. */
    private static java.util.Optional<Component> keyboardBindingText(dev.emi.emi.input.EmiBind binding) {
        var keys = binding.boundKeys.stream().filter(key -> !key.isUnbound()
                && key.key().getType() != com.mojang.blaze3d.platform.InputConstants.Type.MOUSE).toList();
        if (keys.isEmpty()) return java.util.Optional.empty();
        var label = Component.empty();
        for (var key : keys) {
            if (!label.getSiblings().isEmpty()) label.append(" / ");
            label.append(key.getKeyText(net.minecraft.ChatFormatting.RESET));
        }
        return java.util.Optional.of(label.withStyle(style -> style.withColor(dev.latrox.rematter.client.UiPalette.MUTED & 0xffffff)));
    }
    private static Bounds substanceBounds(SubstanceHoverProvider screen) {
        var area = screen.substancePanelBounds();
        return new Bounds(area.getX(), area.getY(), area.getWidth(), area.getHeight());
    }

    /** SEMI requires a real container parent. Pop only our read-only layers, never close the menu. */
    private static void showMatter(SubstanceDetails.Hover value, boolean uses) {
        var minecraft = Minecraft.getInstance();
        var layers = new ArrayList<net.minecraft.client.gui.screens.Screen>();
        while (minecraft.screen instanceof SubstanceScreenLayer) {
            layers.add(minecraft.screen);
            minecraft.popGuiLayer();
        }
        if (!layers.isEmpty()) {
            if (!(minecraft.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> container)) {
                for (var layer : layers.reversed()) minecraft.pushGuiLayer(layer);
                return;
            }
            viewerReturn = new ViewerReturn(container, List.copyOf(layers.reversed()));
        }
        if (uses) EmiApi.displayUses(matterStack(value)); else EmiApi.displayRecipes(matterStack(value));
        // No matching recipe leaves SEMI on the container; return the details immediately in that case.
        restoreViewerLayers(null);
    }

    /** Runs after an actual native EMI Back/Close, not while a recipe result is being shown. */
    private static void restoreViewerLayers(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        var context = viewerReturn;
        if (context == null) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.containerMenu != context.container().getMenu() || minecraft.screen == null) {
            viewerReturn = null;
            return;
        }
        if (minecraft.screen != context.container()) return;
        viewerReturn = null;
        for (var layer : context.layers()) minecraft.pushGuiLayer(layer);
    }

    /** Run before SEMI's global key handler so a normal Screen layer retains its real container context. */
    private static void layerKeyPressed(net.neoforged.neoforge.client.event.ScreenEvent.KeyPressed.Pre event) {
        if (!(event.getScreen() instanceof SubstanceScreenLayer layer) || !SubstanceDetails.viewerAvailable() || EmiApi.isSearchFocused()) return;
        if (event.getScreen().getFocused() instanceof net.minecraft.client.gui.components.EditBox text && text.canConsumeInput()) return;
        var key = event.getKeyEvent();
        boolean recipes = dev.emi.emi.config.EmiConfig.viewRecipes.matchesKey(key.key(), key.scancode());
        boolean uses = dev.emi.emi.config.EmiConfig.viewUses.matchesKey(key.key(), key.scancode());
        if (!recipes && !uses) return;
        var minecraft = Minecraft.getInstance();
        // SEMI invalidates its mouse cache on normal Screen layers. Use the same public scaled
        // MouseHandler coordinates as Minecraft's input path, including window/display scaling.
        var hover = layer.substanceAt(minecraft.mouseHandler.getScaledXPos(minecraft.getWindow()),
                minecraft.mouseHandler.getScaledYPos(minecraft.getWindow()));
        if (hover.isEmpty()) return;
        event.setCanceled(true);
        showMatter(hover.get(), !recipes && uses);
    }

    private static EmiStack stack(ChemistryViewer.Resource resource) {
        return resource.fluid() ? EmiStack.of(BuiltInRegistries.FLUID.getValue(resource.id()), resource.amount())
                : EmiStack.of(new ItemStack(BuiltInRegistries.ITEM.getValue(resource.id()), resource.amount()));
    }
}
