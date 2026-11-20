package dev.latrox.rematter.content.preparation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/** Automatic pattern publication is independent of whether a physical recipe can run. */
public final class PreparationNetworkPolicy {
    public static final MapCodec<Boolean> CODEC=Codec.BOOL.optionalFieldOf("network_recipe",true);
    private PreparationNetworkPolicy() {}
    public static <T> List<T> exported(Collection<T> recipes,Predicate<T> enabled) {
        return recipes.stream().filter(enabled).toList();
    }
}
