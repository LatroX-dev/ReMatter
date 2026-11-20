package dev.latrox.rematter.automation;

import dev.latrox.rematter.content.MachineBlockEntity;
import java.util.*;
import java.util.function.BiFunction;

/** Factories are registered only by installed optional integrations; the base mod has no native API links. */
public final class AutomationBackends {
    private static final Map<String,BiFunction<MachineBlockEntity,AutomationPort,AutomationAdapter>> FACTORIES=new LinkedHashMap<>();
    private AutomationBackends() {}
    public static void register(String id,BiFunction<MachineBlockEntity,AutomationPort,AutomationAdapter> factory) {
        if(FACTORIES.putIfAbsent(id,Objects.requireNonNull(factory))!=null)throw new IllegalArgumentException("Duplicate automation backend");
    }
    public static List<String> available() { return List.copyOf(FACTORIES.keySet()); }
    /** The choice after current, or the first one when current is no longer available; empty when nothing else can be chosen. */
    public static Optional<String> after(List<String> choices,String current) {
        int index=choices.indexOf(current);
        if(choices.isEmpty()||index>=0&&choices.size()<2)return Optional.empty();
        return Optional.of(choices.get((index+1)%choices.size()));
    }
    public static AutomationAdapter create(String id,MachineBlockEntity host,AutomationPort port) {
        var factory=FACTORIES.get(id);return factory==null?null:factory.apply(host,port);
    }
}
