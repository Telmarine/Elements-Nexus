package com.tac5studios.elementsnexus.storage;

import com.tac5studios.elementsnexus.ElementsNexus;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Finds a database driver for the sqlite and mysql backends.
 * The drivers are not bundled with Elements: Nexus. A server that wants one of
 * those backends puts the driver jar in <server>/elements_nexus/drivers.
 * A driver another mod already provides is used first.
 */
final class Drivers {

    /** Kept open for the life of the server so the driver's classes stay loaded. */
    private static URLClassLoader folderLoader;

    private Drivers() {}

    /** The folder driver jars go in. */
    static Path folder() {
        return FMLPaths.GAMEDIR.get().resolve("elements_nexus/drivers").normalize();
    }

    /**
     * Loads and creates the driver.
     *
     * @param className the driver class, e.g. "org.sqlite.JDBC"
     * @param what      name used in messages, e.g. "SQLite"
     * @param download  where to get the jar, shown in the error
     */
    static synchronized Driver load(String className, String what, String download) throws SQLException {
        // 1. Already on the classpath (another mod bundles it).
        try {
            return create(Class.forName(className, true, Drivers.class.getClassLoader()), what);
        } catch (ClassNotFoundException | LinkageError notThere) {
            // fall through to the drivers folder
        }

        // 2. A jar in the drivers folder.
        Path dir = folder();
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
        }
        if (folderLoader == null) {
            List<URL> jars = new ArrayList<>();
            try (Stream<Path> files = Files.list(dir)) {
                for (Path p : (Iterable<Path>) files::iterator) {
                    if (p.getFileName().toString().toLowerCase().endsWith(".jar")) jars.add(p.toUri().toURL());
                }
            } catch (IOException ex) {
                throw new SQLException("Could not read " + dir, ex);
            }
            if (jars.isEmpty()) throw missing(what, dir, download);
            folderLoader = new URLClassLoader(jars.toArray(URL[]::new), Drivers.class.getClassLoader());
            ElementsNexus.LOGGER.info("[Nexus] Loaded {} driver jar(s) from {}.", jars.size(), dir);
        }
        try {
            return create(Class.forName(className, true, folderLoader), what);
        } catch (ClassNotFoundException | LinkageError ex) {
            throw missing(what, dir, download);
        }
    }

    private static Driver create(Class<?> c, String what) throws SQLException {
        try {
            return (Driver) c.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | ClassCastException ex) {
            throw new SQLException("The " + what + " driver could not be started", ex);
        }
    }

    private static SQLException missing(String what, Path dir, String download) {
        return new SQLException("No " + what + " driver found. Put the " + what + " driver jar (" + download
                + ") in " + dir + " and restart");
    }
}
