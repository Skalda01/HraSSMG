package org.example;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.JarURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;

/** Automaticky nalezené resources/levels a vlastní levels/*.properties v pracovním adresáři. */
final class LevelCatalog {
    private static final Level DEFAULT = initialLevel();
    static final String FIRST_ID = DEFAULT.id();
    private static final int MAX_BYTES = 64_000;
    final Path directory;
    private List<Level> levels = List.of();
    private List<String> errors = List.of();

    LevelCatalog(Path directory) { this.directory = directory; reload(); }
    static Level defaultLevel() { return DEFAULT; }
    List<Level> levels() { return levels; }
    List<String> errors() { return errors; }

    void reload() {
        var loaded = new LinkedHashMap<String, Level>();
        List<String> failures = new ArrayList<>();
        bundled(loaded, failures);
        readDirectory(directory, loaded, failures);
        if (loaded.isEmpty()) {
            failures.add("Nebyla nalezena žádná platná úroveň.");
        }
        levels = loaded.values().stream().filter(Level::isVisible).sorted(Comparator.comparing(Level::id)).toList();
        errors = List.copyOf(failures);


    }

    private static Level initialLevel() {
        var loaded = new LinkedHashMap<String, Level>();
        List<String> failures = new ArrayList<>();
        bundled(loaded, failures);
        return loaded.values().stream().min(Comparator.comparing((Level level) -> !level.isVisible()).thenComparing(Level::id))
                .orElseThrow(() -> new IllegalStateException("Chybí platná úroveň v resources/levels. " + String.join("; ", failures)));
    }

    private static void bundled(Map<String, Level> loaded, List<String> failures) {
        try {
            var source = LevelCatalog.class.getProtectionDomain().getCodeSource();
            Path origin = source == null ? null : Path.of(source.getLocation().toURI()).toAbsolutePath().normalize();
            Path project = Path.of("").toAbsolutePath().normalize();
            Path resources = project.resolve("src/main/resources/levels");
            // Ve vývoji čti zdroje místo případně zastaralých kopií v target/classes.
            if (origin != null && Files.isDirectory(origin) && origin.startsWith(project) && Files.isDirectory(resources)) {
                readDirectory(resources, loaded, failures);
                return;
            }

            var directories = new LinkedHashSet<Path>();
            var jars = new LinkedHashSet<Path>();
            var roots = LevelCatalog.class.getClassLoader().getResources("levels");
            while (roots.hasMoreElements()) {
                var root = roots.nextElement();
                if (root.getProtocol().equals("file")) directories.add(Path.of(root.toURI()).toAbsolutePath().normalize());
                else if (root.getProtocol().equals("jar")) {
                    var connection = (JarURLConnection) root.openConnection();
                    jars.add(Path.of(connection.getJarFileURL().toURI()).toAbsolutePath().normalize());
                }
            }
            // JAR nemusí obsahovat samostatný adresářový záznam levels/.
            if (origin != null) {
                if (Files.isDirectory(origin)) directories.add(origin.resolve("levels"));
                else if (Files.isRegularFile(origin)) jars.add(origin);
            }
            for (Path path : directories) readDirectory(path, loaded, failures);
            for (Path path : jars) readJar(path, loaded, failures);
        } catch (Exception e) { failures.add("resources/levels: " + e.getMessage()); }
    }

    private static void readDirectory(Path directory, Map<String, Level> loaded, List<String> failures) {
        if (!Files.isDirectory(directory)) return;
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().endsWith(".properties")).sorted().toList()) {
                try (var input = Files.newInputStream(file)) {
                    add(loaded, read(input, file.getFileName().toString()));
                } catch (Exception e) { failures.add(file + ": " + e.getMessage()); }
            }
        } catch (Exception e) { failures.add(directory + ": " + e.getMessage()); }
    }

    private static void readJar(Path path, Map<String, Level> loaded, List<String> failures) {
        try (var jar = new JarFile(path.toFile())) {
            var entries = jar.stream().filter(entry -> !entry.isDirectory() && entry.getName().startsWith("levels/")
                    && entry.getName().endsWith(".properties") && entry.getName().indexOf('/', 7) < 0)
                    .sorted(Comparator.comparing(java.util.jar.JarEntry::getName)).toList();
            for (var entry : entries) {
                try (var input = jar.getInputStream(entry)) {
                    add(loaded, read(input, entry.getName().substring("levels/".length())));
                } catch (Exception e) { failures.add(path + "!/" + entry.getName() + ": " + e.getMessage()); }
            }
        } catch (Exception e) { failures.add(path + ": " + e.getMessage()); }
    }

    private static Level read(InputStream input, String name) throws Exception {
        byte[] bytes = input.readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("Soubor je větší než 64 kB");
        try (var reader = new InputStreamReader(new java.io.ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            return Level.read(reader, name.substring(0, name.length() - ".properties".length()));
        }
    }

    private static void add(Map<String, Level> loaded, Level level) {
        if (loaded.containsKey(level.id())) throw new IllegalArgumentException("Duplicitní id úrovně: " + level.id());
        loaded.put(level.id(), level);
    }
}
