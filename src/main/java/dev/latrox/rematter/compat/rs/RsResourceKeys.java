package dev.latrox.rematter.compat.rs;

import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import com.refinedmods.refinedstorage.common.support.resource.FluidResource;
import dev.latrox.rematter.automation.AutomationPort.Resource;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/** No component-bearing item or fluid may masquerade as a plain recipe ingredient. */
final class RsResourceKeys {
    private RsResourceKeys() {}
    static ResourceKey nativeKey(Resource resource) {
        if(resource.kind()==dev.latrox.rematter.automation.AutomationPort.Kind.MATTER)return new RsMatterKey(resource.matterKey());
        var id=Identifier.parse(resource.id());
        return switch(resource.kind()) {
            case ITEM -> {if(!BuiltInRegistries.ITEM.containsKey(id))throw new IllegalArgumentException("Missing item");yield new ItemResource(BuiltInRegistries.ITEM.getValue(id));}
            case FLUID -> {if(!BuiltInRegistries.FLUID.containsKey(id))throw new IllegalArgumentException("Missing fluid");yield new FluidResource(BuiltInRegistries.FLUID.getValue(id));}
            case MATTER -> throw new AssertionError();
        };
    }
    static Optional<Resource> commonKey(ResourceKey key) {
        if(key instanceof RsMatterKey matter)return Optional.of(Resource.matter(matter.substance()));
        if(key instanceof ItemResource item&&item.components().isEmpty())return Optional.of(Resource.item(BuiltInRegistries.ITEM.getKey(item.item()).toString()));
        if(key instanceof FluidResource fluid&&fluid.components().isEmpty())return Optional.of(Resource.fluid(BuiltInRegistries.FLUID.getKey(fluid.fluid()).toString()));
        return Optional.empty();
    }
}
