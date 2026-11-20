package dev.latrox.rematter.mixin;

import java.util.List;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.server.ReloadableServerResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read the defaults prepared for this reload before Minecraft binds them. */
@Mixin(ReloadableServerResources.class)
public interface ReloadableServerResourcesAccessor {
    @Accessor("newComponents")
    List<DataComponentInitializers.PendingComponents<?>> rematter$getPendingComponents();
}
