package dev.latrox.rematter.content;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MachineSnapshotInitializationTest {
    private static final String SNAPSHOT = "dev.latrox.rematter.content.MachineSnapshot";

    @Test void eitherNestedEmptyMayInitializeBeforeTheOuterSnapshotInAFreshClassLoader() throws Exception {
        var nested = List.of("Preview", "StructureView", "NetworkView", "CellView");
        for (String first : List.of(SNAPSHOT + "$Preview", SNAPSHOT + "$StructureView", SNAPSHOT + "$NetworkView", SNAPSHOT + "$CellView", SNAPSHOT)) {
            // Each order starts with genuinely uninitialized production classes, regardless of JUnit class order.
            var loader = new SnapshotLoader(getClass().getClassLoader());
            assertNotNull(Class.forName(first, true, loader).getField("EMPTY").get(null));
            var snapshotType = Class.forName(SNAPSHOT, true, loader);
            var snapshot = snapshotType.getField("EMPTY").get(null);
            assertNotNull(snapshot);
            for (String name : nested) {
                var empty = Class.forName(SNAPSHOT + "$" + name, true, loader).getField("EMPTY").get(null);
                assertNotNull(empty);
                if (!name.equals("CellView")) {
                    String accessor = Character.toLowerCase(name.charAt(0)) + name.substring(1).replace("View", "");
                    assertSame(empty, snapshotType.getMethod(accessor).invoke(snapshot));
                }
            }
            assertEquals(List.of(), snapshotType.getMethod("cells").invoke(snapshot));
            assertEquals(-1, snapshotType.getMethod("containerId").invoke(snapshot));
        }
    }

    /** Isolate only the snapshot nest; game/library classes retain the existing test runtime. */
    private static final class SnapshotLoader extends ClassLoader {
        SnapshotLoader(ClassLoader parent) { super(parent); }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.equals(SNAPSHOT) && !name.startsWith(SNAPSHOT + "$")) return super.loadClass(name, resolve);
            synchronized (getClassLoadingLock(name)) {
                Class<?> type = findLoadedClass(name);
                if (type == null) {
                    String resource = name.replace('.', '/') + ".class";
                    try (var stream = getParent().getResourceAsStream(resource)) {
                        if (stream == null) throw new ClassNotFoundException(name);
                        byte[] bytes = stream.readAllBytes();
                        type = defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException error) { throw new ClassNotFoundException(name, error); }
                }
                if (resolve) resolveClass(type);
                return type;
            }
        }
    }
}
