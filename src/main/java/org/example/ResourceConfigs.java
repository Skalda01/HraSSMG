package org.example;

import java.net.JarURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.jar.JarFile;

/** UTF-8 properties ze zdrojových resources, classpath složek a JAR bez ručního indexu. */
final class ResourceConfigs {
    record Entry(String name, String text) {}
    static List<Entry> read(String folder, List<String> errors) {
        var result = new ArrayList<Entry>();
        try {
            var source = ResourceConfigs.class.getProtectionDomain().getCodeSource();
            Path origin = source == null ? null : Path.of(source.getLocation().toURI()).toAbsolutePath().normalize();
            Path project = Path.of("").toAbsolutePath().normalize();
            Path resources = project.resolve("src/main/resources").resolve(folder);
            if (origin != null && Files.isDirectory(origin) && origin.startsWith(project) && Files.isDirectory(resources)) {
                directory(resources, result, errors); return List.copyOf(result);
            }
            var directories = new LinkedHashSet<Path>();
            var jars = new LinkedHashSet<Path>();
            var roots = ResourceConfigs.class.getClassLoader().getResources(folder);
            while (roots.hasMoreElements()) {
                var root = roots.nextElement();
                if (root.getProtocol().equals("file")) directories.add(Path.of(root.toURI()).toAbsolutePath().normalize());
                else if (root.getProtocol().equals("jar")) jars.add(Path.of(((JarURLConnection) root.openConnection()).getJarFileURL().toURI()).toAbsolutePath().normalize());
            }
            if (origin != null) {
                if (Files.isDirectory(origin)) directories.add(origin.resolve(folder));
                else if (Files.isRegularFile(origin)) jars.add(origin);
            }
            for (Path path : directories) directory(path, result, errors);
            String prefix = folder + "/";
            for (Path path : jars) {
                try (var jar = new JarFile(path.toFile())) {
                    for (var entry : jar.stream().filter(e -> !e.isDirectory() && e.getName().startsWith(prefix)
                            && e.getName().endsWith(".properties") && e.getName().indexOf('/', prefix.length()) < 0)
                            .sorted(java.util.Comparator.comparing(java.util.jar.JarEntry::getName)).toList()) {
                        try (var input = jar.getInputStream(entry)) {
                            result.add(new Entry(entry.getName().substring(prefix.length()), text(input)));
                        } catch (Exception e) { errors.add(path + "!/" + entry.getName() + ": " + e.getMessage()); }
                    }
                } catch (Exception e) { errors.add(path + ": " + e.getMessage()); }
            }
        } catch (Exception e) { errors.add(folder + ": " + e.getMessage()); }
        return List.copyOf(result);
    }
    private static void directory(Path directory, List<Entry> result, List<String> errors) {
        if (!Files.isDirectory(directory)) return;
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().endsWith(".properties")).sorted().toList()) {
                try (var input = Files.newInputStream(file)) { result.add(new Entry(file.getFileName().toString(), text(input))); }
                catch (Exception e) { errors.add(file + ": " + e.getMessage()); }
            }
        } catch (Exception e) { errors.add(directory + ": " + e.getMessage()); }
    }
    private static String text(java.io.InputStream input) throws Exception {
        byte[] bytes = input.readNBytes(64_001);
        if (bytes.length > 64_000) throw new IllegalArgumentException("Soubor je větší než 64 kB");
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
