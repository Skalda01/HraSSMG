package org.example;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.awt.geom.Point2D;
import javax.swing.*;
import static java.awt.event.KeyEvent.*;
import static org.lwjgl.glfw.GLFW.*;

/** Okno a vstupy z JDK; nepoužívá GLFW, OpenGL ani nativní knihovny LWJGL. */
final class SwingWindow {
    private final Game game;
    private final JFrame frame = new JFrame("WORKER // OS — Robotická směna");
    private final JPanel canvas;
    private final Timer timer;
    private Rectangle restoreBounds;
    private char highSurrogate;

    SwingWindow(Game game) {
        this.game = game;
        canvas = new JPanel() {
            @Override protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    double dpi = Math.max(g.getTransform().getScaleX(), g.getTransform().getScaleY());
                    game.paintJava2D(g, getWidth(), getHeight(), dpi);
                } finally { g.dispose(); }
            }
        };
        canvas.setFocusable(true);
        canvas.setFocusTraversalKeysEnabled(false);
        canvas.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent event) {
                game.key(key(event.getKeyCode()), modifiers(event));
            }
            @Override public void keyTyped(KeyEvent event) {
                char value = event.getKeyChar();
                if (value == CHAR_UNDEFINED || Character.isISOControl(value)
                        || ((event.isControlDown() || event.isMetaDown()) && !event.isAltGraphDown())) return;
                if (Character.isHighSurrogate(value)) { highSurrogate = value; return; }
                if (Character.isLowSurrogate(value) && highSurrogate != 0) {
                    game.type(new String(new char[]{highSurrogate, value}));
                } else game.type(String.valueOf(value));
                highSurrogate = 0;
            }
        });
        canvas.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                canvas.requestFocusInWindow();
                if (SwingUtilities.isLeftMouseButton(event)) game.click(point(event), modifiers(event));
            }
            @Override public void mouseReleased(MouseEvent event) { game.releasePointer(); }
        });
        canvas.addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseDragged(MouseEvent event) { game.drag(point(event)); }
        });
        canvas.addMouseWheelListener(event -> game.scroll(point(event), -event.getPreciseWheelRotation()));
        canvas.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent event) { highSurrogate = 0; game.lostFocus(); }
        });
        timer = new Timer(16, event -> {
            game.update();
            if ((frame.getExtendedState() & Frame.ICONIFIED) == 0) canvas.repaint();
        });
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { close(); }
        });
        frame.setContentPane(canvas);
    }

    void open() {
        Rectangle available = frame.getGraphicsConfiguration().getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(frame.getGraphicsConfiguration());
        frame.setSize(Math.min(1440, available.width - insets.left - insets.right - 32),
                Math.min(900, available.height - insets.top - insets.bottom - 32));
        frame.setMinimumSize(new Dimension(Math.min(640, frame.getWidth()), Math.min(440, frame.getHeight())));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        canvas.requestFocusInWindow();
        timer.start();
    }

    void close() {
        timer.stop();
        try { game.closeDesktop(); } finally { frame.dispose(); }
    }

    void fullscreen(boolean enabled) {
        if (enabled) restoreBounds = frame.getBounds();
        frame.dispose();
        frame.setUndecorated(enabled);
        if (enabled) {
            frame.setExtendedState(Frame.NORMAL);
            frame.setBounds(frame.getGraphicsConfiguration().getBounds());
        } else if (restoreBounds != null) {
            frame.setExtendedState(Frame.NORMAL);
            frame.setBounds(restoreBounds);
        }
        frame.setVisible(true);
        canvas.requestFocusInWindow();
    }

    private Point2D.Double point(MouseEvent event) {
        return game.desktopPointer(event.getX(), event.getY(), canvas.getWidth(), canvas.getHeight());
    }

    static int modifiers(InputEvent event) {
        return (event.isShiftDown() ? GLFW_MOD_SHIFT : 0)
                | (event.isControlDown() ? GLFW_MOD_CONTROL : 0)
                | (event.isAltDown() || event.isAltGraphDown() ? GLFW_MOD_ALT : 0);
    }

    static int key(int awt) {
        if (awt >= VK_F1 && awt <= VK_F12) return GLFW_KEY_F1 + awt - VK_F1;
        return switch (awt) {
            case VK_ENTER -> GLFW_KEY_ENTER;
            case VK_TAB -> GLFW_KEY_TAB;
            case VK_ESCAPE -> GLFW_KEY_ESCAPE;
            case VK_BACK_SPACE -> GLFW_KEY_BACKSPACE;
            case VK_DELETE -> GLFW_KEY_DELETE;
            case VK_LEFT, VK_KP_LEFT -> GLFW_KEY_LEFT;
            case VK_RIGHT, VK_KP_RIGHT -> GLFW_KEY_RIGHT;
            case VK_UP, VK_KP_UP -> GLFW_KEY_UP;
            case VK_DOWN, VK_KP_DOWN -> GLFW_KEY_DOWN;
            case VK_HOME -> GLFW_KEY_HOME;
            case VK_END -> GLFW_KEY_END;
            case VK_PAGE_UP -> GLFW_KEY_PAGE_UP;
            case VK_PAGE_DOWN -> GLFW_KEY_PAGE_DOWN;
            default -> awt >= VK_A && awt <= VK_Z || awt >= VK_0 && awt <= VK_9 || awt == VK_SPACE ? awt : GLFW_KEY_UNKNOWN;
        };
    }

    void setClipboard(String value) {
        try { Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(value), null); }
        catch (IllegalStateException | SecurityException e) { game.status = "Schránka není dostupná: " + e.getMessage(); }
    }

    String getClipboard() {
        try { return (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor); }
        catch (Exception e) { game.status = "Schránka není dostupná: " + e.getMessage(); return null; }
    }
}
