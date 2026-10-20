package org.example;

import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.lwjgl.opengl.GL;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

public final class LiveBinarySearchChecks {
    public static void main(String[] args) throws Exception {
        var directory = Files.createTempDirectory("hrassmg-live-search-");
        var game = new Game(directory.resolve("levels"), directory.resolve("programs"), directory.resolve("first.txt"));
        var session = Game.class.getDeclaredField("session"); session.setAccessible(true);
        var commands = Game.class.getDeclaredField("commandIndex"); commands.setAccessible(true);
        try {
            LiveSortingChecks.invoke(game, "init", new Class<?>[]{boolean.class}, false);
            Level level = game.levels.levels().stream().filter(Level::searching).findFirst().orElseThrow();
            game.activateLevel(level); game.selectTab(Game.EditorTab.JAVA);
            if (game.levelComplete()) throw new AssertionError("Seřazený vstup není splněný search level");
            game.editor.setText(BinarySearchChecks.solution(level));
            LiveSortingChecks.key(game, GLFW_KEY_F5, 0);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25);
            while (!game.sorting.animating()) LiveSortingChecks.tick(game, deadline);
            if (game.sorting.probe != 7 || game.sorting.searchLow != 8 || game.sorting.searchHigh != 14) throw new AssertionError("První skutečné půlení");
            LiveSortingChecks.key(game, GLFW_KEY_ESCAPE, 0);
            double progress = game.sorting.progress;
            int before = commands.getInt(game);
            for (int i=0;i<8;i++) { Thread.sleep(5); LiveSortingChecks.invoke(game, "update"); }
            if (game.sorting.progress != progress || commands.getInt(game) != before) throw new AssertionError("Pauza musí zmrazit hledání");
            LiveSortingChecks.invoke(game, "render"); glFinish();
            LiveSortingChecks.screenshot("target/binary-search-step-gpu.png");
            LiveSortingChecks.key(game, GLFW_KEY_F6, 0);
            while (game.sorting.animating()) LiveSortingChecks.tick(game, deadline);
            if (game.running || game.sorting.reads != 1 || commands.getInt(game) != before + 1) throw new AssertionError("F6 dokončí jednu kontrolu");
            game.setSpeedMultiplier(10); LiveSortingChecks.key(game, GLFW_KEY_F5, 0);
            while (session.get(game) != null) LiveSortingChecks.tick(game, deadline);
            if (!game.levelComplete() || game.sorting.reportedResult != 12 || game.sorting.reads != 4) throw new AssertionError(game.status);
            LiveSortingChecks.invoke(game, "render"); glFinish();
            if (glGetError() != GL_NO_ERROR) throw new AssertionError("OpenGL search canvas");
            LiveSortingChecks.screenshot("target/binary-search-completed-gpu.png");
            String source = game.editor.text;
            LiveSortingChecks.key(game, GLFW_KEY_R, GLFW_MOD_CONTROL);
            if (game.levelComplete() || game.sorting.searchLow != 0 || game.sorting.searchHigh != 14
                    || !source.equals(game.editor.text)) throw new AssertionError("Reset zachová kód a obnoví hledání");
            game.activateLevel(game.levels.levels().stream().filter(Level::sorting).findFirst().orElseThrow());
            if (session.get(game) != null || game.world.level.searching()) throw new AssertionError("Přepnutí zpět na bubble sort");
            previews(level);
            System.out.println("PASS: live binary search, actual probes, pause, step, completion, reset, level switch, GPU and responsive previews.");
        } finally {
            LiveSortingChecks.invoke(game, "closeSession");
            var compiler = Game.class.getDeclaredField("compiler"); compiler.setAccessible(true);
            ((java.util.concurrent.ExecutorService) compiler.get(game)).shutdownNow();
            game.textRenderer.close();
            long window = glfwGetCurrentContext();
            if (window != 0) { org.lwjgl.glfw.Callbacks.glfwFreeCallbacks(window); glfwDestroyWindow(window); }
            GL.setCapabilities(null); glfwTerminate();
            try (var files = Files.walk(directory)) { for (var path : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
        }
    }
    static void previews(Level level) throws Exception {
        Game game = new Game(); game.world.setLevel(level); game.sorting.reset(level);
        game.editor.setText(BinarySearchChecks.solution(level));
        game.selectTab(Game.EditorTab.JAVA);
        game.sorting.execute(new RobotProgram.Command("sortValue", 7, 23, 9)); game.sorting.advance(.25, .5);
        for (int[] size : new int[][]{{1366,768,1},{640,440,1},{800,600,1},{3840,2160,2}}) {
            game.layout = new UiLayout(size[0],size[1],size[2]); game.editorView = false;
            var image = new BufferedImage(size[0],size[1],BufferedImage.TYPE_INT_RGB);
            var graphics = image.createGraphics(); graphics.scale(game.layout.scale,game.layout.scale);
            game.dashboard.draw(graphics,game); graphics.dispose();
            ImageIO.write(image,"png",new java.io.File("target/binary-search-"+size[0]+".png"));
        }
    }
}
