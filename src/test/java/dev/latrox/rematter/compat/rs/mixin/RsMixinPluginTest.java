package dev.latrox.rematter.compat.rs.mixin;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import static org.junit.jupiter.api.Assertions.*;

class RsMixinPluginTest {
    @Test void theCompiledAgainstAutocrafterDeclaresTheRecoveryHook() throws Exception {
        var node = new ClassNode();
        try (var bytes = getClass().getResourceAsStream(
                "/com/refinedmods/refinedstorage/common/autocrafting/autocrafter/AutocrafterBlockEntity.class")) {
            assertNotNull(bytes);
            new ClassReader(bytes).accept(node, ClassReader.SKIP_CODE);
        }
        assertTrue(RsMixinPlugin.declaresHook(node));
    }

    @Test void aMissingOrChangedTargetMethodSkipsTheMixinInsteadOfFailingTransformation() {
        assertFalse(RsMixinPlugin.declaresHook(null));
        var changed = new ClassNode();
        changed.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, RsMixinPlugin.HOOK_NAME,
                "(Lnet/minecraft/core/BlockPos;)V", null, null));
        changed.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "preRemove", RsMixinPlugin.HOOK_DESCRIPTOR, null, null));
        assertFalse(RsMixinPlugin.declaresHook(changed));
        changed.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, RsMixinPlugin.HOOK_NAME, RsMixinPlugin.HOOK_DESCRIPTOR, null, null));
        assertTrue(RsMixinPlugin.declaresHook(changed));
    }
}
