package org.example;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/** Ověření skutečného řízení programu a UI, okno zůstává skryté. */
public final class LiveGameChecks {
    public static void main(String[] args) throws Exception {
        var directory = Files.createTempDirectory("hrassmg-live-game-check-");
        Game game = new Game(directory.resolve("levels"), directory.resolve("programs"), directory.resolve("first.txt"));
        var init = method("init", boolean.class);
        var update = method("update");
        var execute = method("execute");
        var render = method("render");
        var key = method("key", int.class, int.class);
        var session = Game.class.getDeclaredField("session"); session.setAccessible(true);
        try {
            init.invoke(game, false);
            Level endless = game.levels.levels().stream().filter(Level::endless).findFirst().orElseThrow();
            game.activateLevel(endless);
            game.editor.setText(CodeEditor.starter(endless).replace("while (true) {",
                    "if (tileType() != TileType.FLOOR || cargoType() != null) throw new IllegalStateException(\"Start\");\n"
                    + "        if (robotCoordinatesX() != 0 || robotCoordinatesY() != 0) throw new IllegalStateException(\"Start coordinates\");\n"
                    + "        presunNa(1,0);\n"
                    + "        if (robotCoordinatesX() != 1 || robotCoordinatesY() != 0) throw new IllegalStateException(\"Inbound coordinates\");\n"
                    + "        if (tileType() != TileType.INBOUND || shelfType() != null) throw new IllegalStateException(\"Inbound\");\n"
                    + "        presunNa(0,1);\n"
                    + "        if (robotCoordinatesX() != 0 || robotCoordinatesY() != 1) throw new IllegalStateException(\"Dock coordinates\");\n"
                    + "        if (tileType() != TileType.DOCK) throw new IllegalStateException(\"Dock\");\n"
                    + "        while (true) {"));
            key.invoke(game, GLFW_KEY_F5, 0);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            while (session.get(game) == null) {
                update.invoke(game);
                if (System.nanoTime() > deadline || game.status.startsWith("Chyba")) throw new AssertionError(game.status);
                Thread.sleep(5);
            }
            long expectedDelivered = 0, previousOrder = 0;
            while (game.world.completedOrders < 3) {
                int orderSize = game.world.totalGoal();
                execute.invoke(game);
                if (game.world.completedOrders != previousOrder) { expectedDelivered += orderSize; previousOrder = game.world.completedOrders; }
                if (System.nanoTime() > deadline || session.get(game) == null) throw new AssertionError(game.status);
                Thread.sleep(1);
            }
            if (!game.running || game.world.totalDelivered != expectedDelivered) throw new AssertionError("Hra nepokračuje po dalších zakázkách");
            key.invoke(game, GLFW_KEY_F5, 0);
            int x = game.world.x, y = game.world.y;
            for (int i=0;i<5;i++) update.invoke(game);
            if (game.running || x != game.world.x || y != game.world.y) throw new AssertionError("Pauza posunula robota");
            var index = Game.class.getDeclaredField("commandIndex"); index.setAccessible(true);
            int before = index.getInt(game);
            int beforeSteps = game.world.steps;
            key.invoke(game, GLFW_KEY_F6, 0);
            for (int i=0;i<20;i++) { update.invoke(game); Thread.sleep(1); }
            if (game.running || index.getInt(game) - before > 1 || game.world.steps - beforeSteps > 1
                    || index.getInt(game) == before && game.world.steps == beforeSteps) throw new AssertionError("F6 neprovedlo právě jeden krok nebo dotaz");
            x = game.world.x; y = game.world.y;
            key.invoke(game, GLFW_KEY_F6, 0);
            for (int i=0;i<20;i++) { update.invoke(game); Thread.sleep(1); }
            if (Math.abs(x - game.world.x) + Math.abs(y - game.world.y) > 1) throw new AssertionError("Krokování presunu nesmí posunout více než jedno pole");
            key.invoke(game, GLFW_KEY_F5, 0);
            if (!game.running || game.world.completedOrders != 3) throw new AssertionError("F5 neobnovilo původní běh");
            game.selectTab(Game.EditorTab.ASSIGNMENT);
            render.invoke(game);
            glFinish();
            if (glGetError() != GL_NO_ERROR) throw new AssertionError("Vykreslení nekonečné úrovně");
            screenshot("target/endless-preview.png");
            game.toggleLevelMenu(); render.invoke(game);
            if (glGetError() != GL_NO_ERROR) throw new AssertionError("Vykreslení nabídky úrovní");
            screenshot("target/endless-level-menu.png");
            game.toggleLevelMenu();
            key.invoke(game, GLFW_KEY_R, GLFW_MOD_CONTROL);
            if (session.get(game) != null || game.running || game.world.completedOrders != 0 || !game.world.level.equals(endless))
                throw new AssertionError("Reset neukončil proces a neobnovil mapu");
            game.selectTab(Game.EditorTab.JAVA);
            game.editor.setText(CodeEditor.wrapLegacyBody("move(Direction.RIGHT);"));
            key.invoke(game, GLFW_KEY_F5, 0);
            deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (session.get(game) == null) {
                update.invoke(game); Thread.sleep(5);
                if (System.nanoTime() > deadline) throw new AssertionError(game.status);
            }
            game.activateLevel(LevelCatalog.defaultLevel());
            if (session.get(game) != null || game.running) throw new AssertionError("Změna mapy neukončila starý program");
            System.out.println("PASS: live Game integration, 3 orders, F5 pause/resume, F6 query/movement, reset, level switch, OpenGL previews.");
        } finally {
            method("closeSession").invoke(game);
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

    private static java.lang.reflect.Method method(String name, Class<?>... args) throws Exception {
        var method = Game.class.getDeclaredMethod(name, args); method.setAccessible(true); return method;
    }
    private static void screenshot(String file) throws Exception {
        int[] width = new int[1], height = new int[1];
        glfwGetFramebufferSize(glfwGetCurrentContext(),width,height);
        var pixels = BufferUtils.createByteBuffer(width[0] * height[0] * 4);
        glReadPixels(0,0,width[0],height[0],GL_RGBA,GL_UNSIGNED_BYTE,pixels);
        var image = new BufferedImage(width[0],height[0],BufferedImage.TYPE_INT_RGB);
        for (int y=0;y<height[0];y++) for (int x=0;x<width[0];x++) {
            int offset = (y*width[0]+x)*4;
            image.setRGB(x,height[0]-y-1,((pixels.get(offset)&255)<<16)|((pixels.get(offset+1)&255)<<8)|(pixels.get(offset+2)&255));
        }
        ImageIO.write(image,"png",new java.io.File(file));
    }
}
