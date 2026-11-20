package dev.latrox.rematter.compat.rs.mixin;

import java.util.List;
import java.util.Set;
import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/** Applies the recovery hook only when the installed Refined Storage still declares the exact target method. */
public final class RsMixinPlugin implements IMixinConfigPlugin {
    static final String HOOK_NAME = "preRemoveSideEffects";
    static final String HOOK_DESCRIPTOR = "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V";
    private static final Logger LOGGER = LoggerFactory.getLogger(RsMixinPlugin.class);

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        var mods = LoadingModList.get();
        if (mods == null || mods.getModFileById("refinedstorage") == null) return false;
        ClassNode target;
        try { target = MixinService.getService().getBytecodeProvider().getClassNode(targetClassName); }
        catch (ClassNotFoundException | java.io.IOException missing) { target = null; }
        boolean supported = declaresHook(target);
        if (supported) LOGGER.info("Refined Storage autocrafter matter recovery is active");
        else LOGGER.warn("Refined Storage autocrafter matter recovery disabled: {} no longer declares {}{}",
                targetClassName, HOOK_NAME, HOOK_DESCRIPTOR);
        return supported;
    }
    static boolean declaresHook(ClassNode target) {
        return target != null && target.methods.stream()
                .anyMatch(method -> method.name.equals(HOOK_NAME) && method.desc.equals(HOOK_DESCRIPTOR));
    }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo info) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo info) {}
}
