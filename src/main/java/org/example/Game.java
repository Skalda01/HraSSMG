package org.example;

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.awt.geom.Point2D;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/** GLFW okno, OpenGL prezentace GUI a časování příkazů. */
public class Game {
    final Warehouse world = new Warehouse();
    final SortState sorting = new SortState();
    final CodeEditor editor = new CodeEditor();
    final CodeCompletion completion = new CodeCompletion();
    final TextRenderer textRenderer = new TextRenderer();
    final Dashboard dashboard = new Dashboard();
    final LevelCatalog levels;
    final DocumentationCatalog documentation = new DocumentationCatalog();
    DocumentationChapter documentationChapter;
    int documentationSelection;
    final Path saveFile, programDirectory;
    private final java.util.Map<String, String> levelPrograms = new java.util.HashMap<>();
    boolean levelMenu;
    int levelSelection;
    enum LevelChapter {
        ROBOT("Robot", false), MATERIALS("Podklady", true);
        final String label;
        final boolean visualization;
        LevelChapter(String label, boolean visualization) { this.label = label; this.visualization = visualization; }
        boolean contains(Level level) { return level.arrayLevel() == visualization; }
        static LevelChapter of(Level level) { return level.arrayLevel() ? MATERIALS : ROBOT; }
    }
    LevelChapter levelChapter = LevelChapter.ROBOT;
    enum MaterialsChapter {
        ARRAY("Pole"), ALGORITHMS("Algoritmy");
        final String label;
        MaterialsChapter(String label) { this.label = label; }
        boolean contains(Level level) { return this == ARRAY ? level.arrayPractice() : level.sorting() || level.searching(); }
        static MaterialsChapter of(Level level) { return level.arrayPractice() ? ARRAY : ALGORITHMS; }
    }
    MaterialsChapter materialsChapter;
    private final ExecutorService compiler = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "robot-compiler");
        thread.setDaemon(true);
        return thread;
    });
    private CompletableFuture<RobotProgram.Session> pending;
    private RobotProgram.Session session;
    private RobotProgram.Command activeCommand;
    private boolean sortingCommandStarted;
    private boolean commandNeedsDelay = true;
    private boolean stepRequested;
    private int commandIndex;
    int activeLine;
    enum EditorTab { JAVA, ASSIGNMENT, DOCUMENTATION, CONSOLE }
    EditorTab tab = EditorTab.JAVA;
    boolean running, saved = true;
    boolean editorView, fullscreen;
    UiLayout layout = new UiLayout(1280, 800, 1);
    String status = "Připraveno. Uprav program a stiskni F5.";
    int speedMultiplier = 1;
    double robotX = 2, robotY = 3;
    private double nextStep, previousFrame;
    private long window;
    private int shader, texture, vao;
    private boolean selecting, draggingSpeed;
    private BufferedImage surface;
    private IntBuffer pixels;
    private int restoreX, restoreY, restoreWidth, restoreHeight;
    private long currentMonitor;
    private boolean monitorGeometryChanged, monitorScaleChanged, repaint = true;
    private long cursorPhase = -1;
    private SwingWindow desktop;

    public Game() { this(Path.of("levels"), Path.of("programs"), Path.of("robot-program.java.txt")); }
    Game(Path levelDirectory, Path programDirectory, Path legacyProgram) {
        levels = new LevelCatalog(levelDirectory);
        sorting.reset(world.level);
        this.programDirectory = programDirectory;
        saveFile = legacyProgram;
    }

    public void run() {
        if (!"opengl".equalsIgnoreCase(System.getProperty("hrassmg.renderer", "java2d"))) {
            javax.swing.SwingUtilities.invokeLater(() -> {
                desktop = new SwingWindow(this);
                load();
                previousFrame = time();
                desktop.open();
            });
            return;
        }
        runOpenGL();
    }

    private void runOpenGL() {
        GLFWErrorCallback errors = GLFWErrorCallback.createPrint(System.err);
        errors.set();
        try {
            load();
            init(true);
            previousFrame = glfwGetTime();
            while (!glfwWindowShouldClose(window)) {
                glfwPollEvents();
                refreshLayout();
                update();
                render();
                glfwSwapBuffers(window);
            }
        } finally {
            save();
            closeSession();
            if (pending != null) pending.thenAccept(RobotProgram.Session::close);
            compiler.shutdownNow();
            textRenderer.close();
            if (pixels != null) org.lwjgl.system.MemoryUtil.memFree(pixels);
            if (window != NULL) {
                if (texture != 0) glDeleteTextures(texture);
                if (vao != 0) glDeleteVertexArrays(vao);
                if (shader != 0) glDeleteProgram(shader);
                glfwFreeCallbacks(window);
                glfwDestroyWindow(window);
            }
            GL.setCapabilities(null);
            glfwTerminate();
            glfwSetErrorCallback(null);
            errors.free();
        }
    }

    private void init(boolean visible) {
        if (!glfwInit()) throw new IllegalStateException("Nepodařilo se inicializovat GLFW.");
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        // https://www.glfw.org/docs/3.4/window_guide.html#window_scale
        glfwWindowHint(GLFW_SCALE_TO_MONITOR, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        window = glfwCreateWindow(1440, 900, "WORKER // OS — Robotická směna", NULL, NULL);
        if (window == NULL) throw new IllegalStateException("Nepodařilo se vytvořit okno OpenGL 3.3.");
        fitInitialWindow();
        glfwMakeContextCurrent(window);
        GL.createCapabilities();
        glfwSwapInterval(1);
        shader = createShader();
        vao = glGenVertexArrays();
        texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        // Textura má přesně velikost framebufferu: jeden texel na jeden pixel, bez převzorkování.
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        textRenderer.create();
        glfwSetKeyCallback(window, (w, key, scan, action, mods) -> {
            if (action == GLFW_PRESS || action == GLFW_REPEAT) {
                String name = (mods & GLFW_MOD_CONTROL) != 0 && (mods & GLFW_MOD_ALT) == 0
                        ? glfwGetKeyName(key, scan) : null;
                key(shortcutKey(key, mods, name), mods);
            }
        });
        glfwSetCharCallback(window, (w, codepoint) -> {
            if (codepoint == ' ' && (glfwGetKey(window, GLFW_KEY_LEFT_CONTROL) == GLFW_PRESS || glfwGetKey(window, GLFW_KEY_RIGHT_CONTROL) == GLFW_PRESS)
                    && glfwGetKey(window, GLFW_KEY_LEFT_ALT) != GLFW_PRESS && glfwGetKey(window, GLFW_KEY_RIGHT_ALT) != GLFW_PRESS) return;
            if (editable() && !Character.isISOControl(codepoint)) {
                editor.insert(new String(Character.toChars(codepoint)));
                changed();
                refreshCompletion(false);
            }
        });
        glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_PRESS) click(mods);
            if (button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_RELEASE) { selecting = false; draggingSpeed = false; }
        });
        glfwSetCursorPosCallback(window, (w, x, y) -> {
            if (draggingSpeed) {
                Point2D.Double point = pointer(x, y);
                setSpeedMultiplier(layout.speedAt(point.x));
                return;
            }
            if (!selecting || !editable()) return;
            repaint = true;
            Point2D.Double point = pointer(x, y);
            editor.at(editor.scroll + layout.codeRow(point.y), dashboard.codeColumn(point.x, layout, editor), true);
        });
        glfwSetScrollCallback(window, (w, dx, dy) -> {
            repaint = true;
            if (levelMenu) { moveLevelSelection(dy > 0 ? -1 : 1); return; }
            if (documentationChapter != null && (!layout.compact || !editorView)) {
                double[] x = new double[1], y = new double[1];
                glfwGetCursorPos(window, x, y);
                if (layout.documentationWindow.contains(pointer(x[0], y[0]))) {
                    dashboard.scrollChapter((int) (-dy * 48)); return;
                }
            }
            if (completion.isOpen()) {
                double[] x = new double[1], y = new double[1];
                glfwGetCursorPos(window, x, y);
                if (dashboard.completionPopup(editor, layout, completion).bounds().contains(pointer(x[0], y[0]))) {
                    completion.move(dy > 0 ? -1 : 1);
                    return;
                }
                completion.close();
            }
            if (world.level.arrayPractice() && documentationChapter == null && (!layout.compact || !editorView)) {
                double[] x = new double[1], y = new double[1];
                glfwGetCursorPos(window, x, y);
                if (layout.board.contains(pointer(x[0], y[0]))) {
                    dashboard.scrollArray(dy > 0 ? -1 : 1, sorting, layout); return;
                }
            }
            if (tab != EditorTab.JAVA) dashboard.scrollPage((int) (-dy * 48));
            else editor.scroll = Math.clamp(editor.scroll - (int) (dy * 3), 0, Math.max(0, editor.lines().length - editor.visibleRows));
        });
        glfwSetWindowPosCallback(window, (w, x, y) -> monitorGeometryChanged = true);
        glfwSetWindowContentScaleCallback(window, (w, x, y) -> { monitorGeometryChanged = true; monitorScaleChanged = true; });
        glfwSetWindowFocusCallback(window, (w, focused) -> {
            if (!focused) { completion.close(); selecting = draggingSpeed = false; repaint = true; }
        });
        refreshLayout();
        if (visible) glfwShowWindow(window);
    }

    boolean editable() { return editor.focused && !levelMenu && !running && pending == null && tab == EditorTab.JAVA && (!layout.compact || editorView); }

    /** GLFW předává fyzické klávesy; zkratky Z/Y se řídí aktuálním rozložením. */
    static int shortcutKey(int physicalKey, int mods, String name) {
        if ((mods & GLFW_MOD_CONTROL) == 0 || (mods & GLFW_MOD_ALT) != 0) return physicalKey;
        if ("z".equalsIgnoreCase(name)) return GLFW_KEY_Z;
        if ("y".equalsIgnoreCase(name)) return GLFW_KEY_Y;
        return physicalKey;
    }

    void key(int key, int mods) {
        repaint = true;
        boolean ctrl = (mods & GLFW_MOD_CONTROL) != 0 && (mods & GLFW_MOD_ALT) == 0, shift = (mods & GLFW_MOD_SHIFT) != 0;
        if (documentationChapter != null && !levelMenu && (!layout.compact || !editorView)) {
            switch (key) {
                case GLFW_KEY_ESCAPE, GLFW_KEY_BACKSPACE -> backToDocumentation();
                case GLFW_KEY_F1 -> closeDocumentation();
                case GLFW_KEY_F11 -> toggleFullscreen();
                case GLFW_KEY_F2 -> editorView = !editorView;
                case GLFW_KEY_F3 -> { backToDocumentation(); toggleLevelMenu(); }
                case GLFW_KEY_UP -> dashboard.scrollChapter(-26);
                case GLFW_KEY_DOWN -> dashboard.scrollChapter(26);
                case GLFW_KEY_PAGE_UP -> dashboard.scrollChapter(-layout.documentationContent.height);
                case GLFW_KEY_PAGE_DOWN, GLFW_KEY_SPACE -> dashboard.scrollChapter(layout.documentationContent.height);
                case GLFW_KEY_HOME -> dashboard.scrollChapter(-Integer.MAX_VALUE);
                case GLFW_KEY_END -> dashboard.scrollChapter(Integer.MAX_VALUE);
                default -> { }
            }
            return;
        }
        if (key == GLFW_KEY_F3) { toggleLevelMenu(); return; }
        if (levelMenu) {
            switch (key) {
                case GLFW_KEY_UP -> moveLevelSelection(-1);
                case GLFW_KEY_DOWN -> moveLevelSelection(1);
                case GLFW_KEY_LEFT -> selectLevelChapter(LevelChapter.ROBOT);
                case GLFW_KEY_RIGHT -> selectLevelChapter(LevelChapter.MATERIALS);
                case GLFW_KEY_TAB -> selectLevelChapter(levelChapter == LevelChapter.ROBOT ? LevelChapter.MATERIALS : LevelChapter.ROBOT);
                case GLFW_KEY_ENTER, GLFW_KEY_KP_ENTER -> activateSelectedLevel();
                case GLFW_KEY_ESCAPE -> { if (materialsChapter != null && levelChapter == LevelChapter.MATERIALS) backToMaterialsChapters(); else levelMenu = false; }
                case GLFW_KEY_BACKSPACE -> { if (materialsChapter != null && levelChapter == LevelChapter.MATERIALS) backToMaterialsChapters(); }
                case GLFW_KEY_F4 -> reloadLevels();
                case GLFW_KEY_F11 -> toggleFullscreen();
                default -> { }
            }
            return;
        }
        if (ctrl && key == GLFW_KEY_SPACE && editable()) { refreshCompletion(true); return; }
        if (completion.isOpen() && editable()) {
            if (key == GLFW_KEY_ESCAPE) { completion.close(); return; }
            if (!ctrl && !shift) {
                if (key == GLFW_KEY_UP) { completion.move(-1); return; }
                if (key == GLFW_KEY_DOWN) { completion.move(1); return; }
                if (key == GLFW_KEY_ENTER || key == GLFW_KEY_KP_ENTER || key == GLFW_KEY_TAB) {
                    if (completion.accept(editor)) changed();
                    return;
                }
            }
            completion.close();
        }
        if (key == GLFW_KEY_F11) { toggleFullscreen(); return; }
        if (key == GLFW_KEY_F2) { completion.close(); editorView = !editorView; return; }
        if (key == GLFW_KEY_F5) { play(); return; }
        if (key == GLFW_KEY_F6) { step(); return; }
        if (key == GLFW_KEY_F1) { selectTab(tab == EditorTab.DOCUMENTATION ? EditorTab.JAVA : EditorTab.DOCUMENTATION); editorView = true; return; }
        if (tab == EditorTab.DOCUMENTATION && (!layout.compact || editorView)) {
            if (key == GLFW_KEY_UP || key == GLFW_KEY_DOWN) {
                documentationSelection = Math.clamp(documentationSelection + (key == GLFW_KEY_UP ? -1 : 1), 0, Math.max(0, documentation.chapters().size() - 1));
                dashboard.showDocumentationItem(documentationSelection, documentation.chapters().size(), layout);
                return;
            }
            if (key == GLFW_KEY_ENTER || key == GLFW_KEY_KP_ENTER) { openDocumentationChapter(documentationSelection); return; }
            if (key == GLFW_KEY_F4) { selectTab(EditorTab.DOCUMENTATION); return; }
        }
        if (key == GLFW_KEY_ESCAPE) { running = stepRequested = false; status = "Pozastaveno."; return; }
        if (ctrl && key == GLFW_KEY_S) { save(); return; }
        if (ctrl && key == GLFW_KEY_R) { reset(); return; }
        if (!editable()) return;
        if (ctrl) {
            switch (key) {
                case GLFW_KEY_A -> { editor.anchor = 0; editor.caret = editor.text.length(); editor.reveal(); }
                case GLFW_KEY_Z -> history(shift);
                case GLFW_KEY_Y -> history(true);
                case GLFW_KEY_C -> setClipboard(editor.selection());
                case GLFW_KEY_X -> { setClipboard(editor.selection()); editor.insert(""); changed(); }
                case GLFW_KEY_V -> { String value = getClipboard(); if (value != null) { editor.insert(value); changed(); } }
                case GLFW_KEY_HOME -> editor.move(0, shift);
                case GLFW_KEY_END -> editor.move(editor.text.length(), shift);
                default -> { }
            }
            return;
        }
        switch (key) {
            case GLFW_KEY_LEFT -> editor.move(editor.caret - 1, shift);
            case GLFW_KEY_RIGHT -> editor.move(editor.caret + 1, shift);
            case GLFW_KEY_UP -> editor.vertical(-1, shift);
            case GLFW_KEY_DOWN -> editor.vertical(1, shift);
            case GLFW_KEY_HOME -> editor.move(editor.caret - editor.column(), shift);
            case GLFW_KEY_END -> editor.move(editor.caret - editor.column() + editor.lines()[editor.line()].length(), shift);
            case GLFW_KEY_PAGE_UP -> editor.vertical(-editor.visibleRows, shift);
            case GLFW_KEY_PAGE_DOWN -> editor.vertical(editor.visibleRows, shift);
            case GLFW_KEY_BACKSPACE -> { editor.backspace(); changed(); refreshCompletion(false); }
            case GLFW_KEY_DELETE -> { editor.delete(); changed(); refreshCompletion(false); }
            case GLFW_KEY_ENTER, GLFW_KEY_KP_ENTER -> { editor.newline(); changed(); }
            case GLFW_KEY_TAB -> { editor.insert("    "); changed(); }
            default -> { }
        }
    }

    private void changed() { closeSession(); repaint = true; saved = false; commandIndex = 0; activeLine = 0; }
    void type(String value) {
        if (!editable()) return;
        editor.insert(value); changed(); refreshCompletion(false);
    }

    private void setClipboard(String value) {
        if (desktop != null) desktop.setClipboard(value);
        else glfwSetClipboardString(window, value);
    }

    private String getClipboard() {
        return desktop != null ? desktop.getClipboard() : glfwGetClipboardString(window);
    }
    private void history(boolean redo) {
        if (!(redo ? editor.redo() : editor.undo())) return;
        changed();
        status = redo ? "Úprava obnovena." : "Poslední úprava vrácena. Ctrl+Y ji obnoví.";
    }
    private void refreshCompletion(boolean explicit) { completion.refresh(editor, explicit, world.level.allowedKeywords(), world.level.arrayLevel()); }

    private void click(int mods) {
        repaint = true;
        double[] mx = new double[1], my = new double[1];
        glfwGetCursorPos(window, mx, my);
        Point2D.Double point = pointer(mx[0], my[0]);
        click(point, mods);
    }

    void click(Point2D.Double point, int mods) {
        repaint = true;
        double y = point.y;
        if (documentationChapter != null && !levelMenu && (!layout.compact || !editorView) && layout.documentationWindow.contains(point)) {
            if (layout.documentationBack.contains(point)) backToDocumentation();
            else if (layout.documentationClose.contains(point)) closeDocumentation();
            return;
        }
        if (levelMenu) {
            if (layout.levelClose.contains(point)) levelMenu = false;
            else if (layout.levelReload.contains(point)) reloadLevels();
            else if (layout.levelActivate.contains(point)) activateSelectedLevel();
            else if (layout.levelRobot.contains(point)) selectLevelChapter(LevelChapter.ROBOT);
            else if (layout.levelMaterials.contains(point)) selectLevelChapter(LevelChapter.MATERIALS);
            else if (levelChapter == LevelChapter.MATERIALS && materialsChapter != null && layout.levelBack.contains(point)) backToMaterialsChapters();
            else if (layout.levelList.contains(point)) {
                int row = (int) ((y - layout.levelList.y) / layout.levelRowHeight);
                int index = firstLevelVisible() + row;
                if (row < layout.levelRows() && index < levelMenuSize()) levelSelection = index;
            }
            return;
        }
        if (layout.levelButton.contains(point)) { if (documentationChapter != null) backToDocumentation(); toggleLevelMenu(); return; }
        if (world.level.arrayPractice() && documentationChapter == null && (!layout.compact || !editorView)) {
            if (layout.arrayPrevious.contains(point)) { dashboard.scrollArray(-1, sorting, layout); return; }
            if (layout.arrayNext.contains(point)) { dashboard.scrollArray(1, sorting, layout); return; }
        }
        if ((!layout.compact || !editorView) && layout.nextLevel.contains(point) && canAdvanceLevel()) { advanceLevel(); return; }
        boolean showEditor = !layout.compact || editorView;
        if (showEditor && completion.isOpen() && editable()) {
            Dashboard.CompletionPopup popup = dashboard.completionPopup(editor, layout, completion);
            if (popup.bounds().contains(point)) {
                int row = (int) ((y - popup.bounds().y) / popup.rowHeight());
                if (row < popup.rows()) {
                    completion.move(popup.first() + row - completion.selected());
                    if (completion.accept(editor)) changed();
                }
                return;
            }
        }
        completion.close();
        if (layout.fullscreen.contains(point)) { toggleFullscreen(); return; }
        if (layout.compact && layout.boardTab.contains(point)) { editorView = false; return; }
        if (layout.compact && layout.editorTab.contains(point)) { editorView = true; return; }
        if (showEditor && layout.speed.contains(point)) {
            draggingSpeed = true;
            setSpeedMultiplier(layout.speedAt(point.x));
        } else if (showEditor && layout.code.contains(point) && tab == EditorTab.JAVA) {
            editor.focused = true;
            selecting = true;
            if (editable()) editor.at(editor.scroll + layout.codeRow(y),
                    dashboard.codeColumn(point.x, layout, editor), (mods & GLFW_MOD_SHIFT) != 0);
        } else if (showEditor && layout.play.contains(point)) play();
        else if (showEditor && layout.step.contains(point)) step();
        else if (showEditor && layout.reset.contains(point)) reset();
        else if (showEditor && layout.resetCode.contains(point)) resetCode();
        else if (showEditor && layout.assignment.contains(point)) selectTab(EditorTab.ASSIGNMENT);
        else if (showEditor && layout.documentation.contains(point)) selectTab(EditorTab.DOCUMENTATION);
        else if (showEditor && layout.console.contains(point)) selectTab(EditorTab.CONSOLE);
        else if (showEditor && layout.source.contains(point)) selectTab(EditorTab.JAVA);
        else if (showEditor && tab == EditorTab.DOCUMENTATION && layout.code.contains(point)) {
            for (int i = 0; i < documentation.chapters().size(); i++) {
                if (dashboard.documentationRow(layout, i).contains(point)) { openDocumentationChapter(i); break; }
            }
        }
    }

    void resetCode() {
        if (pending != null) { status = "Probíhá překlad, počkej prosím."; repaint = true; return; }
        completion.close();
        selecting = draggingSpeed = false;
        editor.replaceText(CodeEditor.starter(world.level));
        changed();
        selectTab(EditorTab.JAVA);
        editorView = editor.focused = true;
        status = "Původní kód úrovně obnoven. Ctrl+Z vrátí předchozí program.";
    }

    private void play() {
        completion.close();
        if (pending != null) return;
        if (advanceLevel()) return;
        if (running) { running = stepRequested = false; status = "Pozastaveno. F5 pokračuje, F6 provede krok."; return; }
        if (session != null) { running = true; stepRequested = false; nextStep = 0; return; }
        save();
        world.reset();
        sorting.reset(world.level);
        robotX = world.x;
        robotY = world.y;
        activeLine = commandIndex = 0;
        status = "Překládám Java program…";
        String source = editor.text;
        var allowedKeywords = world.level.allowedKeywords();
        var requiredKeywords = world.level.requiredKeywords();
        var sortingArray = world.level.array();
        boolean searching = world.level.searching();
        boolean sandbox = world.level.arraySandbox();
        pending = CompletableFuture.supplyAsync(() -> {
            try { return new RobotProgram(allowedKeywords, sortingArray, searching, requiredKeywords, sandbox).start(source); }
            catch (Exception e) { throw new java.util.concurrent.CompletionException(e); }
        }, compiler);
    }

    private void reset() {
        completion.close();
        if (pending != null) { status = "Probíhá překlad, počkej prosím."; return; }
        closeSession();
        commandIndex = activeLine = 0;
        world.reset();
        sorting.reset(world.level);
        robotX = world.x;
        robotY = world.y;
        status = (world.level.arrayLevel() ? "Pole resetováno." : "Sklad resetován.") + " Program zůstal zachován.";
    }

    private void step() {
        completion.close();
        if (pending != null) return;
        running = false;
        if (session == null) { status = "Nejprve načti program tlačítkem Spustit (F5)."; return; }
        stepRequested = true;
        nextStep = 0;
    }

    private double time() { return desktop != null ? System.nanoTime() / 1_000_000_000.0 : glfwGetTime(); }

    void update() {
        double now = time(), dt = Math.min(0.1, now - previousFrame);
        previousFrame = now;
        if (world.level.arrayLevel() && sorting.animating() && (running || stepRequested)) {
            sorting.advance(dt, stepInterval());
            repaint = true;
        }
        if (pending != null && pending.isDone()) {
            repaint = true;
            try {
                session = pending.join();
                running = !levelMenu && documentationChapter == null;
                status = running ? "Program běží. Příkazy se provádějí průběžně." : "Program je přeložen a pozastaven. F5 pokračuje.";
                world.message(status);
                nextStep = now + 0.3;
            } catch (java.util.concurrent.CompletionException e) {
                programError(e.getCause());
            } finally { pending = null; }
        }
        try {
            if (session != null) { updateConsole(); session.check(); }
            if (world.level.arrayLevel() && session != null && activeCommand == null && session.finished()) execute();
        } catch (IllegalStateException e) { programError(e); }
        if (running || stepRequested) {
            if (nextStep == 0) nextStep = now;
            int budget = 50;
            while ((running || stepRequested) && now >= nextStep && budget-- > 0) {
                repaint = true;
                if (!execute()) { nextStep = now; break; }
                nextStep += commandNeedsDelay ? stepInterval() : 0;
            }
        }
        double blend = 1 - Math.exp(-dt * 18 * speedMultiplier);
        robotX += (world.x - robotX) * blend;
        robotY += (world.y - robotY) * blend;
    }

    private boolean execute() {
        commandNeedsDelay = true;
        if (session == null) { running = stepRequested = false; return false; }
        try {
            updateConsole();
            if (activeCommand == null) activeCommand = session.poll();
            updateConsole();
            if (activeCommand == null) {
                if (session.finished()) {
                    updateConsole();
                    world.finishProgram();
                    if (world.level.arrayLevel()) {
                        sorting.finish();
                        if (!world.outputComplete()) {
                            sorting.complete = false;
                            sorting.operation = world.outputProblem();
                        }
                        repaint = true;
                        status = sorting.operation;
                        if (sorting.complete && canAdvanceLevel()) status += " F5 otevře další level.";
                        world.message(status); closeSession();
                        return false;
                    }
                    status = world.complete() ? completedStatus()
                            : !world.outputComplete() ? world.outputProblem()
                            : world.level.positionGoal() != null ? "Program skončil. Dojeď do cíle " + world.level.positionGoal() + "."
                            : commandIndex == 0 && world.consoleOutput.isEmpty() ? "Program neobsahuje žádné příkazy robota."
                            : "Program skončil. " + (world.level.endless() ? "Splněných zakázek: " + world.completedOrders
                            : "Doručeno " + world.delivered() + "/" + world.totalGoal()) + ". F5 spustí nový běh.";
                    world.message(status); closeSession();
                }
                return false;
            }
            RobotProgram.Command command = activeCommand;
            activeLine = command.line();
            if (activeLine > 0) editor.scroll = Math.max(0, activeLine - editor.visibleRows / 2 - 1);
            if (world.level.arrayLevel()) return executeSorting(command);
            int replyX = world.x, replyY = world.y;
            long previousOrder = world.completedOrders;
            boolean completed;
            if (command.query()) {
                int[] result = world.query(command); replyX = result[0]; replyY = result[1]; completed = true;
            } else {
                completed = world.execute(command); replyX = world.x; replyY = world.y;
            }
            stepRequested = false;
            if (!completed) return true;
            commandIndex++;
            session.acknowledge(replyX, replyY, world.completedOrders + 1);
            activeCommand = null;
            status = "Řádek " + activeLine + " · " + command.name() + " · příkaz " + commandIndex
                    + (world.level.endless() ? " · zakázka " + (world.completedOrders + 1) : "");
            if (world.completedOrders > previousOrder) status = "Zakázka " + world.completedOrders + " splněna. Přichází další zakázka, zásoby zůstávají.";
            if (world.complete()) {
                closeSession();
                status = completedStatus();
            }
            return true;
        } catch (IllegalStateException | IllegalArgumentException e) { programError(e); return false; }
    }

    private void updateConsole() {
        String output = session.output();
        if (!output.equals(world.consoleOutput)) { world.captureOutput(output); repaint = true; }
    }

    private String completedStatus() {
        return "Úroveň splněna! " + (world.level.checksConsole() ? "Výstup konzole odpovídá zadání."
                : world.level.positionGoal() != null ? "Robot dorazil do cíle " + world.level.positionGoal() + " · " + world.steps + " kroků."
                : world.delivered() + " zásilek · " + world.steps + " kroků.")
                + (canAdvanceLevel() ? " Pokračuj tlačítkem Další level nebo F5." : "");
    }

    private boolean executeSorting(RobotProgram.Command command) {
        int reply = 0;
        if (!sortingCommandStarted) {
            reply = sorting.execute(command);
            sortingCommandStarted = true;
        }
        if (sorting.animating()) return false;
        commandNeedsDelay = !java.util.Set.of("sortAttach", "sortResult", "sortSnapshot", "sandboxArray", "sandboxBlock").contains(command.name());
        session.acknowledge(reply, 0, 1);
        sortingCommandStarted = false;
        commandIndex++; activeCommand = null; stepRequested = false;
        status = "Řádek " + activeLine + " · " + sorting.operation;
        return true;
    }

    private void closeSession() {
        running = stepRequested = false;
        activeCommand = null;
        sortingCommandStarted = false;
        sorting.progress = 1;
        if (session != null) { session.close(); session = null; }
    }

    private void programError(Throwable e) {
        closeSession(); repaint = true;
        String message = e.getMessage() == null ? e.toString() : e.getMessage();
        var errorLine = java.util.regex.Pattern.compile("editor:(\\d+)").matcher(message);
        if (errorLine.find()) {
            activeLine = Integer.parseInt(errorLine.group(1));
            editor.scroll = Math.max(0, activeLine - editor.visibleRows / 2 - 1);
        }
        status = (activeLine > 0 ? "Řádek " + activeLine + ": " : "Chyba: ") + message.lines().filter(line -> !line.isBlank()).findFirst().orElse(message);
        for (String line : message.split("\n")) world.message(line.strip());
    }

    private void load() {
        String cached = levelPrograms.get(world.level.id());
        if (cached != null) { editor.setText(cached); return; }
        editor.setText(CodeEditor.starter(world.level));
        saved = false;
        Path file = programFile();
        try {
            if (Files.isRegularFile(file) && Files.size(file) <= 120_000) {
                String source = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
                editor.setText(CodeEditor.migrateSavedProgram(source));
                saved = editor.text.equals(source);
            }
        } catch (Exception e) { world.message("Uložený program nelze načíst: " + e.getMessage()); }
    }
    private void save() {
        levelPrograms.put(world.level.id(), editor.text);
        try {
            Path file = programFile();
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Files.writeString(file, editor.text, StandardCharsets.UTF_8); saved = true;
        }
        catch (Exception e) { saved = false; world.message("Uložení selhalo: " + e.getMessage()); }
    }

    private Path programFile() {
        return world.level.id().equals(LevelCatalog.FIRST_ID) ? saveFile : programDirectory.resolve(world.level.id() + ".java.txt");
    }

    void toggleLevelMenu() {
        completion.close(); selecting = draggingSpeed = false;
        levelMenu = !levelMenu;
        if (levelMenu) {
            running = stepRequested = false;
            selectLevelChapter(LevelChapter.of(world.level));
            if (world.level.arrayLevel()) selectMaterialsChapter(MaterialsChapter.of(world.level));
            reloadLevels();
            selectActiveLevel();
        }
        repaint = true;
    }

    void moveLevelSelection(int delta) {
        int size = levelMenuSize();
        if (size == 0) { levelSelection = 0; return; }
        levelSelection = Math.clamp(levelSelection + delta, 0, size - 1);
        repaint = true;
    }

    boolean materialsOverview() { return levelChapter == LevelChapter.MATERIALS && materialsChapter == null; }
    int levelMenuSize() { return materialsOverview() ? MaterialsChapter.values().length : levelChoices().size(); }
    java.util.List<Level> levelChoices() {
        if (materialsOverview()) return java.util.List.of();
        return levels.levels().stream().filter(levelChapter::contains)
                .filter(level -> levelChapter == LevelChapter.ROBOT || materialsChapter.contains(level)).toList();
    }
    void selectLevelChapter(LevelChapter chapter) {
        levelChapter = chapter; materialsChapter = null; levelSelection = 0; selectActiveLevel(); repaint = true;
    }
    void selectMaterialsChapter(MaterialsChapter chapter) {
        levelChapter = LevelChapter.MATERIALS; materialsChapter = chapter; levelSelection = 0; selectActiveLevel(); repaint = true;
    }
    private void backToMaterialsChapters() {
        int selected = materialsChapter.ordinal();
        selectLevelChapter(LevelChapter.MATERIALS); levelSelection = selected;
    }
    private void selectActiveLevel() {
        var choices = levelChoices();
        for (int i = 0; i < choices.size(); i++) if (choices.get(i).id().equals(world.level.id())) levelSelection = i;
    }
    int firstLevelVisible() { return Math.clamp(levelSelection - layout.levelRows() + 1, 0, Math.max(0, levelMenuSize() - layout.levelRows())); }
    boolean compiling() { return pending != null; }

    Level nextLevel() {
        var available = levels.levels();
        for (int i = 0; i + 1 < available.size(); i++) {
            if (available.get(i).id().equals(world.level.id())) return available.get(i + 1);
        }
        return null;
    }

    boolean levelComplete() { return !world.level.arraySandbox() && (world.level.arrayLevel() ? sorting.complete && world.outputComplete() : world.complete()); }
    boolean canAdvanceLevel() { return levelComplete() && pending == null && nextLevel() != null; }

    boolean advanceLevel() {
        if (!canAdvanceLevel()) return false;
        return activateLevel(nextLevel());
    }

    void reloadLevels() {
        int chapterSelection = levelSelection;
        var previous = levelChoices();
        String selected = previous.isEmpty() ? null : previous.get(Math.clamp(levelSelection, 0, previous.size() - 1)).id();
        levels.reload();
        levelSelection = 0;
        var choices = levelChoices();
        for (int i = 0; i < choices.size(); i++) if (choices.get(i).id().equals(selected)) levelSelection = i;
        if (materialsOverview()) levelSelection = Math.clamp(chapterSelection, 0, MaterialsChapter.values().length - 1);
        repaint = true;
    }

    private void activateSelectedLevel() {
        if (materialsOverview()) { selectMaterialsChapter(MaterialsChapter.values()[levelSelection]); return; }
        var choices = levelChoices();
        if (!choices.isEmpty()) activateLevel(choices.get(levelSelection));
    }

    boolean activateLevel(Level level) {
        if (pending != null) { status = "Probíhá překlad programu. S aktivací úrovně počkej na jeho dokončení."; repaint = true; return false; }
        save();
        closeSession(); completion.close(); selecting = draggingSpeed = false;
        commandIndex = activeLine = 0; nextStep = 0;
        world.setLevel(level); robotX = world.x; robotY = world.y;
        selectLevelChapter(LevelChapter.of(level));
        if (level.arrayLevel()) selectMaterialsChapter(MaterialsChapter.of(level));
        sorting.reset(level);
        load();
        selectTab(EditorTab.ASSIGNMENT); editorView = false; levelMenu = false;
        status = "Aktivní úroveň: " + level.name() + ". Zadání je v panelu programu.";
        repaint = true;
        return true;
    }

    private void render() {
        refreshLayout();
        int width = layout.pixelWidth, height = layout.pixelHeight;
        if (glfwGetWindowAttrib(window, GLFW_ICONIFIED) == GLFW_TRUE) {
            glfwWaitEventsTimeout(0.1);
            return;
        }
        if (surface == null || surface.getWidth() != width || surface.getHeight() != height) {
            repaint = true;
            surface = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            if (pixels != null) org.lwjgl.system.MemoryUtil.memFree(pixels);
            pixels = org.lwjgl.system.MemoryUtil.memAllocInt(Math.multiplyExact(width, height));
            glBindTexture(GL_TEXTURE_2D, texture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_BGRA, GL_UNSIGNED_BYTE, 0L);
        }
        long blink = System.currentTimeMillis() / 500;
        if (blink != cursorPhase) { repaint = true; cursorPhase = blink; }
        if (running || Math.abs(robotX - world.x) > 0.001 || Math.abs(robotY - world.y) > 0.001) repaint = true;
        if (repaint) {
            textRenderer.beginFrame(width, height);
            Graphics2D graphics = surface.createGraphics();
            graphics.scale(layout.scale, layout.scale);
            dashboard.draw(graphics, this);
            graphics.dispose();
            textRenderer.prepare();
            pixels.clear();
            pixels.put(((DataBufferInt) surface.getRaster().getDataBuffer()).getData()).flip();
        }
        glViewport(0, 0, width, height);
        glDisable(GL_BLEND);
        glDisable(GL_SCISSOR_TEST);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texture);
        if (repaint) glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_BGRA, GL_UNSIGNED_BYTE, pixels);
        repaint = false;
        glUseProgram(shader);
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        textRenderer.render(width, height);
    }

    void paintJava2D(Graphics2D graphics, int width, int height, double dpi) {
        UiLayout next = new UiLayout(Math.max(1, (int) Math.round(width * dpi)),
                Math.max(1, (int) Math.round(height * dpi)), dpi);
        if (next.pixelWidth != layout.pixelWidth || next.pixelHeight != layout.pixelHeight || next.scale != layout.scale) {
            completion.close(); repaint = true;
        }
        layout = next;
        editor.visibleRows = layout.visibleRows();
        if (surface == null || surface.getWidth() != layout.pixelWidth || surface.getHeight() != layout.pixelHeight) {
            surface = new BufferedImage(layout.pixelWidth, layout.pixelHeight, BufferedImage.TYPE_INT_RGB);
            repaint = true;
        }
        long blink = System.currentTimeMillis() / 500;
        if (blink != cursorPhase) { repaint = true; cursorPhase = blink; }
        if (running || Math.abs(robotX - world.x) > 0.001 || Math.abs(robotY - world.y) > 0.001) repaint = true;
        if (repaint) {
            Graphics2D image = surface.createGraphics();
            try { image.scale(layout.scale, layout.scale); dashboard.draw(image, this); }
            finally { image.dispose(); }
            repaint = false;
        }
        graphics.drawImage(surface, 0, 0, width, height, null);
    }

    Point2D.Double desktopPointer(double x, double y, int width, int height) {
        return layout.pointer(x, y, width, height);
    }

    void drag(Point2D.Double point) {
        repaint = true;
        if (draggingSpeed) setSpeedMultiplier(layout.speedAt(point.x));
        else if (selecting && editable()) {
            editor.at(editor.scroll + layout.codeRow(point.y), dashboard.codeColumn(point.x, layout, editor), true);
        }
    }

    void releasePointer() { selecting = draggingSpeed = false; }
    void lostFocus() { completion.close(); releasePointer(); repaint = true; }

    void scroll(Point2D.Double point, double dy) {
        repaint = true;
        if (dy == 0) return;
        if (levelMenu) { moveLevelSelection(dy > 0 ? -1 : 1); return; }
        if (documentationChapter != null && (!layout.compact || !editorView) && layout.documentationWindow.contains(point)) {
            dashboard.scrollChapter((int) (-dy * 48)); return;
        }
        if (completion.isOpen()) {
            if (dashboard.completionPopup(editor, layout, completion).bounds().contains(point)) {
                completion.move(dy > 0 ? -1 : 1); return;
            }
            completion.close();
        }
        if (world.level.arrayPractice() && documentationChapter == null && (!layout.compact || !editorView) && layout.board.contains(point)) {
            dashboard.scrollArray(dy > 0 ? -1 : 1, sorting, layout); return;
        }
        if (tab != EditorTab.JAVA) dashboard.scrollPage((int) (-dy * 48));
        else editor.scroll = Math.clamp(editor.scroll - (int) (dy * 3), 0, Math.max(0, editor.lines().length - editor.visibleRows));
    }

    void closeDesktop() {
        save(); closeSession();
        if (pending != null) pending.thenAccept(RobotProgram.Session::close);
        compiler.shutdownNow();
        textRenderer.close();
    }

    private void refreshLayout() {
        if (monitorGeometryChanged && !fullscreen) fitChangedMonitor();
        int[] width = new int[1], height = new int[1];
        float[] sx = new float[1], sy = new float[1];
        glfwGetFramebufferSize(window, width, height);
        if (width[0] == 0 || height[0] == 0) return;
        glfwGetWindowContentScale(window, sx, sy);
        UiLayout next = new UiLayout(width[0], height[0], Math.max(sx[0], sy[0]));
        if (next.pixelWidth != layout.pixelWidth || next.pixelHeight != layout.pixelHeight || next.scale != layout.scale) {
            repaint = true;
            completion.close();
        }
        layout = next;
        editor.visibleRows = layout.visibleRows();
    }

    private Point2D.Double pointer(double x, double y) {
        refreshLayout();
        int[] width = new int[1], height = new int[1];
        glfwGetWindowSize(window, width, height);
        return layout.pointer(x, y, width[0], height[0]);
    }

    void selectTab(EditorTab selected) {
        completion.close();
        documentationChapter = null;
        if (selected == EditorTab.DOCUMENTATION) {
            documentation.reload();
            documentationSelection = Math.clamp(documentationSelection, 0, Math.max(0, documentation.chapters().size() - 1));
            if (!documentation.errors().isEmpty()) status = "Dokumentace: " + documentation.errors().getFirst();
        }
        tab = selected;
        dashboard.resetPageScroll();
        repaint = true;
    }

    void openDocumentationChapter(int index) {
        if (index < 0 || index >= documentation.chapters().size()) return;
        completion.close(); selecting = draggingSpeed = false;
        running = stepRequested = false;
        tab = EditorTab.DOCUMENTATION; editorView = false; levelMenu = false;
        documentationSelection = index;
        documentationChapter = documentation.chapters().get(index);
        dashboard.resetChapterScroll();
        dashboard.showDocumentationItem(index, documentation.chapters().size(), layout);
        repaint = true;
    }

    void backToDocumentation() {
        documentationChapter = null;
        editorView = true;
        dashboard.resetChapterScroll();
        dashboard.showDocumentationItem(documentationSelection, documentation.chapters().size(), layout);
        repaint = true;
    }

    void closeDocumentation() {
        selectTab(EditorTab.JAVA);
        editorView = false;
    }

    void setSpeedMultiplier(int value) {
        int next = Math.clamp(value, 1, 10);
        if (next == speedMultiplier) return;
        speedMultiplier = next;
        nextStep = 0;
        repaint = true;
    }

    double stepInterval() { return (world.level.searching() ? 0.5 : 0.24) / speedMultiplier; }

    private void fitInitialWindow() {
        long monitor = glfwGetPrimaryMonitor();
        if (monitor == NULL) return;
        currentMonitor = monitor;
        int[] x = new int[1], y = new int[1], width = new int[1], height = new int[1];
        int[] left = new int[1], top = new int[1], right = new int[1], bottom = new int[1];
        float[] sx = new float[1], sy = new float[1];
        glfwGetMonitorWorkarea(monitor, x, y, width, height);
        glfwGetMonitorContentScale(monitor, sx, sy);
        glfwGetWindowFrameSize(window, left, top, right, bottom);
        double scale = Math.max(sx[0], sy[0]);
        int w = Math.min((int) (1440 * scale), width[0] - left[0] - right[0] - 32);
        int h = Math.min((int) (900 * scale), height[0] - top[0] - bottom[0] - 32);
        glfwSetWindowSize(window, Math.max(320, w), Math.max(240, h));
        glfwSetWindowPos(window, x[0] + (width[0] - w) / 2, y[0] + top[0] + (height[0] - h - top[0] - bottom[0]) / 2);
        glfwSetWindowSizeLimits(window, Math.min(w, (int) (640 * scale)), Math.min(h, (int) (440 * scale)), GLFW_DONT_CARE, GLFW_DONT_CARE);
    }

    private void toggleFullscreen() {
        if (desktop != null) { fullscreen = !fullscreen; desktop.fullscreen(fullscreen); return; }
        if (fullscreen) {
            glfwSetWindowMonitor(window, NULL, restoreX, restoreY, restoreWidth, restoreHeight, GLFW_DONT_CARE);
            fullscreen = false;
        } else {
            int[] x = new int[1], y = new int[1], width = new int[1], height = new int[1];
            glfwGetWindowPos(window, x, y);
            glfwGetWindowSize(window, width, height);
            restoreX = x[0]; restoreY = y[0]; restoreWidth = width[0]; restoreHeight = height[0];
            long monitor = monitorForWindow();
            var mode = monitor == NULL ? null : glfwGetVideoMode(monitor);
            if (mode == null) return;
            glfwSetWindowMonitor(window, monitor, 0, 0, mode.width(), mode.height(), mode.refreshRate());
            fullscreen = true;
        }
        glfwSwapInterval(1);
        refreshLayout();
    }

    private long monitorForWindow() {
        int[] x = new int[1], y = new int[1], width = new int[1], height = new int[1];
        glfwGetWindowPos(window, x, y); glfwGetWindowSize(window, width, height);
        long result = glfwGetPrimaryMonitor(), bestArea = -1;
        var monitors = glfwGetMonitors();
        if (monitors != null) for (int i = 0; i < monitors.limit(); i++) {
            long candidate = monitors.get(i);
            int[] mx = new int[1], my = new int[1], mw = new int[1], mh = new int[1];
            glfwGetMonitorWorkarea(candidate, mx, my, mw, mh);
            long area = (long) Math.max(0, Math.min(x[0] + width[0], mx[0] + mw[0]) - Math.max(x[0], mx[0]))
                    * Math.max(0, Math.min(y[0] + height[0], my[0] + mh[0]) - Math.max(y[0], my[0]));
            if (area > bestArea) { result = candidate; bestArea = area; }
        }
        return result;
    }

    private void fitChangedMonitor() {
        monitorGeometryChanged = false;
        long monitor = monitorForWindow();
        if (monitor == NULL || (monitor == currentMonitor && !monitorScaleChanged)) return;
        monitorScaleChanged = false;
        currentMonitor = monitor;
        int[] mx = new int[1], my = new int[1], mw = new int[1], mh = new int[1];
        int[] x = new int[1], y = new int[1], width = new int[1], height = new int[1];
        int[] left = new int[1], top = new int[1], right = new int[1], bottom = new int[1];
        float[] sx = new float[1], sy = new float[1];
        glfwGetMonitorWorkarea(monitor, mx, my, mw, mh);
        glfwGetMonitorContentScale(monitor, sx, sy);
        glfwGetWindowPos(window, x, y); glfwGetWindowSize(window, width, height);
        glfwGetWindowFrameSize(window, left, top, right, bottom);
        int w = Math.max(320, Math.min(width[0], mw[0] - left[0] - right[0] - 16));
        int h = Math.max(240, Math.min(height[0], mh[0] - top[0] - bottom[0] - 16));
        double dpi = Math.max(sx[0], sy[0]);
        glfwSetWindowSizeLimits(window, Math.min(w, (int) (640 * dpi)), Math.min(h, (int) (440 * dpi)), GLFW_DONT_CARE, GLFW_DONT_CARE);
        if (w != width[0] || h != height[0]) glfwSetWindowSize(window, w, h);
        int px = Math.clamp(x[0], mx[0] + left[0], mx[0] + mw[0] - w - right[0]);
        int py = Math.clamp(y[0], my[0] + top[0], my[0] + mh[0] - h - bottom[0]);
        if (px != x[0] || py != y[0]) glfwSetWindowPos(window, px, py);
        monitorGeometryChanged = false;
    }

    private int createShader() {
        int vertex = compileShader(GL_VERTEX_SHADER, """
                #version 330 core
                out vec2 uv;
                void main() {
                    vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                    uv = vec2(p.x, 1.0 - p.y);
                    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
                }
                """);
        int fragment = compileShader(GL_FRAGMENT_SHADER, """
                #version 330 core
                in vec2 uv;
                out vec4 color;
                uniform sampler2D screen;
                void main() { color = texture(screen, uv); }
                """);
        int program = glCreateProgram();
        glAttachShader(program, vertex);
        glAttachShader(program, fragment);
        glLinkProgram(program);
        glDeleteShader(vertex);
        glDeleteShader(fragment);
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) throw new IllegalStateException(glGetProgramInfoLog(program));
        return program;
    }
    private int compileShader(int type, String code) {
        int id = glCreateShader(type);
        glShaderSource(id, code);
        glCompileShader(id);
        if (glGetShaderi(id, GL_COMPILE_STATUS) == GL_FALSE) throw new IllegalStateException(glGetShaderInfoLog(id));
        return id;
    }
}
