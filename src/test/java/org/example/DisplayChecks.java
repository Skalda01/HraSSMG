package org.example;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Rozložení a rasterizace při různých velikostech a simulovaném DPI, bez otevírání okna. */
public final class DisplayChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        int[][] resolutions = {{640,440}, {800,600}, {1024,768}, {1366,768}, {1920,1080},
                {2560,1440}, {3840,2160}, {3440,1440}, {900,1440}};
        double[] scales = {1, 1.25, 1.5, 2};
        var levels = new LevelCatalog(Path.of("levels")).levels();
        for (int[] size : resolutions) for (double dpi : scales) {
            UiLayout l = new UiLayout(size[0], size[1], dpi);
            check(l.width >= 639 && l.height >= 439, "Rozhraní se nesmí zmenšit pod použitelné minimum");
            for (Rectangle rectangle : new Rectangle[]{l.board, l.editor, l.code, l.play, l.step, l.reset, l.resetCode,
                    l.source, l.assignment, l.documentation, l.speed, l.fullscreen, l.levelButton, l.nextLevel, l.levelDialog,
                    l.levelList, l.levelRobot, l.levelMaterials, l.levelBack, l.levelActivate, l.levelReload, l.levelClose}) {
                check(rectangle.width > 0 && rectangle.height > 0, "Platný rozměr prvku");
                check(rectangle.x >= 0 && rectangle.y >= 0 && rectangle.x + rectangle.width <= l.width
                        && rectangle.y + rectangle.height <= l.height, "Prvek musí zůstat v okně");
            }
            check(l.editor.contains(l.code) && l.editor.contains(l.play), "Editor obsahuje text a tlačítka");
            check(l.board.y == l.editor.y && l.board.height == l.editor.height, "Sklad a editor mají stejnou výšku i horní okraj");
            check(l.editor.contains(l.speed), "Posuvník je uvnitř panelu editoru");
            check(l.board.contains(l.nextLevel), "Tlačítko další úrovně se vejde nad mapu");
            check(l.documentationWindow.equals(l.board) && l.documentationWindow.contains(l.documentationContent)
                    && l.documentationWindow.contains(l.documentationBack) && l.documentationWindow.contains(l.documentationClose), "Kapitola dokumentace vyplňuje pouze panel skladu");
            check(!l.source.intersects(l.assignment) && !l.assignment.intersects(l.documentation), "Tři záložky se nepřekrývají");
            check(l.editor.contains(l.resetCode) && !l.code.intersects(l.resetCode)
                    && !l.code.intersects(l.play) && !l.play.intersects(l.step) && !l.step.intersects(l.reset)
                    && !l.reset.intersects(l.resetCode), "Ovládací prvky včetně resetu kódu se nepřekrývají");
            check(!l.board.intersects(l.orders), "Objednávky se nepřekrývají se skladem");
            check(l.compact || !l.editor.intersects(l.board), "Dva sloupce se nepřekrývají");
            for (Level level : levels) {
                var geometry = l.warehouse(level);
                check(l.board.contains(geometry.sidebar()), "Inventář zůstává uvnitř skladu při všech rozlišeních");
                check(geometry.orderPanel().height == geometry.inventoryPanel().height
                        && geometry.orderPanel().width == geometry.inventoryPanel().width
                        && geometry.orderPanel().y < geometry.inventoryPanel().y
                        && !geometry.orderPanel().intersects(geometry.inventoryPanel())
                        && geometry.sidebar().contains(geometry.orderPanel()) && geometry.sidebar().contains(geometry.inventoryPanel()),
                        "Objednávka a sklad jsou stejně vysoké a pod sebou vlevo od mapy");
                check(geometry.cell() > 0 && geometry.gridX() - 24 >= geometry.sidebar().getMaxX(), "Inventář nepřekrývá mapu ani souřadnice");
                check(geometry.gridX() + geometry.cell() * level.width() + 8 <= l.board.getMaxX()
                        && geometry.gridY() + geometry.cell() * level.height() + 8 <= l.board.getMaxY(), "Mapa s levým panelem se vejde do skladu");
            }
            check(l.levelDialog.contains(l.levelList) && l.levelDialog.contains(l.levelActivate), "Výběr úrovně obsahuje seznam a tlačítka");
            check(!l.levelActivate.intersects(l.levelReload) && !l.levelReload.intersects(l.levelClose), "Tlačítka výběru úrovní se nepřekrývají");
            for (int speed = 1; speed <= 10; speed++)
                check(l.speedAt(l.speed.x + (speed - 1) * l.speed.width / 9.0) == speed, "Posuvník musí vybrat přesnou rychlost");
            check(l.speedAt(l.speed.x - 100) == 1 && l.speedAt(l.speed.x + l.speed.width + 100) == 10, "Tažení mimo posuvník drží rozsah 1–10");
            double physicalX = l.play.getCenterX() * l.scale, physicalY = l.play.getCenterY() * l.scale;
            // Screen coordinates mohou mít vůči framebufferu poměr 1:1 i 1:2.
            for (double ratio : new double[]{1, 2}) {
                var point = l.pointer(physicalX / ratio, physicalY / ratio, (int) (size[0] / ratio), (int) (size[1] / ratio));
                check(l.play.contains(point), "Mapování myši musí trefit skutečně vykreslené tlačítko");
            }
        }
        Path previews = Path.of("target", "display-previews");
        Files.createDirectories(previews);
        preview(previews, "notebook-1366x768", 1366, 768, 1, false);
        preview(previews, "notebook-125-percent", 1366, 768, 1.25, false);
        preview(previews, "compact-editor", 1024, 768, 1.25, true);
        preview(previews, "compact-help", 800, 600, 1, true);
        preview(previews, "assignment", 1366, 768, 1, true);
        preview(previews, "documentation", 1366, 768, 1, true);
        preview(previews, "desktop-150-percent", 1920, 1080, 1.5, false);
        preview(previews, "4k-200-percent", 3840, 2160, 2, false);
        preview(previews, "ultrawide", 3440, 1440, 1, false);
        preview(previews, "level-menu", 1366, 768, 1, false);
        preview(previews, "level-menu-compact", 800, 600, 1, false);
        preview(previews, "second-level", 1366, 768, 1, false);
        preview(previews, "assignment-code", 1366, 768, 1, true);
        preview(previews, "assignment-code-compact", 800, 600, 1, true);
        preview(previews, "endless-warehouse", 1366, 768, 1, false);
        preview(previews, "endless-compact", 800, 600, 1, false);
        preview(previews, "completed-level", 1366, 768, 1, false);
        preview(previews, "completed-compact", 800, 600, 1, false);
        preview(previews, "documentation-chapter", 1366, 768, 1, false);
        preview(previews, "documentation-chapter-compact", 800, 600, 1, false);
        preview(previews, "documentation-chapter-4k", 3840, 2160, 2, false);
        Game game = new Game();
        double normal = game.stepInterval();
        game.setSpeedMultiplier(10);
        check(Math.abs(normal / game.stepInterval() - 10) < 0.001, "Rychlost 10× má desetkrát kratší interval");
        game.setSpeedMultiplier(0);
        check(game.speedMultiplier == 1, "Dolní mez rychlosti");
        game.setSpeedMultiplier(20);
        check(game.speedMultiplier == 10, "Horní mez rychlosti");
        String source = game.editor.text;
        game.selectTab(Game.EditorTab.ASSIGNMENT);
        game.selectTab(Game.EditorTab.DOCUMENTATION);
        game.selectTab(Game.EditorTab.JAVA);
        check(game.editor.text.equals(source), "Přepnutí záložek zachová Java program");
        System.out.println("PASS: " + checks + " display checks, 36 size/DPI combinations, speed, tabs, level selector, 21 previews.");
    }

    private static void preview(Path dir, String name, int width, int height, double dpi, boolean editorView) throws Exception {
        Game game = new Game();
        game.layout = new UiLayout(width, height, dpi);
        game.editor.visibleRows = game.layout.visibleRows();
        game.editorView = editorView;
        if (name.endsWith("help") || name.equals("documentation")) game.selectTab(Game.EditorTab.DOCUMENTATION);
        if (name.equals("assignment")) game.selectTab(Game.EditorTab.ASSIGNMENT);
        if (name.startsWith("level-menu")) game.toggleLevelMenu();
        if (name.equals("second-level") || name.startsWith("assignment-code")) {
            game.world.setLevel(game.levels.levels().stream().filter(l -> l.id().equals("02-repeated-delivery")).findFirst().orElseThrow());
            game.robotX = game.world.x; game.robotY = game.world.y;
            game.selectTab(Game.EditorTab.ASSIGNMENT);
        }
        if (name.startsWith("endless-")) {
            game.world.setLevel(SortingChecks.bundled(Level::endless));
            game.robotX = game.world.x; game.robotY = game.world.y;
            game.selectTab(Game.EditorTab.ASSIGNMENT);
        }
        if (name.startsWith("completed-")) {
            String solution=CodeEditor.wrapLegacyBody("""
                    move(Direction.RIGHT); move(Direction.RIGHT); pick();
                    move(Direction.LEFT); move(Direction.LEFT); move(Direction.LEFT); move(Direction.LEFT); drop();
                    """);
            for (var command : new RobotProgram(game.world.level.allowedKeywords()).compile(solution)) {
                int guard = 0;
                while (!game.world.execute(command)) if (++guard > 100) throw new AssertionError("Program nedokončil příkaz");
            }
            game.robotX = game.world.x; game.robotY = game.world.y;
            game.status = "Úroveň splněna! Pokračuj tlačítkem Další level nebo F5.";
        }
        if (name.startsWith("documentation-chapter")) {
            game.selectTab(Game.EditorTab.DOCUMENTATION);
            game.openDocumentationChapter(0);
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.scale(game.layout.scale, game.layout.scale);
        game.dashboard.draw(graphics, game);
        if (name.startsWith("assignment-code")) {
            game.dashboard.scrollPage(650);
            game.dashboard.draw(graphics, game);
        }
        graphics.dispose();
        ImageIO.write(image, "png", dir.resolve(name + ".png").toFile());
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
