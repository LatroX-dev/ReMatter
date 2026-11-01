package dev.latrox.rematter.content;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MachineSnapshotInitializationTest {
    private static final String SNAPSHOT = "dev.latrox.rematter.content.MachineSnapshot";

    @Test void eitherNestedEmptyMayInitializeBeforeTheOuterSnapshotInAFreshClassLoader() throws Exception {
        for (String first : List.of(SNAPSHOT + "$Preview", SNAPSHOT + "$CellView", SNAPSHOT)) {
            // Each order starts with genuinely uninitialized production classes, regardless of JUnit class order.
            var loader = new SnapshotLoader(getClass().getClassLoader());
            assertNotNull(Class.forName(first, true, loader).getField("EMPTY").get(null));
            var snapshotType = Class.forName(SNAPSHOT, true, loader);
            var snapshot = snapshotType.getField("EMPTY").get(null);
            assertNotNull(snapshot);
            var previewType = Class.forName(SNAPSHOT + "$Preview", true, loader);
            var cellType = Class.forName(SNAPSHOT + "$CellView", true, loader);
            assertSame(previewType.getField("EMPTY").get(null), snapshotType.getMethod("preview").invoke(snapshot));
            assertSame(cellType.getField("EMPTY").get(null), snapshotType.getMethod("cell").invoke(snapshot));
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
