package org.example;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/** Přibalený JetBrains Mono a skutečné GPU coverage, ořezávání a zakrývání textu. */
public final class TextRendererChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        TextRenderer renderer = null;
        long window = 0;
        try {
            FontBook defaults = new FontBook();
            check(defaults.font(FontBook.Face.UI, 20).canDisplayUpTo("Příliš žluťoučký kůň úpěl ďábelské ódy") == -1, "Výchozí font obsahuje českou diakritiku");
            check(defaults.source(FontBook.Face.UI).font().getPSName().startsWith("JetBrainsMono"), "Používá se přibalený JetBrains Mono");
            check(defaults.source(FontBook.Face.UI) == defaults.source(FontBook.Face.BOLD)
                    && defaults.source(FontBook.Face.UI) == defaults.source(FontBook.Face.CODE), "Celé rozhraní používá jediný zdroj fontu");
            String oldProperty = System.getProperty("hrassmg.ui.font");
            try {
                System.setProperty("hrassmg.ui.font", "C:/external.ttf");
                check(new FontBook().source(FontBook.Face.UI).name().equals(FontBook.FONT_RESOURCE), "Externí cesta v JVM parametru nezmění font");
            } finally {
                if (oldProperty == null) System.clearProperty("hrassmg.ui.font"); else System.setProperty("hrassmg.ui.font", oldProperty);
            }
            renderer = new TextRenderer(defaults);
            BufferedImage canvas = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
            var g = canvas.createGraphics();
            check(renderer.measure(g, FontBook.Face.CODE, 20, "iiii") == renderer.measure(g, FontBook.Face.CODE, 20, "MMMM"), "Editor má pevné buňky i s proporcionálním fontem");
            if (!glfwInit()) throw new AssertionError("GLFW init failed");
            glfwDefaultWindowHints(); glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3); glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
            window = glfwCreateWindow(640, 480, "font checks", 0, 0);
            if (window == 0) throw new AssertionError("Hidden window failed");
            glfwMakeContextCurrent(window); GL.createCapabilities(); renderer.create();
            check(renderer.isGpu(), "Text používá GPU renderer");
            check(Math.abs(renderer.measure(g, FontBook.Face.CODE, 20, "iiii") - renderer.measure(g, FontBook.Face.CODE, 20, "MMMM")) < 0.001, "GPU editor má shodné pevné buňky");
            glViewport(0, 0, 640, 480); glClearColor(0, 0, 0, 1);
            renderer.beginFrame(640, 480);
            g.setClip(20, 20, 120, 40);
            renderer.draw(g, FontBook.Face.UI, 24, "Příliš žluťoučký kůň", 20, 48, Color.WHITE);
            renderer.prepare(); glClear(GL_COLOR_BUFFER_BIT); renderer.render(640, 480);
            ByteBuffer pixels = pixels();
            check(count(pixels, new Rectangle(20,20,120,40)) > 200, "STB atlas vykreslí skutečné glyphy");
            check(count(pixels, new Rectangle(140,0,500,480)) == 0, "GL scissor nepustí text za pravý okraj");
            check(count(pixels, new Rectangle(0,0,20,480)) == 0, "GL scissor nepustí text za levý okraj");
            renderer.beginFrame(640, 480); g.setClip(null);
            renderer.draw(g, FontBook.Face.UI, 24, "Příliš žluťoučký kůň", 20, 48, Color.WHITE);
            Rectangle cover = new Rectangle(20, 20, 100, 50);
            renderer.occlude(g, cover); renderer.prepare(); glClear(GL_COLOR_BUFFER_BIT); renderer.render(640,480);
            pixels = pixels();
            check(count(pixels, cover) == 0, "Našeptávač a další panely správně zakryjí text pod sebou");
            check(count(pixels, new Rectangle(120,20,400,50)) > 100, "Nezakrytý zbytek textu se zachová");
            renderer.beginFrame(640,480); g.scale(2,2);
            renderer.draw(g, FontBook.Face.CODE, 15, "Main: čřěšžýáíéůúťďň", 10, 60, Color.WHITE);
            renderer.prepare(); glClear(GL_COLOR_BUFFER_BIT); renderer.render(640,480);
            check(count(pixels(), new Rectangle(0,80,640,60)) > 500, "Dvojnásobné DPI použije odpovídající atlas");
            check(glGetError() == GL_NO_ERROR, "Bez OpenGL chyb");
            g.dispose();
            System.out.println("PASS: " + checks + " font checks (bundled JetBrains Mono, Czech, fixed cells, GPU, clipping, occlusion, DPI).");
        } finally {
            if (renderer != null) renderer.close();
            if (window != 0) glfwDestroyWindow(window);
            GL.setCapabilities(null); glfwTerminate();
        }
    }

    private static ByteBuffer pixels() {
        ByteBuffer pixels = BufferUtils.createByteBuffer(640*480*4);
        glReadPixels(0,0,640,480,GL_RGBA,GL_UNSIGNED_BYTE,pixels); return pixels;
    }
    private static int count(ByteBuffer pixels, Rectangle region) {
        int count = 0;
        for (int y=region.y;y<region.y+region.height;y++) for(int x=region.x;x<region.x+region.width;x++) {
            int offset=((479-y)*640+x)*4;
            if ((pixels.get(offset)&255)>16 || (pixels.get(offset+1)&255)>16 || (pixels.get(offset+2)&255)>16) count++;
        }
        return count;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
}
