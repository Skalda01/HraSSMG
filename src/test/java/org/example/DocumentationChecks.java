package org.example;

import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import static org.lwjgl.glfw.GLFW.*;

public final class DocumentationChecks {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var catalog = new DocumentationCatalog();
        check(catalog.chapters().size() >= 8 && catalog.errors().isEmpty(), "Společná dokumentace načte kapitoly bez indexu");
        var parsed = DocumentationChapter.read(new StringReader("id=test\ntitle=Česká kapitola\norder=12\ncontent=# Nadpis\\n\\nText `int x = 0`\n"), "fallback");
        check(parsed.content().equals("# Nadpis\n\nText `int x = 0`") && parsed.order() == 12, "UTF-8, více řádků a značky kódu se zachovají");
        var temp = Files.createTempDirectory("hrassmg-documentation-");
        try {
            Game game = new Game(temp.resolve("levels"), temp.resolve("programs"), temp.resolve("first.txt"));
            game.layout = new UiLayout(1366, 768, 1);
            String code = game.editor.text;
            int steps = game.world.steps, x = game.world.x, y = game.world.y;
            game.selectTab(Game.EditorTab.DOCUMENTATION);
            draw(game);
            var row = game.dashboard.documentationRow(game.layout, 0);
            game.running = true;
            game.click(new Point2D.Double(row.getCenterX(), row.getCenterY()), 0);
            check(game.documentationChapter != null && !game.running && game.tab == Game.EditorTab.DOCUMENTATION,
                    "Kliknutí na položku otevře kapitolu a pozastaví program");
            check(game.layout.documentationWindow.equals(game.layout.board) && !game.layout.documentationWindow.intersects(game.layout.editor)
                            && !game.layout.documentationWindow.intersects(game.layout.orders), "Kapitola nezakrývá editor ani dolní skladové počty");
            draw(game);
            var page = Dashboard.class.getDeclaredField("pageScroll"); page.setAccessible(true);
            var chapter = Dashboard.class.getDeclaredField("chapterScroll"); chapter.setAccessible(true);
            int listScroll = page.getInt(game.dashboard);
            game.dashboard.scrollChapter(200); draw(game);
            check(chapter.getInt(game.dashboard) > 0 && page.getInt(game.dashboard) == listScroll, "Kapitola a seznam se posouvají nezávisle");
            var key = Game.class.getDeclaredMethod("key", int.class, int.class); key.setAccessible(true);
            key.invoke(game, GLFW_KEY_ESCAPE, 0);
            check(game.documentationChapter == null && game.tab == Game.EditorTab.DOCUMENTATION, "Escape vrátí seznam kapitol");
            game.openDocumentationChapter(1);
            var close = game.layout.documentationClose;
            game.click(new Point2D.Double(close.getCenterX(), close.getCenterY()), 0);
            check(game.documentationChapter == null && game.tab == Game.EditorTab.JAVA, "Zavření kapitoly vrátí herní zobrazení");
            check(game.editor.text.equals(code) && game.world.steps == steps && game.world.x == x && game.world.y == y,
                    "Prohlížení dokumentace nezmění program ani sklad");
            var ids = game.documentation.chapters().stream().map(DocumentationChapter::id).toList();
            game.world.setLevel(game.levels.levels().get(1));
            game.selectTab(Game.EditorTab.DOCUMENTATION);
            check(game.documentation.chapters().stream().map(DocumentationChapter::id).toList().equals(ids), "Další úroveň sdílí stejné kapitoly");
            game.layout = new UiLayout(800, 600, 1);
            game.openDocumentationChapter(0); draw(game);
            check(!game.editorView && game.documentationChapter != null, "Malé okno otevře kapitolu v panelu skladu");
            key.invoke(game, GLFW_KEY_ESCAPE, 0);
            check(game.editorView && game.documentationChapter == null, "Malé okno se vrátí do seznamu");

            var jar = temp.resolve("documentation.jar");
            try (var output = new JarOutputStream(Files.newOutputStream(jar))) {
                for (String name : List.of("DocumentationCatalog", "DocumentationChapter", "ResourceConfigs", "ResourceConfigs$Entry")) {
                    String entry = "org/example/" + name + ".class";
                    try (var input = DocumentationChecks.class.getResourceAsStream("/" + entry)) {
                        output.putNextEntry(new JarEntry(entry)); output.write(input.readAllBytes()); output.closeEntry();
                    }
                }
                output.putNextEntry(new JarEntry("documentation/arbitrary-name.properties"));
                output.write("title=Česká kapitola\ncontent=Obsah z JAR\n".getBytes(StandardCharsets.UTF_8)); output.closeEntry();
            }
            try (var loader = new java.net.URLClassLoader(new java.net.URL[]{jar.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
                Class<?> type = loader.loadClass("org.example.DocumentationCatalog");
                var constructor = type.getDeclaredConstructor(); constructor.setAccessible(true);
                var chapters = type.getDeclaredMethod("chapters"); chapters.setAccessible(true);
                check(((List<?>) chapters.invoke(constructor.newInstance())).size() == 1, "Dokumentace se načte i z JAR bez indexu a adresářových záznamů");
            }
        } finally {
            try (var files = Files.walk(temp)) { for (var file : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(file); }
        }
        System.out.println("PASS: " + checks + " documentation checks (configuration, mouse, keyboard, separate scrolling, level sharing, compact view, JAR).");
    }
    private static void draw(Game game) {
        var image = new BufferedImage(game.layout.pixelWidth, game.layout.pixelHeight, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics(); graphics.scale(game.layout.scale, game.layout.scale);
        game.dashboard.draw(graphics, game); graphics.dispose();
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
}
