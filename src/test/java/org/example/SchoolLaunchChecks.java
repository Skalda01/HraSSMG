package org.example;

import java.awt.Frame;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/** Spuštění školního okna bez nativních JAR a DLL, včetně vstupů a ukončení. */
public final class SchoolLaunchChecks {
    public static void main(String[] args) throws Exception {
        System.setProperty("sun.java2d.d3d", "false");
        System.setProperty("sun.java2d.opengl", "false");
        Path output = Path.of("target/school-checks");
        Files.createDirectories(output);
        Game game = new Game(Path.of("levels"), output.resolve("programs"), output.resolve("program.java.txt"));
        game.run();
        SwingUtilities.invokeAndWait(() -> {});
        JFrame frame = (JFrame) java.util.Arrays.stream(Frame.getFrames()).filter(Frame::isVisible).findFirst().orElseThrow();
        try {
            SwingUtilities.invokeAndWait(() -> {
                check(!game.textRenderer.isGpu(), "Výchozí okno nepoužívá GPU renderer LWJGL");
                check(SwingWindow.key(KeyEvent.VK_F5) == 294 && SwingWindow.key(KeyEvent.VK_LEFT) == 263, "Mapování kláves");
                game.editor.focused = true;
                String before = game.editor.text;
                game.type("// škola\n");
                check(!before.equals(game.editor.text), "Psaní českého textu");
                game.key(SwingWindow.key(KeyEvent.VK_Z), 2);
                check(before.equals(game.editor.text), "Ctrl+Z");
                game.key(SwingWindow.key(KeyEvent.VK_F3), 0);
                check(game.levelMenu, "Nabídka úrovní");
                game.key(SwingWindow.key(KeyEvent.VK_ESCAPE), 0);
                check(!game.levelMenu, "Zavření nabídky");
                game.key(SwingWindow.key(KeyEvent.VK_F11), 0);
                check(game.fullscreen, "Celá obrazovka");
                game.key(SwingWindow.key(KeyEvent.VK_F11), 0);
                check(!game.fullscreen, "Návrat do okna");
                var canvas = frame.getContentPane();
                BufferedImage image = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_RGB);
                var graphics = image.createGraphics();
                try { canvas.paint(graphics); }
                finally { graphics.dispose(); }
                try { ImageIO.write(image, "png", output.resolve("java2d.png").toFile()); }
                catch (Exception e) { throw new RuntimeException(e); }
                game.editor.setText("public class Main { public static void main(String[] args) { System.out.println(\"Skolni Java funguje\"); } }");
                game.key(SwingWindow.key(KeyEvent.VK_F5), 0);
            });
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
            var finished = new java.util.concurrent.atomic.AtomicBoolean();
            while (!finished.get() && System.nanoTime() < deadline) {
                Thread.sleep(50);
                SwingUtilities.invokeAndWait(() -> finished.set(!game.compiling() && !game.running
                        && game.world.consoleOutput.contains("Skolni Java funguje")));
            }
            check(finished.get(), "F5 přeloží a spustí studentský program i bez LWJGL DLL: " + game.status);
        } finally {
            SwingUtilities.invokeAndWait(() -> frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING)));
        }
        check(Files.isRegularFile(output.resolve("program.java.txt")), "Uložení při ukončení");
        check(!frame.isDisplayable(), "Ukončení okna");
        System.out.println("SchoolLaunchChecks: OK (bez nativních knihoven LWJGL)");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
