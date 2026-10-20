package org.example;

import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.lwjgl.opengl.GL;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/** Skutečné časování, pauza v animaci, krokování, reset a GPU vykreslení. */
public final class LiveSortingChecks {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var directory = Files.createTempDirectory("hrassmg-live-sort-");
        var game = new Game(directory.resolve("levels"), directory.resolve("programs"), directory.resolve("first.txt"));
        var session = Game.class.getDeclaredField("session"); session.setAccessible(true);
        var commands = Game.class.getDeclaredField("commandIndex"); commands.setAccessible(true);
        try {
            invoke(game, "init", new Class<?>[]{boolean.class}, false);
            Level level = game.levels.levels().stream().filter(Level::sorting).findFirst().orElseThrow();
            game.activateLevel(level); game.selectTab(Game.EditorTab.JAVA);
            check(game.sorting.length() == 12 && !game.levelComplete(), "Aktivace zobrazí nesetříděné pole");
            game.editor.setText(SortingChecks.solution(level));
            key(game, GLFW_KEY_F5, 0);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(35);
            while (!game.sorting.animating()) tick(game, deadline);
            check(game.sorting.reads > 0 && game.sorting.writes == 1, "Živý proces zahájil první animaci");
            key(game, GLFW_KEY_ESCAPE, 0);
            double progress = game.sorting.progress;
            int before = commands.getInt(game);
            for (int i = 0; i < 8; i++) { Thread.sleep(8); invoke(game, "update"); }
            check(game.sorting.progress == progress && commands.getInt(game) == before, "Pauza zmrazí výměnu i hráčův program");
            key(game, GLFW_KEY_F6, 0);
            while (game.sorting.animating()) tick(game, deadline);
            for (int i = 0; i < 5; i++) tick(game, deadline);
            check(!game.running && commands.getInt(game) == before + 1 && game.sorting.writes == 1,
                    "F6 dokončí právě jednu rozpracovanou operaci");
            game.setSpeedMultiplier(10);
            key(game, GLFW_KEY_F5, 0);
            while (session.get(game) != null) tick(game, deadline);
            check(game.sorting.complete && game.levelComplete() && !game.running, "Herní smyčka potvrdí seřazení až po skončení programu");
            check(game.sorting.reads > 132, "Běh nevynechá žádné porovnání");
            invoke(game, "render"); glFinish();
            check(glGetError() == GL_NO_ERROR, "Canvas a texty se vykreslí na GPU");
            screenshot("target/bubblesort-completed-gpu.png");
            String source = game.editor.text;
            key(game, GLFW_KEY_R, GLFW_MOD_CONTROL);
            check(!game.levelComplete() && game.sorting.value(0) == 8 && game.sorting.writes == 0
                    && game.editor.text.equals(source), "Reset obnoví původní pole a ponechá kód");
            key(game, GLFW_KEY_F5, 0);
            while (!game.sorting.animating()) tick(game, deadline);
            key(game, GLFW_KEY_ESCAPE, 0);
            invoke(game, "render"); glFinish(); screenshot("target/bubblesort-swap-gpu.png");
            game.selectTab(Game.EditorTab.DOCUMENTATION); game.openDocumentationChapter(0);
            check(!game.running && game.documentationChapter != null, "Dokumentace funguje i nad canvasem");
            game.closeDocumentation();
            game.activateLevel(LevelCatalog.defaultLevel());
            check(session.get(game) == null && !game.world.level.sorting(), "Přechod zpět na robotí level ukončí třídění");
            game.activateLevel(level);
            check(game.editor.text.equals(source) && !game.sorting.complete, "Návrat načte uložený kód a nové pole");
            game.editor.setText(SortingChecks.program(level, "pole[999] = 1;"));
            key(game, GLFW_KEY_F5, 0);
            do { tick(game, deadline); } while (game.compiling() || session.get(game) != null);
            check(game.status.contains("999") && game.activeLine == 4 && game.sorting.writes == 0, "Chybný index označí řádek editoru a nezmění pole");
            previews(level);
            System.out.println("PASS: " + checks + " live sorting checks (GPU, pause during swap, step, completion, reset, persistence, errors, responsive previews).");
        } finally {
            invoke(game, "closeSession");
            var compiler = Game.class.getDeclaredField("compiler"); compiler.setAccessible(true);
            ((java.util.concurrent.ExecutorService) compiler.get(game)).shutdownNow();
            game.textRenderer.close();
            long window = glfwGetCurrentContext();
            if (window != 0) { org.lwjgl.glfw.Callbacks.glfwFreeCallbacks(window); glfwDestroyWindow(window); }
            GL.setCapabilities(null); glfwTerminate();
            try (var files = Files.walk(directory)) {
                for (var path : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }
    static void previews(Level level) throws Exception {
        // Neinitializovaný renderer používá pro statické obrázky Java2D, nikoli GPU frontu.
        Game game = new Game();
        game.world.setLevel(level);
        game.selectTab(Game.EditorTab.JAVA);
        game.editor.setText(SortingChecks.solution(level));
        game.sorting.reset(level); game.sorting.execute(new RobotProgram.Command("sortValue", 0, level.array().getFirst(), 4));
        for (int[] size : new int[][]{{1366,768,1},{640,440,1},{800,600,1},{3840,2160,2}}) {
            game.layout = new UiLayout(size[0], size[1], size[2]);
            game.editorView = false; game.editor.visibleRows = game.layout.visibleRows();
            var image = new BufferedImage(size[0], size[1], BufferedImage.TYPE_INT_RGB);
            var graphics = image.createGraphics(); graphics.scale(game.layout.scale, game.layout.scale);
            game.dashboard.draw(graphics, game); graphics.dispose();
            ImageIO.write(image, "png", new java.io.File("target/bubblesort-" + size[0] + ".png"));
        }
    }
    static void tick(Game game, long deadline) throws Exception {
        invoke(game, "update"); Thread.sleep(2);
        if (System.nanoTime() > deadline) throw new AssertionError(game.status);
    }
    static void key(Game game, int key, int mods) throws Exception { invoke(game, "key", new Class<?>[]{int.class,int.class}, key, mods); }
    static void invoke(Game game, String name) throws Exception { invoke(game, name, new Class<?>[0]); }
    static void invoke(Game game, String name, Class<?>[] types, Object... args) throws Exception {
        var method = Game.class.getDeclaredMethod(name, types); method.setAccessible(true); method.invoke(game, args);
    }
    static void screenshot(String file) throws Exception {
        int[] width = new int[1], height = new int[1];
        glfwGetFramebufferSize(glfwGetCurrentContext(), width, height);
        var pixels = org.lwjgl.BufferUtils.createByteBuffer(width[0] * height[0] * 4);
        glReadPixels(0, 0, width[0], height[0], GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        var image = new BufferedImage(width[0], height[0], BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height[0]; y++) for (int x = 0; x < width[0]; x++) {
            int offset = (y * width[0] + x) * 4;
            image.setRGB(x, height[0] - y - 1, ((pixels.get(offset) & 255) << 16) | ((pixels.get(offset + 1) & 255) << 8) | (pixels.get(offset + 2) & 255));
        }
        ImageIO.write(image, "png", new java.io.File(file));
    }
    static void check(boolean result, String message) { if (!result) throw new AssertionError(message); checks++; }
}
