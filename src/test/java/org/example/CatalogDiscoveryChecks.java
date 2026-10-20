package org.example;

import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/** Izolované načítání skutečných resources ze složky i JAR bez adresářových záznamů. */
public final class CatalogDiscoveryChecks {
    private static int checks;
    private static final String DEFINITION = """
            id=10-alpha
            name=Česká úroveň
            width=5
            height=3
            start=0,1
            dock=0,1
            goal.orange=1
            shelves=orange,4,1,2
            """;
    private static final List<String> CLASSES = List.of("org/example/LevelCatalog", "org/example/Level",
            "org/example/Level$Cell", "org/example/Level$Shelf", "robot/api/Robot", "robot/api/Robot$ShelfType",
            "robot/api/Robot$Direction", "robot/api/Robot$TileType");

    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("hrassmg-discovery-");
        try {
            Path classes = Files.createDirectories(temp.resolve("classes"));
            for (String name : CLASSES) {
                Path target = classes.resolve(name + ".class");
                Files.createDirectories(target.getParent());
                Files.write(target, classBytes(name));
            }
            Path resources = Files.createDirectories(classes.resolve("levels"));
            Files.writeString(resources.resolve("arbitrary-file.properties"), DEFINITION, StandardCharsets.UTF_8);
            try (var loader = loader(classes)) {
                Object catalog = catalog(loader, temp);
                check(ids(catalog).equals(List.of("10-alpha")), "Resources ve složce nevyžadují index ani pevný název");
                check(call(loader.loadClass("org.example.LevelCatalog"), null, "defaultLevel").equals(levels(catalog).getFirst()),
                        "Počáteční level se najde bez původního id a názvu souboru");
                Files.writeString(resources.resolve("new-level.properties"), DEFINITION.replace("10-alpha", "20-beta"));
                call(catalog.getClass(), catalog, "reload");
                check(ids(catalog).equals(List.of("10-alpha", "20-beta")), "Obnovení najde nově přidaný resource bez restartu");
                Files.writeString(resources.resolve("new-level.properties"), DEFINITION.replace("10-alpha", "20-beta").replace("goal.orange=1", "goal.orange=2"));
                call(catalog.getClass(), catalog, "reload");
                check(call(levels(catalog).getLast().getClass(), levels(catalog).getLast(), "orangeGoal").equals(2), "Obnovení načte upravené cíle");
                Files.writeString(resources.resolve("new-level.properties"), DEFINITION.replace("10-alpha", "20-beta") + "isVisible=false\n");
                call(catalog.getClass(), catalog, "reload");
                check(ids(catalog).equals(List.of("10-alpha")), "isVisible=false skryje úroveň ze složky");
                Files.writeString(resources.resolve("arbitrary-file.properties"), DEFINITION + "isVisible=false\n");
                call(catalog.getClass(), catalog, "reload");
                check(ids(catalog).isEmpty() && ((List<?>) call(catalog.getClass(), catalog, "errors")).isEmpty(), "Všechny skryté úrovně vytvoří prázdný seznam bez návratu skryté výchozí úrovně");
                Files.writeString(resources.resolve("new-level.properties"), DEFINITION.replace("10-alpha", "20-beta") + "isVisible=true\n");
                call(catalog.getClass(), catalog, "reload");
                check(ids(catalog).equals(List.of("20-beta")), "isVisible=true vrátí úroveň do seznamu");
                Files.delete(resources.resolve("arbitrary-file.properties"));
                Files.move(resources.resolve("new-level.properties"), resources.resolve("renamed.properties"));
                call(catalog.getClass(), catalog, "reload");
                check(ids(catalog).equals(List.of("20-beta")), "Přejmenování ani odstranění neudržuje staré definice v katalogu");
                Files.writeString(resources.resolve("bad.properties"), DEFINITION.replace("width=5", "width=26"));
                Files.writeString(resources.resolve("duplicate.properties"), DEFINITION.replace("10-alpha", "20-beta"));
                call(catalog.getClass(), catalog, "reload");
                check(ids(catalog).equals(List.of("20-beta")) && ((List<?>) call(catalog.getClass(), catalog, "errors")).size() == 2,
                        "Vadné a duplicitní resources jsou ohlášeny a nevyřadí platnou úroveň");
            }

            Path jar = temp.resolve("úrovně s mezerou.jar");
            try (var output = new JarOutputStream(Files.newOutputStream(jar))) {
                for (String name : CLASSES) entry(output, name + ".class", classBytes(name));
                entry(output, "levels/z-last-name.properties", DEFINITION.getBytes(StandardCharsets.UTF_8));
                entry(output, "levels/a-first-name.properties", DEFINITION.replace("10-alpha", "20-beta").getBytes(StandardCharsets.UTF_8));
                entry(output, "levels/hidden.properties", (DEFINITION.replace("10-alpha", "00-hidden") + "isVisible=false\n").getBytes(StandardCharsets.UTF_8));
            }
            try (var loader = loader(jar)) {
                Object catalog = catalog(loader, temp);
                check(ids(catalog).equals(List.of("10-alpha", "20-beta")), "JAR se načte bez indexu i bez záznamu adresáře levels/");
                check(call(levels(catalog).getFirst().getClass(), levels(catalog).getFirst(), "name").equals("Česká úroveň"), "JAR zachová UTF-8 i mezeru a diakritiku v cestě");
                check(call(call(loader.loadClass("org.example.LevelCatalog"), null, "defaultLevel").getClass(),
                        call(loader.loadClass("org.example.LevelCatalog"), null, "defaultLevel"), "id").equals("10-alpha"), "Start vybere viditelnou úroveň i před skrytým nižším id v JAR");
                call(catalog.getClass(), catalog, "reload");
                check(ids(catalog).size() == 2 && ((List<?>) call(catalog.getClass(), catalog, "errors")).isEmpty(), "Opakované načítání JAR nepřidává duplikáty");
            }
            Files.delete(jar);
            check(!Files.exists(jar), "Načítání nezanechá otevřený JAR soubor");
        } finally {
            try (var files = Files.walk(temp)) {
                for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
            }
        }
        System.out.println("PASS: " + checks + " resource discovery checks (directory, add/edit/rename/delete, validation, JAR without index).");
    }

    private static URLClassLoader loader(Path path) throws Exception {
        return new URLClassLoader(new java.net.URL[]{path.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
    }
    private static Object catalog(ClassLoader loader, Path temp) throws Exception {
        var constructor = loader.loadClass("org.example.LevelCatalog").getDeclaredConstructor(Path.class);
        constructor.setAccessible(true);
        return constructor.newInstance(temp.resolve("external"));
    }
    private static List<?> levels(Object catalog) throws Exception { return (List<?>) call(catalog.getClass(), catalog, "levels"); }
    private static List<String> ids(Object catalog) throws Exception {
        var result = new java.util.ArrayList<String>();
        for (Object level : levels(catalog)) result.add((String) call(level.getClass(), level, "id"));
        return result;
    }
    private static Object call(Class<?> type, Object instance, String name) throws Exception {
        var method = type.getDeclaredMethod(name); method.setAccessible(true); return method.invoke(instance);
    }
    private static byte[] classBytes(String name) throws Exception {
        try (var input = CatalogDiscoveryChecks.class.getResourceAsStream("/" + name + ".class")) {
            if (input == null) throw new AssertionError(name);
            return input.readAllBytes();
        }
    }
    private static void entry(JarOutputStream output, String name, byte[] bytes) throws Exception {
        output.putNextEntry(new JarEntry(name)); output.write(bytes); output.closeEntry();
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
}
