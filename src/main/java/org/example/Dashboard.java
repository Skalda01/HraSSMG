package org.example;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import robot.api.Robot.ShelfType;

/** DPI nezávislé rozložení; text se rasterizuje až do skutečných pixelů framebufferu. */
final class Dashboard {
    private int firstArrayIndex;
    private long lastArrayVersion = Long.MIN_VALUE;
    private int lastArrayCapacity;
    private static final Color BG = new Color(0x0e161b), PANEL = new Color(0x192329),
            BORDER = new Color(0x2b3941), WHITE = new Color(0xe9efeb), MUTED = new Color(0x91a3ae),
            GREEN = new Color(0xd6fa83), ORANGE = new Color(0xffbd60), PURPLE = new Color(0xb59aff),
            BLUE = new Color(0x6bc9ff), CORAL = new Color(0xff9393);
    private static final String JAVA_KEYWORDS = "abstract|assert|boolean|break|byte|case|catch|char|class|continue|default|do|double|else|enum|extends|final|finally|float|for|if|implements|import|instanceof|int|interface|long|native|new|package|private|protected|public|return|short|static|super|switch|synchronized|this|throw|throws|transient|try|var|void|volatile|while|true|false|null|record|String|ArrayList|List|Integer|Random|Math";
    private static final Set<String> KEYWORDS = Set.of(JAVA_KEYWORDS.split("\\|"));
    private static final Pattern TOKENS = Pattern.compile("//.*|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|\\b(?:" + JAVA_KEYWORDS + ")\\b|\\b(?:pick|drop|move|robotCoordinatesX|robotCoordinatesY|tileType|shelfType|cargoType|orderRemaining|Direction|TileType|ShelfType|LEFT|RIGHT|TOP|DOWN|FLOOR|SHELF|DOCK|INBOUND|PARTS|ELECTRONICS|TOOLS|MATERIALS|main)\\b|\\b\\d+\\b");
    private static Color cargoColor(String type) {
        return switch (type) { case "orange" -> ORANGE; case "purple" -> PURPLE; case "tools" -> BLUE; case "materials" -> CORAL; default -> MUTED; };
    }
    private Graphics2D g;
    private TextRenderer textRenderer;
    private double charWidth = 8.25;
    private int pageScroll, maxPageScroll;
    private int chapterScroll, chapterMaxScroll;

    int horizontal(UiLayout layout, CodeEditor editor) {
        int columns = Math.max(8, (int) ((layout.code.width - 52) / charWidth));
        return Math.max(0, editor.column() - columns + 1);
    }

    int codeColumn(double x, UiLayout layout, CodeEditor editor) {
        return Math.max(0, (int) Math.round((x - layout.codeX()) / charWidth) + horizontal(layout, editor));
    }

    void scrollPage(int delta) { pageScroll = (int) Math.clamp((long) pageScroll + delta, 0L, (long) maxPageScroll); }
    void resetPageScroll() { pageScroll = maxPageScroll = 0; }
    void scrollChapter(int delta) { chapterScroll = (int) Math.clamp((long) chapterScroll + delta, 0L, (long) chapterMaxScroll); }
    void resetChapterScroll() { chapterScroll = chapterMaxScroll = 0; }

    record CompletionPopup(Rectangle bounds, int rows, int first, int rowHeight) {}

    CompletionPopup completionPopup(CodeEditor e, UiLayout l, CodeCompletion completion) {
        int available = Math.max(1, l.code.height - 2);
        int rowHeight = Math.min(24, available);
        int footer = available >= 86 ? 38 : 0;
        int rows = Math.min(completion.items().size(), Math.max(1, Math.min(6, (available - footer) / rowHeight)));
        int height = rows * rowHeight + footer;
        int width = Math.min(380, l.code.width - 4);
        int caretX = (int) Math.round(l.codeX() + (e.column() - horizontal(l, e)) * charWidth);
        int caretY = l.codeBaseline() + (e.line() - e.scroll) * l.lineHeight;
        int x = Math.clamp(caretX, l.code.x + 2, l.code.x + l.code.width - width - 2);
        int y = caretY + 7;
        if (y + height > l.code.y + l.code.height - 1) y = caretY - 20 - height;
        y = Math.clamp(y, l.code.y + 1, l.code.y + l.code.height - height - 1);
        return new CompletionPopup(new Rectangle(x, y, width, height), rows, completion.firstVisible(rows), rowHeight);
    }

    void draw(Graphics2D graphics, Game game) {
        g = graphics;
        textRenderer = game.textRenderer;
        UiLayout l = game.layout;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setColor(BG);
        // Přesah pokrývá také poslední fyzický pixel při neceločíselném DPI.
        g.fillRect(0, 0, l.width + 1, l.height + 1);
        header(game, l);
        if (l.compact) {
            String boardTitle=game.world.level.arrayPractice() ? "Práce s polem" : game.world.level.searching() ? "Binary search" : game.world.level.sorting() ? "Bubble sort"
                    : game.world.level.visitsTiles() ? "Políčka "+game.world.printedTiles.size()+"/"+game.world.level.tileGoal()
                    : "Sklad " + game.world.delivered() + "/" + game.world.totalGoal();
            button(l.boardTab, boardTitle, "", !game.editorView ? GREEN : PANEL, !game.editorView ? BG : MUTED);
            button(l.editorTab, "Java editor", "F2", game.editorView ? GREEN : PANEL, game.editorView ? BG : MUTED);
        }
        if (!l.compact || !game.editorView) {
            if (game.documentationChapter != null) documentationChapter(game, l);
            else if (game.world.level.arrayPractice()) arrayCanvas(game, l);
            else if (game.world.level.searching()) searchCanvas(game, l);
            else if (game.world.level.sorting()) sortingCanvas(game, l);
            else board(game, l);
        }
        if (!l.compact || game.editorView) editor(game, l);
        String footer = l.width < 1000 ? "F1 dokumentace  F2 editor  F3 úrovně  F5 start/pauza  F6 krok  F11 celá obrazovka"
                : "Ctrl+mezerník návrhy    F1 dokumentace    F3 úrovně    F5 start / pauza    F6 krok    Ctrl+S uložit    F11 celá obrazovka";
        text(footer, l.board.x, l.height - 10, 11, MUTED, false);
        if (game.levelMenu) levelMenu(game, l);
    }

    private void header(Game game, UiLayout l) {
        fill(0, 0, l.width + 1, 64, new Color(0x141e24), 0);
        fill(20, 18, 30, 30, GREEN, 10);
        g.setColor(BG);
        g.setStroke(new BasicStroke(2.5f));
        g.drawLine(27, 31, 33, 38);
        g.drawLine(33, 38, 43, 25);
        text("WORKER", 64, 38, 22, WHITE, true);
        text("// OS", 171, 38, 22, GREEN, false);
        if (l.width > 1000) text(shortened(game.world.level.name(), l.levelButton.x - 340, 12), 320, 37, 12, MUTED, false);
        button(l.levelButton, "Úrovně", "F3", game.levelMenu ? GREEN : PANEL, game.levelMenu ? BG : WHITE);
        button(l.fullscreen, game.fullscreen ? "Okno" : "Celá obr.", "", PANEL, MUTED);
    }

    private int arrayColumns(SortState state, UiLayout layout) {
        return Math.max(1, Math.min(state.length(), Math.min(8, (layout.board.width - 56) / 64)));
    }
    int arrayCapacity(SortState state, UiLayout layout) {
        return arrayColumns(state, layout) * Math.max(1, (layout.board.height - 250) / 72);
    }
    private void prepareArrayView(SortState state, UiLayout layout) {
        int capacity = arrayCapacity(state, layout);
        if (lastArrayVersion != state.viewVersion || lastArrayCapacity != capacity) {
            firstArrayIndex = state.left < 0 ? 0 : (state.left / capacity) * capacity;
            lastArrayVersion = state.viewVersion;
            lastArrayCapacity = capacity;
        }
        int last = state.length() == 0 ? 0 : ((state.length() - 1) / capacity) * capacity;
        firstArrayIndex = Math.clamp((firstArrayIndex / capacity) * capacity, 0, last);
    }
    void scrollArray(int delta, SortState state, UiLayout layout) {
        prepareArrayView(state, layout);
        int capacity = arrayCapacity(state, layout);
        long last = state.length() == 0 ? 0 : ((state.length() - 1) / capacity) * (long) capacity;
        firstArrayIndex = (int) Math.clamp(firstArrayIndex + (long) delta * capacity, 0, last);
    }
    private void arrayCanvas(Game game, UiLayout l) {
        Rectangle r = l.board;
        SortState state = game.sorting;
        panel(r, PANEL);
        Shape clip = g.getClip();
        g.clipRect(r.x + 1, r.y + 1, r.width - 2, r.height - 2);
        prepareArrayView(state, l);
        int capacity = arrayCapacity(state, l);
        boolean paged = state.length() > capacity;
        int end = (int) Math.min(state.length(), (long) firstArrayIndex + capacity);
        text(game.world.level.arraySandbox() ? "SANDBOX POLE" : "PRÁCE S POLEM", r.x + 22, r.y + 31, 16, WHITE, true);
        text(state.complete ? "DOKONČENO" : game.running ? "PROGRAM BĚŽÍ" : "PŘIPRAVENO / PAUZA",
                r.x + 22, r.y + 54, 11, state.complete ? GREEN : MUTED, false);
        if (game.canAdvanceLevel()) button(l.nextLevel, "Další level", "", GREEN, BG);
        int statWidth = (r.width - 48) / 3;
        text("DÉLKA  " + state.length(), r.x + 24, r.y + 85, 11, MUTED, true);
        text("ČTENÍ  " + state.reads, r.x + 24 + statWidth, r.y + 85, 11, BLUE, true);
        text("ZÁPISY  " + state.writes, r.x + 24 + statWidth * 2, r.y + 85, 11, ORANGE, true);
        int x = r.x + 28, width = r.width - 56;
        int top = r.y + 115, bottom = r.y + r.height - 135;
        fill(x - 10, top - 10, width + 20, Math.max(36, bottom - top + 20), new Color(0x101a21), 10);
        if (state.length() > 0) {
            int columns = arrayColumns(state, l);
            int rows = (end - firstArrayIndex + columns - 1) / columns;
            int rowHeight = Math.max(30, Math.min(108, (bottom - top) / rows));
            int firstY = top + Math.max(0, (bottom - top - rows * rowHeight) / 2);
            double slot = (double) width / columns;
            for (int i = firstArrayIndex; i < end; i++) {
                int position = i - firstArrayIndex;
                int px = (int) (x + (position % columns) * slot + 4), py = firstY + (position / columns) * rowHeight;
                int cellWidth = Math.max(16, (int) slot - 8), cellHeight = Math.max(16, rowHeight - 33);
                boolean selected = i == state.left;
                Color color = state.complete ? GREEN : selected ? state.writing ? ORANGE : BLUE : new Color(0x42685b);
                mono("[" + i + "]", px + 3, py + 11, 11, selected ? color : MUTED);
                fill(px, py + 19, cellWidth, cellHeight, color, 7);
                String value = "" + state.value(i);
                int size = Math.max(8, Math.min(24, Math.min(cellHeight / 2, (int) (cellWidth / (value.length() * .66 + .5)))));
                double textWidth = textRenderer.measure(g, FontBook.Face.CODE, size, value);
                mono(value, px + (cellWidth - textWidth) / 2, py + 19 + cellHeight / 2.0 + size / 3.0, size,
                        selected || state.complete ? BG : WHITE);
                if (selected && state.animating()) fill(px, py + 19 + cellHeight - 4, (int) (cellWidth * state.progress), 4, WHITE, 2);
            }
        } else wrapped(state.arrayId > 0 ? "Pole je prázdné (délka 0)." : "Vytvoř int[] ve svém programu. Pole se zobrazí po spuštění.", x + 8, top + 28, width - 16, 13, 21, MUTED, false);
        int infoY = r.y + r.height - 108;
        text("Čtení prvku", r.x + 24, infoY, 11, BLUE, true);
        text("Zápis nové hodnoty", r.x + Math.max(150, r.width / 2), infoY, 11, ORANGE, true);
        wrapped(state.operation, r.x + 24, infoY + 25, r.width - 48, 12, 17, state.complete ? GREEN : WHITE, false);
        String caption = game.world.level.arraySandbox()
                ? "Pole #" + state.arrayId + " · délka " + state.length() + (paged ? " · indexy " + firstArrayIndex + "–" + (end - 1) : "")
                : "Cílové hodnoty: " + game.world.level.expectedArray();
        text(shortened(caption, r.width - (paged ? 180 : 48), 11), r.x + 24, r.y + r.height - 20, 11, MUTED, false);
        if (paged) {
            button(l.arrayPrevious, "←", "", firstArrayIndex > 0 ? BORDER : PANEL, firstArrayIndex > 0 ? WHITE : MUTED);
            button(l.arrayNext, "→", "", end < state.length() ? BORDER : PANEL, end < state.length() ? WHITE : MUTED);
        }
        g.setClip(clip);
    }

    private void searchCanvas(Game game, UiLayout l) {
        Rectangle r = l.board;
        SortState state = game.sorting;
        panel(r, PANEL);
        Shape clip = g.getClip();
        g.clipRect(r.x + 1, r.y + 1, r.width - 2, r.height - 2);
        text("BINARY SEARCH", r.x + 22, r.y + 31, 16, WHITE, true);
        if (game.canAdvanceLevel()) button(l.nextLevel, "Další level", "", GREEN, BG);
        text("HLEDÁME  " + state.target, r.x + 22, r.y + 63, 21, GREEN, true);
        text("ČTENÍ  " + state.reads, r.x + 22, r.y + 89, 11, BLUE, true);
        text("VÝSLEDEK  " + (state.reportedResult < 0 ? "−1" : state.reportedResult), r.x + r.width / 2, r.y + 89, 11, MUTED, true);
        int x = r.x + 28, width = r.width - 56;
        int rowHeight = Math.min(68, Math.max(42, r.height / 9));
        int y = Math.max(r.y + 136, r.y + r.height / 2 - rowHeight / 2);
        fill(x - 10, y - 30, width + 20, rowHeight + 76, new Color(0x101a21), 10);
        if (state.length() > 0) {
            double slot = (double) width / state.length();
            double t = state.progress * state.progress * (3 - 2 * state.progress);
            double low = state.fromLow + (state.searchLow - state.fromLow) * t;
            double high = state.fromHigh + (state.searchHigh - state.fromHigh) * t;
            if (high >= low) fill((int) (x + low * slot), y - 9, (int) ((high - low + 1) * slot - 3), rowHeight + 18, new Color(0x365149), 6);
            for (int i = 0; i < state.length(); i++) {
                int px = (int) (x + i * slot + 2), cellWidth = Math.max(3, (int) slot - 6);
                boolean excluded = i < state.searchLow || i > state.searchHigh;
                boolean current = i == state.probe;
                Color color = current ? state.foundProbe == i ? GREEN : ORANGE
                        : excluded ? new Color(0x243039) : new Color(0x42685b);
                fill(px, y, cellWidth, rowHeight, color, 5);
                int size = Math.max(8, Math.min(17, (int) (slot / 2.6)));
                mono("" + state.value(i), px + 4, y + rowHeight / 2 + size / 3.0, size,
                        current ? BG : excluded ? MUTED : WHITE);
                mono("" + i, px + 4, y + rowHeight + 26, Math.max(8, Math.min(11, (int) slot / 3)), MUTED);
                if (state.visited[i]) {
                    g.setColor(current ? ORANGE : BLUE); g.fillOval(px + cellWidth / 2 - 3, y - 24, 6, 6);
                }
            }
        }
        if (r.height >= 400) {
            text("Modrý bod: kontrolovaný index", r.x + 22, y + rowHeight + 80, 11, BLUE, false);
            text("Zelený pás: zbývající interval", r.x + 22, y + rowHeight + 101, 11, MUTED, false);
        }
        String interval = state.searchLow <= state.searchHigh ? "Interval [" + state.searchLow + ", " + state.searchHigh + "]" : "Interval je prázdný";
        text(interval + "  ·  " + (state.complete ? "DOKONČENO" : game.running ? "BĚŽÍ" : "PŘIPRAVENO / PAUZA"),
                r.x + 22, r.y + r.height - 62, 11, state.complete ? GREEN : MUTED, false);
        wrapped(state.operation, r.x + 22, r.y + r.height - 36, r.width - 44, 12, 17, state.complete ? GREEN : WHITE, false);
        g.setClip(clip);
    }

    private void sortingCanvas(Game game, UiLayout l) {
        Rectangle r = l.board;
        SortState state = game.sorting;
        panel(r, PANEL);
        Shape clip = g.getClip();
        g.clipRect(r.x + 1, r.y + 1, r.width - 2, r.height - 2);
        text("BUBBLE SORT", r.x + 22, r.y + 31, 16, WHITE, true);
        String mode = state.complete ? "SEŘAZENO" : game.running ? "TŘÍDĚNÍ BĚŽÍ" : "PŘIPRAVENO / PAUZA";
        text(mode, r.x + 22, r.y + 54, 11, state.complete ? GREEN : MUTED, false);
        if (game.canAdvanceLevel()) button(l.nextLevel, "Další level", "", GREEN, BG);
        int inset = 24, statWidth = (r.width - inset * 2) / 3;
        text("PRVKY  " + state.length(), r.x + inset, r.y + 85, 11, MUTED, true);
        text("ČTENÍ  " + state.reads, r.x + inset + statWidth, r.y + 85, 11, BLUE, true);
        text("ZÁPISY  " + state.writes, r.x + inset + statWidth * 2, r.y + 85, 11, ORANGE, true);
        int x = r.x + 28, top = r.y + 117, width = r.width - 56;
        int baseline = r.y + r.height - 100, height = Math.max(30, baseline - top);
        fill(x - 10, top - 10, width + 20, height + 47, new Color(0x101a21), 10);
        g.setStroke(new BasicStroke(1)); g.setColor(BORDER);
        for (int line = 0; line < 4; line++) {
            int y = top + height * line / 3;
            g.drawLine(x, y, x + width, y);
        }
        if (state.length() > 0) {
            double slot = (double) width / state.length(), gap = Math.min(10, slot * 0.2);
            int barWidth = Math.max(2, (int) (slot - gap));
            int fontSize = Math.max(8, Math.min(16, (int) (slot / 2.2)));
            for (int i = 0; i < state.length(); i++) {
                boolean selected = i == state.left || i == state.right;
                double position = i;
                int barX = (int) (x + position * slot + gap / 2);
                int barHeight = Math.max(8, (int) (state.displayValue(i) / state.maximum() * (height - 25)));
                Color color = state.complete ? GREEN : selected ? state.animating() ? ORANGE : BLUE : new Color(0x658f77);
                fill(barX, baseline - barHeight, barWidth, barHeight, color, 5);
                mono("" + state.value(i), barX + 2, baseline - barHeight - 8, fontSize, selected ? WHITE : MUTED);
                if (slot >= 18) mono("" + i, x + i * slot + gap / 2 + 2, baseline + 23, 10, MUTED);
            }
        }
        wrapped(state.operation, r.x + 22, r.y + r.height - 49, r.width - 44, 12, 17,
                state.complete ? GREEN : WHITE, false);
        g.setClip(clip);
    }

    private void board(Game game, UiLayout l) {
        Warehouse w = game.world;
        Rectangle r = l.board;
        panel(r, PANEL);
        text((w.level.visitsTiles() || w.level.positionGoal() != null ? "MAPA " : "SKLAD ") + w.level.width() + "×" + w.level.height(), r.x + 18, r.y + 31, 11, WHITE, true);
        text(w.complete() ? "DOKONČENO" : game.running ? "V PROVOZU" : "POZASTAVENO", r.x + 148, r.y + 31, 11, GREEN, true);
        if (game.canAdvanceLevel()) button(l.nextLevel, "Další level", "", GREEN, BG);
        int columns = w.level.width(), rows = w.level.height();
        UiLayout.BoardLayout geometry = l.warehouse(w.level);
        orderPanel(w, geometry.orderPanel());
        inventoryPanel(w, geometry.inventoryPanel());
        double cell = geometry.cell();
        int gx = geometry.gridX(), gy = geometry.gridY();
        fill(gx - 8, gy - 8, (int) (cell * columns) + 16, (int) (cell * rows) + 16, new Color(0x0d171d), 12);
        for (int x = 0; x < columns; x++) mono("" + x, gx + (x + 0.35) * cell, gy - 13, 11, MUTED);
        for (int y = 0; y < rows; y++) {
            mono("" + y, gx - 23, gy + (y + 0.55) * cell, 11, MUTED);
            for (int x = 0; x < columns; x++) {
                int px = (int) Math.round(gx + x * cell), py = (int) Math.round(gy + y * cell);
                int size = Math.max(1, (int) Math.round(cell) - 2);
                fill(px, py, size, size, new Color(0x213039), 5);
                if (w.blocked(x, y)) obstacle(px, py, size);
            }
        }
        if (w.level.positionGoal() != null) {
            int px = (int) (gx + w.level.positionGoal().x() * cell), py = (int) (gy + w.level.positionGoal().y() * cell);
            fill(px + 2, py + 2, (int) cell - 5, (int) cell - 5, new Color(0x244459), 7);
            g.setColor(BLUE); g.setStroke(new BasicStroke(2));
            g.drawOval(px + (int) (cell * .23), py + (int) (cell * .14), (int) (cell * .5), (int) (cell * .5));
            text("CÍL", px + 5, py + (int) cell - 6, Math.max(8, Math.min(11, (int) (cell / 4))), BLUE, true);
        }
        if (w.level.dock()!=null) {
            int dockX = (int) (gx + w.level.dock().x() * cell), dockY = (int) (gy + w.level.dock().y() * cell);
            fill(dockX + 2, dockY + 2, (int) cell - 5, (int) cell - 5, new Color(0x34482f), 7);
            g.setStroke(new BasicStroke(2)); g.setColor(new Color(0x71924f));
            g.drawLine(dockX + 8, dockY + (int) cell - 12, dockX + (int) cell - 12, dockY + 8);
            text("DOCK", dockX + 5, dockY + (int) cell - 6, Math.max(8, Math.min(11, (int) (cell / 4))), GREEN, true);
        }
        if (w.level.inbound() != null) {
            int px = (int) (gx + w.level.inbound().x() * cell), py = (int) (gy + w.level.inbound().y() * cell);
            fill(px + 2, py + 2, (int) cell - 5, (int) cell - 5, new Color(0x244459), 6);
            if (w.incoming != null) {
                int size = Math.max(6, (int) (cell * 0.46));
                fill(px + (int) (cell-size)/2, py + 3, size, size, cargoColor(w.incoming), 3);
                g.setColor(WHITE); g.drawLine(px + (int) cell/2, py + 4, px + (int) cell/2, py + size);
            }
            text(cell < 42 ? "IN" : "PŘÍJEM", px + 2, py + (int) cell - 4, Math.max(8, Math.min(10, (int) (cell / 5))), BLUE, true);
        }
        for (Level.Shelf shelf : w.level.shelves()) station(gx + shelf.cell().x() * cell, gy + shelf.cell().y() * cell,
                cell, cargoColor(shelf.type()), w.stock(shelf), w.level.endless());
        robot(gx + game.robotX * cell, gy + game.robotY * cell, cell, w.cargo);
        if (w.complete()) {
            int width = Math.min(430, r.width - 32), x = r.x + (r.width - width) / 2, y = r.y + (r.height - 94) / 2;
            panel(new Rectangle(x, y, width, 94), new Color(0x172a25));
            text("ÚROVEŇ SPLNĚNA", x + 22, y + 37, 24, GREEN, true);
            text(game.canAdvanceLevel() ? "Pokračuj tlačítkem Další level nebo F5."
                    : w.level.positionGoal() != null ? "Robot dorazil do cíle " + w.level.positionGoal() + "."
                    : "Doručeno " + w.delivered() + " zásilek.", x + 22, y + 67, 12, WHITE, false);
        }
    }

    private void obstacle(int x, int y, int size) {
        Graphics2D original = g;
        g = (Graphics2D) g.create();
        try {
            g.translate(x, y); g.scale(size / 70.0, size / 70.0);
            fill(6, 11, 60, 56, new Color(0, 0, 0, 95), 7);
            fill(5, 5, 60, 58, new Color(0x46505a), 6);
            fill(9, 8, 52, 5, new Color(0x7b8790), 2);
            fill(10, 17, 50, 40, new Color(0x202a31), 3);

            // Výstražný pás je čitelný i při malých políčkách a vyšším DPI.
            Shape clip = g.getClip();
            g.clip(new java.awt.geom.RoundRectangle2D.Double(10, 18, 50, 16, 3, 3));
            g.setColor(new Color(0xffcd4d)); g.fillRect(10, 18, 50, 16);
            g.setColor(new Color(0x171d22));
            for (int stripe = 0; stripe < 90; stripe += 18) {
                g.fillPolygon(new int[]{stripe, stripe + 9, stripe - 7, stripe - 16}, new int[]{18, 18, 34, 34}, 4);
            }
            g.setClip(clip);

            // Zpevněná ocelová výplň a široké patky odlišují zábranu od police.
            g.setColor(new Color(0x63717c));
            g.setStroke(new BasicStroke(4, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
            g.drawLine(18, 40, 52, 51); g.drawLine(18, 51, 52, 40);
            fill(7, 62, 15, 5, new Color(0x10191f), 2);
            fill(48, 62, 15, 5, new Color(0x10191f), 2);
            g.setColor(new Color(0xa0acb3));
            for (int boltX : new int[]{12, 56}) for (int boltY : new int[]{10, 57}) g.fillOval(boltX - 2, boltY - 2, 4, 4);
            g.setColor(new Color(0x77848d)); g.setStroke(new BasicStroke(1.5f));
            g.drawRoundRect(5, 5, 60, 58, 6, 6);
        } finally { g.dispose(); g = original; }
    }

    private void station(double x, double y, double cell, Color color, long stock, boolean unlimited) {
        Graphics2D original = g;
        g = (Graphics2D) g.create();
        g.translate(x, y); g.scale(cell / 70, cell / 70);
        fill(5, 6, 58, 58, new Color(0x41515c), 10);
        fill(10, 12, 48, 19, new Color(0x142028), 3);
        for (int i = 0; i < 4; i++) {
            fill(14 + i * 10, 15, 7, 13, color, 2);
            fill(14 + i * 10, 15, 2, 13, WHITE, 1);
        }
        fill(10, 36, 48, 20, new Color(0x1b2931), 3);
        fill(15, 40, 10, 11, color, 2);
        mono(stock > 999 ? "999+" : "" + stock, 32, 51, stock > 999 ? 9 : 12, WHITE);
        if (unlimited) mono("∞", 50, 11, 11, color);
        g.dispose(); g = original;
    }

    private void robot(double x, double y, double cell, String cargo) {
        Graphics2D original = g;
        g = (Graphics2D) g.create();

        try {
            g.translate(x, y);
            g.scale(cell / 70, cell / 70);

            // Tělo
            fill(5, 10, 60, 54, GREEN, 15);

            // Displej
            fill(12, 17, 46, 29, BG, 9);

            // Oči
            fill(21, 27, 8, 7, GREEN, 3);
            fill(41, 27, 8, 7, GREEN, 3);

            // Identifikace
            mono("01", 29, 57, 11, BG);

            // Náklad
            if (cargo != null) {
                fill(46, 44, 21, 21,
                        cargoColor(cargo), 5);
            }

        } finally {
            g.dispose();
            g = original;
        }
    }

    private void orderPanel(Warehouse w, Rectangle r) {
        panel(r, new Color(0x142027));
        if (w.level.positionGoal() != null) {
            text("CÍL BLUDIŠTĚ", r.x+10, r.y+23, 12, BLUE, true);
            mono(w.level.positionGoal().toString(), r.x+10, r.y+48, 14, WHITE);
            if (r.height>90) text(w.atGoal() ? "V cíli" : "Najdi cestu", r.x+10, r.y+74, 12, MUTED, false);
            return;
        }
        if (w.level.visitsTiles()) {
            text("PRŮCHOD MAPOU",r.x+10,r.y+23,12,GREEN,true);
            text(w.printedTiles.size()+" / "+w.level.tileGoal()+" políček",r.x+10,r.y+48,13,WHITE,true);
            if(r.height>90) bar(r.x+10,r.y+64,r.width-20,w.printedTiles.size()/(double)w.level.tileGoal(),GREEN);
            return;
        }
        boolean tight = r.height < 185;
        int inset = tight ? 10 : 14;
        text(shortened("ZAKÁZKA" + (w.level.endless() ? " " + (w.completedOrders + 1) : ""), r.width - 2 * inset, tight ? 13 : 16),
                r.x + inset, r.y + (tight ? 20 : 26), tight ? 13 : 16, GREEN, true);
        int top = tight ? 32 : 64;
        if (r.height >= 150) {
            text("Odesláno " + w.delivered() + " / " + w.totalGoal(), r.x + inset, r.y + (tight ? 39 : 48), 11, MUTED, false);
            if (tight) top = 49;
        }
        List<ShelfType> types = w.level.orderTypes();
        if (types.isEmpty()) {
            int y = r.y + top + 15;
            if (w.level.checksOutput()) {
                text("Výpis do konzole", r.x + inset, y, 12, WHITE, true); y += 24;
            }
            if (w.level.cargoGoal() != null) wrapped("Náklad: " + ShelfType.fromKey(w.level.cargoGoal()).label(),
                    r.x + inset, y, r.width-2*inset, 12, 18, ORANGE, false);
            return;
        }
        int footer = tight ? 0 : w.level.endless() ? 48 : 30;
        int rowHeight = Math.min(48, Math.max(14, (r.height - top - footer - 8) / types.size()));
        int fontSize = rowHeight < 23 ? 10 : r.width < 200 ? 11 : 13;
        for (int i = 0; i < types.size(); i++) {
            ShelfType type = types.get(i);
            int y = r.y + top + fontSize + i * rowHeight;
            int goal = w.goal(type.key()), delivered = Math.min(goal, w.delivered(type.key()));
            Color color = goal > 0 ? cargoColor(type.key()) : MUTED;
            String amount = delivered + "/" + goal;
            double amountWidth = textRenderer.measure(g, FontBook.Face.CODE, fontSize, amount);
            text(shortened(type.label(), (int) (r.width - 2 * inset - amountWidth - 8), fontSize), r.x + inset, y, fontSize, color, true);
            mono(amount, r.x + r.width - inset - amountWidth, y, fontSize, WHITE);
            if (rowHeight >= 23) bar(r.x + inset, y + 7, r.width - 2 * inset, goal == 0 ? 0 : delivered / (double) goal, color);
        }
        if (!tight) {
            if (w.level.endless()) text("Splněno: " + w.completedOrders, r.x + inset, r.y + r.height - 34, 11, MUTED, false);
            text("Dock " + w.level.dock(), r.x + inset, r.y + r.height - 16, 11, MUTED, false);
        }
    }

    private void inventoryPanel(Warehouse w, Rectangle r) {
        panel(r, new Color(0x142027));
        if (w.level.positionGoal() != null) {
            text("POLOHA ROBOTA", r.x+10, r.y+23, 12, GREEN, true);
            mono("["+w.x+","+w.y+"]", r.x+10, r.y+48, 14, WHITE);
            if (r.height>90) text("Kroky: "+w.steps, r.x+10, r.y+74, 12, MUTED, false);
            return;
        }
        if (w.level.visitsTiles()) {
            text("VÝPIS NA POLÍČKU",r.x+10,r.y+23,12,GREEN,true);
            boolean returned=w.level.visitPerimeter() && w.printedTiles.size()==w.level.tileGoal()
                    && w.x==w.level.start().x() && w.y==w.level.start().y();
            mono(returned ? "HOTOVO" : "tile("+w.x+","+w.y+")",r.x+10,r.y+48,14,WHITE);
            return;
        }
        boolean tight = r.height < 185;
        int inset = tight ? 10 : 14;
        text("SKLAD", r.x + inset, r.y + (tight ? 20 : 26), tight ? 13 : 16, GREEN, true);
        var types = w.level.inventoryTypes();
        if (types.isEmpty()) { text("Bez polic", r.x + inset, r.y + 52, 12, MUTED, false); return; }
        int top = tight ? 32 : 48;
        int rowHeight = Math.min(48, Math.max(14, (r.height - top - 8) / types.size()));
        int fontSize = rowHeight < 23 ? 10 : r.width < 200 ? 11 : 13;
        for (int i = 0; i < types.size(); i++) {
            ShelfType type = types.get(i);
            int baseline = r.y + top + fontSize + i * rowHeight;
            String count = Long.toString(w.stockCount(type.key()));
            int countSize = Math.max(9, Math.min(fontSize + (tight ? 0 : 2), (int) (r.width * 0.48 / (count.length() * 0.62))));
            double countWidth = textRenderer.measure(g, FontBook.Face.CODE, countSize, count);
            Color color = cargoColor(type.key());
            fill(r.x + inset, baseline - 9, 8, 10, color, 2);
            int labelX = r.x + inset + 16;
            text(shortened(type.label(), (int) (r.width - 2 * inset - 24 - countWidth), fontSize), labelX, baseline, fontSize, color, true);
            mono(count, r.x + r.width - inset - countWidth, baseline, countSize, WHITE);
        }
    }

    private void editor(Game game, UiLayout l) {
        Rectangle r = l.editor;
        panel(r, new Color(0x151f25));
        text("ŘÍDICÍ PROGRAM", r.x + 18, r.y + 24, 11, MUTED, true);
        text(game.saved ? "Uloženo" : "Změněno", r.x + r.width - 86, r.y + 24, 11, game.saved ? GREEN : ORANGE, false);
        if (!l.shortEditor && game.tab != Game.EditorTab.DOCUMENTATION) {
            fill(l.example.x, l.example.y, l.example.width, l.example.height, new Color(0x1e2a31), 9);
            mono(game.tab == Game.EditorTab.CONSOLE ? "Výstup programu" : "</> Main.java", l.example.x + 14, l.example.y + 25, 14, WHITE);
        }
        text("Java program", l.source.x + 3, l.source.y + 21, 13, game.tab == Game.EditorTab.JAVA ? GREEN : MUTED, true);
        text("Zadání", l.assignment.x + 3, l.assignment.y + 21, 13, game.tab == Game.EditorTab.ASSIGNMENT ? GREEN : MUTED, true);
        text("Dokumentace", l.documentation.x + 3, l.documentation.y + 21, 13, game.tab == Game.EditorTab.DOCUMENTATION ? GREEN : MUTED, true);
        text("Konzole", l.console.x + 3, l.console.y + 21, 13, game.tab == Game.EditorTab.CONSOLE ? GREEN : MUTED, true);
        g.setColor(BORDER); g.setStroke(new BasicStroke(1));
        g.drawLine(l.source.x, l.source.y + 30, l.console.x + l.console.width, l.source.y + 30);
        g.setColor(GREEN);
        Rectangle active = switch (game.tab) {
            case JAVA -> l.source;
            case ASSIGNMENT -> l.assignment;
            case DOCUMENTATION -> l.documentation;
            case CONSOLE -> l.console;
        };
        g.drawLine(active.x, active.y + 30, active.x + active.width - 8, active.y + 30);
        switch (game.tab) {
            case JAVA -> code(game, l);
            case ASSIGNMENT -> assignment(game, l);
            case DOCUMENTATION -> documentation(game, l);
            case CONSOLE -> console(game, l);
        }
        Shape clip = g.getClip();
        g.clipRect(l.play.x, l.play.y - 39, r.width - 36, 33);
        wrapped(game.status, l.play.x, l.play.y - 27, r.width - 36, 12, 16, game.levelComplete() ? GREEN : MUTED, false);
        g.setClip(clip);
        button(l.play, game.canAdvanceLevel() ? "Další level" : game.running ? "Pozastavit" : "Spustit program", "F5", GREEN, BG);
        button(l.step, "Krok", "F6", new Color(0x293740), WHITE);
        button(l.reset, "Reset", "", new Color(0x202c33), MUTED);
        button(l.resetCode, "Reset kódu", "", new Color(0x293740), game.compiling() ? MUTED : WHITE);
        speedSlider(game, l);
    }

    private void console(Game game, UiLayout l) {
        Shape clip = g.getClip(); g.clip(l.code);
        int x=l.code.x+8, width=l.code.width-16, y=l.code.y+24-pageScroll;
        text("STANDARDNÍ VÝSTUP", x, y, 12, GREEN, true); y+=28;
        String output=game.world.consoleOutput;
        if (output.isEmpty()) y=wrapped("Program zatím nic nevypsal.",x,y,width,14,22,MUTED,false);
        else for (String line:output.split("\n",-1)) y=consoleLine(line,x,y,width,WHITE);
        if (game.world.level.checksOutput()) {
            y+=32;
            text("OČEKÁVANÝ VÝSTUP",x,y,12,MUTED,true); y+=26;
            for (String line:game.world.level.expectedOutput().split("\n",-1))
                y=consoleLine(line,x,y,width,ORANGE);
        }
        if (game.world.level.visitsTiles()) {
            y+=26;
            y=wrapped(game.world.level.visitPerimeter() ? "Na obvodu tile(x,y), po návratu HOTOVO."
                    : "Každé políčko jednou: tile(x,y)",x,y,width,14,22,ORANGE,false);
            y=wrapped("Ověřeno "+game.world.printedTiles.size()+" / "+game.world.level.tileGoal()+" políček",x,y+8,width,14,22,GREEN,false);
        }
        finishPage(l.code,y+20); g.setClip(clip);
    }

    private int consoleLine(String line,int x,int y,int width,Color color) {
        line=line.replace("\t","    ");
        int columns=Math.max(1,(int)(width/textRenderer.measure(g,FontBook.Face.CODE,14,"M")));
        if(line.isEmpty()) return y+22;
        for(int start=0;start<line.length();) {
            int end=Math.min(line.length(),start+columns);
            if(end<line.length() && Character.isHighSurrogate(line.charAt(end-1))) end++;
            mono(line.substring(start,end),x,y,14,color); y+=22; start=end;
        }
        return y;
    }

    private void speedSlider(Game game, UiLayout l) {
        Rectangle r = l.speed;
        int cy = r.y + 10;
        text(game.world.level.arrayPractice() ? "RYCHLOST OPERACÍ POLE" : game.world.level.searching() ? "RYCHLOST HLEDÁNÍ" : game.world.level.sorting() ? "RYCHLOST TŘÍDĚNÍ" : "RYCHLOST ROBOTA", r.x - 8, r.y - 8, 10, MUTED, true);
        text(game.speedMultiplier + "×", r.x + r.width - 20, r.y - 8, 12, GREEN, true);
        fill(r.x, cy - 2, r.width, 4, BORDER, 4);
        int thumb = r.x + (int) Math.round((game.speedMultiplier - 1) * r.width / 9.0);
        fill(r.x, cy - 2, thumb - r.x, 4, GREEN, 4);
        for (int i = 0; i < 10; i++) {
            int x = r.x + (int) Math.round(i * r.width / 9.0);
            g.setColor(MUTED); g.fillRect(x, cy + 6, 1, 3);
        }
        g.setColor(GREEN); g.fillOval(thumb - 7, cy - 7, 14, 14);
        text("1×", r.x, r.y + 30, 10, MUTED, false);
        text("10×", r.x + r.width - 20, r.y + 30, 10, MUTED, false);
    }

    private void code(Game game, UiLayout l) {
        CodeEditor e = game.editor;
        charWidth = textRenderer.measure(g, FontBook.Face.CODE, 15, "M");
        int horizontal = horizontal(l, e);
        String[] rows = e.lines();
        e.scroll = Math.clamp(e.scroll, 0, Math.max(0, rows.length - l.visibleRows()));
        Shape clip = g.getClip(); g.clip(l.code);
        int offset = 0;
        for (int i = 0; i < rows.length; i++) {
            if (i >= e.scroll && i < e.scroll + l.visibleRows()) {
                int y = l.codeBaseline() + (i - e.scroll) * l.lineHeight;
                if (game.activeLine == i + 1) fill(l.code.x, y - 18, l.code.width, l.lineHeight, new Color(0x2a3c25), 4);
                mono(String.format("%02d", i + 1), l.code.x + 4, y, 12, game.activeLine == i + 1 ? GREEN : new Color(0x718a99));
                Shape rowClip = g.getClip(); g.clipRect(l.codeX() - 2, l.code.y, l.code.width - 42, l.code.height);
                int start = Math.max(0, Math.min(e.anchor, e.caret) - offset);
                int end = Math.min(rows[i].length(), Math.max(e.anchor, e.caret) - offset);
                if (e.selected() && start <= end && end >= 0) {
                    fill((int) Math.round(l.codeX() + (start - horizontal) * charWidth), y - 18,
                            (int) Math.ceil(Math.max(charWidth / 2, (end - start) * charWidth)), l.lineHeight, new Color(0x3d525f), 1);
                }
                syntax(rows[i], l.codeX() - horizontal * charWidth, y);
                if (e.focused && e.line() == i && !game.running && (System.currentTimeMillis() / 500) % 2 == 0) {
                    fill((int) Math.round(l.codeX() + (e.column() - horizontal) * charWidth), y - 17, 1, 20, GREEN, 0);
                }
                g.setClip(rowClip);
            }
            offset += rows[i].length() + 1;
        }
        if (game.completion.isOpen() && !game.running) completion(game, l);
        g.setClip(clip);
    }

    private void completion(Game game, UiLayout l) {
        CodeCompletion completion = game.completion;
        CompletionPopup popup = completionPopup(game.editor, l, completion);
        Rectangle r = popup.bounds();
        fill(r.x, r.y, r.width, r.height, new Color(0x22343e), 6);
        g.setColor(new Color(0x647d88)); g.setStroke(new BasicStroke(1));
        g.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1, 6, 6);
        Shape clip = g.getClip(); g.clipRect(r.x + 1, r.y + 1, r.width - 2, r.height - 2);
        for (int row = 0; row < popup.rows(); row++) {
            int index = popup.first() + row;
            int y = r.y + row * popup.rowHeight();
            boolean selected = index == completion.selected();
            if (selected) fill(r.x + 2, y + 1, r.width - 4, popup.rowHeight() - 1, new Color(0x3c5330), 3);
            mono(completion.items().get(index).label(), r.x + 10, y + 17, 13, selected ? GREEN : WHITE);
        }
        int footerY = r.y + popup.rows() * popup.rowHeight();
        if (footerY + 30 < r.y + r.height) {
            text(shortened(completion.items().get(completion.selected()).description(), r.width - 18, 11), r.x + 9, footerY + 14, 11, MUTED, false);
            text("Šipky: výběr   Enter/Tab: vložit   Esc: zavřít", r.x + 9, footerY + 29, 10, MUTED, false);
        }
        g.setClip(clip);
    }

    private String shortened(String value, int width, int size) {
        if (textRenderer.measure(g, FontBook.Face.UI, size, value) <= width) return value;
        while (!value.isEmpty() && textRenderer.measure(g, FontBook.Face.UI, size, value + "…") > width) value = value.substring(0, value.length() - 1);
        return value + "…";
    }

    private void syntax(String row, double x, int y) {
        syntax(row, x, y, 15, charWidth);
    }

    private void syntax(String row, double x, int y, int size, double cellWidth) {
        // Každý znak kreslíme právě jednou. Překreslování tokenů přes původní text rozmazávalo okraje.
        Matcher matcher = TOKENS.matcher(row);
        int end = 0;
        while (matcher.find()) {
            mono(row.substring(end, matcher.start()), x + end * cellWidth, y, size, new Color(0xc6d4da));
            String token = matcher.group();
            Color color = token.startsWith("//") ? new Color(0x8ba4b2)
                    : Character.isDigit(token.charAt(0)) || token.startsWith("\"") || token.startsWith("'") ? ORANGE
                    : KEYWORDS.contains(token) ? PURPLE : GREEN;
            mono(token, x + matcher.start() * cellWidth, y, size, color);
            end = matcher.end();
        }
        mono(row.substring(end), x + end * cellWidth, y, size, new Color(0xc6d4da));
    }

    private void documentation(Game game, UiLayout l) {
        Shape clip = g.getClip(); g.clip(l.code);
        var chapters = game.documentation.chapters();
        if (chapters.isEmpty()) {
            wrapped(game.documentation.errors().isEmpty() ? "Zatím nejsou k dispozici žádné kapitoly dokumentace."
                    : "Dokumentaci se nepodařilo načíst: " + game.documentation.errors().getFirst(),
                    l.code.x + 8, l.code.y + 26, l.code.width - 16, 16, 26, MUTED, false);
        }
        for (int i = 0; i < chapters.size(); i++) {
            Rectangle row = documentationRow(l, i);
            if (!row.intersects(l.code)) continue;
            var chapter = chapters.get(i);
            boolean selected = i == game.documentationSelection;
            fill(row.x, row.y, row.width, row.height, selected ? new Color(0x30432d) : new Color(0x202d35), 8);
            text(shortened(chapter.title(), row.width - 52, 16), row.x + 12, row.y + 25, 16, selected ? GREEN : WHITE, true);
            text(shortened(chapter.description(), row.width - 28, 12), row.x + 12, row.y + 49, 12, MUTED, false);
            text(">", row.x + row.width - 25, row.y + 25, 16, GREEN, true);
        }
        maxPageScroll = Math.max(0, chapters.size() * 76 + 8 - l.code.height);
        pageScroll = Math.min(pageScroll, maxPageScroll);
        g.setClip(clip);
    }

    Rectangle documentationRow(UiLayout l, int index) {
        return new Rectangle(l.code.x + 8, l.code.y + 4 + index * 76 - pageScroll, l.code.width - 16, 68);
    }
    void showDocumentationItem(int index, int count, UiLayout l) {
        maxPageScroll = Math.max(0, count * 76 + 8 - l.code.height);
        int top = 4 + index * 76, bottom = top + 68;
        if (top < pageScroll) pageScroll = top;
        else if (bottom > pageScroll + l.code.height) pageScroll = bottom - l.code.height;
        pageScroll = Math.clamp(pageScroll, 0, maxPageScroll);
    }
    private void documentationChapter(Game game, UiLayout l) {
        int listScroll = pageScroll, listMaxScroll = maxPageScroll;
        pageScroll = chapterScroll; maxPageScroll = chapterMaxScroll;
        try { drawDocumentationChapter(game, l); }
        finally {
            chapterScroll = pageScroll; chapterMaxScroll = maxPageScroll;
            pageScroll = listScroll; maxPageScroll = listMaxScroll;
        }
    }
    private void drawDocumentationChapter(Game game, UiLayout l) {
        Rectangle window = l.documentationWindow;
        panel(window, PANEL);
        button(l.documentationBack, "Seznam kapitol", "Esc", new Color(0x293740), WHITE);
        button(l.documentationClose, "Zavřít", "F1", BORDER, WHITE);
        text(shortened(game.documentationChapter.title(), window.width - 40, 24), window.x + 20, window.y + 87, 24, GREEN, true);
        levelPage(game.documentationChapter.content().isBlank() ? "Obsah kapitoly bude doplněn později." : game.documentationChapter.content(), l.documentationContent);
        text("Kolečko / šipky: posun    Esc: seznam kapitol    F1: zavřít", window.x + 20, window.y + window.height - 9, 11, MUTED, false);
        if (maxPageScroll > 0) {
            Rectangle area = l.documentationContent;
            int thumb = Math.max(24, area.height * area.height / (area.height + maxPageScroll));
            fill(area.x + area.width + 4, area.y, 4, area.height, BORDER, 4);
            fill(area.x + area.width + 4, area.y + (area.height - thumb) * pageScroll / maxPageScroll, 4, thumb, GREEN, 4);
        }
    }

    private void assignment(Game game, UiLayout l) {
        if (game.world.level.assignment() != null) {
            levelPage(game.world.level.assignment(), l);
            return;
        }
        Shape clip = g.getClip(); g.clip(l.code);
        int x = l.code.x + 8, y = l.code.y + 26 - pageScroll, width = l.code.width - 16;
        Level level = game.world.level;
        y = wrapped(level.name(), x, y, width, 21, 31, WHITE, true) + 14;
        y = wrapped(level.description(), x, y, width, 16, 26, WHITE, false) + 24;
        if (level.endless()) {
            y = wrapped("Zakázka " + (game.world.completedOrders + 1) + " • Splněno " + game.world.completedOrders
                    + " zakázek • Celkem doručeno " + game.world.totalDelivered + " zásilek.", x, y, width, 16, 26, GREEN, true) + 18;
            y = wrapped("Po dokončení přijde jiná zakázka: pro každý typ požaduje 0 až 4 balíky, alespoň jeden celkem. Požadavky vidíš vlevo od mapy a program je čte přes orderRemaining(ShelfType...). Spodní lišta ukazuje pouze zásoby ve skladu.", x, y, width, 16, 26, MUTED, false) + 18;
            y = wrapped("Police jsou pevné a mají neomezenou kapacitu. Zásoby se automaticky nedoplňují. Příjem " + level.inbound() + " nabízí další balík po vyzvednutí. Nejprve jej ulož do správné police; pak lze zásilku z police odeslat na dock. Nová zakázka zachová všechny zásoby.", x, y, width, 16, 26, MUTED, false) + 18;
            y = wrapped("Na příjmu: " + ShelfType.fromKey(game.world.incoming).label() + ". Robot nese: "
                    + (game.world.cargo == null ? "nic" : game.world.cargoType().label()) + ". Naskladněno celkem: " + game.world.totalStored + ".", x, y, width, 16, 26, BLUE, false) + 18;
        }
        for (ShelfType type : level.orderTypes()) {
            text(type.label() + ": " + Math.min(game.world.goal(type.key()), game.world.delivered(type.key())) + " / " + game.world.goal(type.key()), x, y, 16, cargoColor(type.key()), true); y += 26;
        }
        y += 10;
        y = wrapped("Start robota: " + level.start() + ". Expedice: " + level.dock() + ". Rozměry: " + level.width() + " × " + level.height() + ".", x, y, width, 16, 26, MUTED, false) + 14;
        for (Level.Shelf shelf : level.shelves()) {
            y = wrapped(ShelfType.fromKey(shelf.type()).label() + " " + shelf.cell() + ": " + game.world.stock(shelf)
                    + (level.endless() ? " zásilek, kapacita ∞." : " / " + shelf.stock() + " zásilek."), x, y, width, 16, 26, cargoColor(shelf.type()), false);
        }
        y += 18;
        String obstacles = level.obstacles().stream().sorted(java.util.Comparator.comparingInt(Level.Cell::y).thenComparingInt(Level.Cell::x))
                .map(Level.Cell::toString).collect(java.util.stream.Collectors.joining(", "));
        y = wrapped("Překážky: " + (obstacles.isEmpty() ? "žádné" : obstacles), x, y, width, 16, 26, MUTED, false) + 14;
        y = wrapped("Robot unese jednu zásilku. pick() vyzvedává, drop() odevzdává. Do překážek ani mimo sklad robot nesmí. F5 / Escape pozastaví program.", x, y, width, 16, 26, MUTED, false) + 20;
        y = wrapped("Souřadnice [0,0] jsou vlevo nahoře. move(Direction.TOP) snižuje souřadnici Y. Program spusť pomocí F5, pozastav stejnou klávesou a jednotlivé kroky proveď pomocí F6.", x, y, width, 16, 26, MUTED, false);
        finishPage(l, y);
        g.setClip(clip);
    }

    /** Celý obsah stránky určuje definice úrovně, včetně nadpisů. */
    private void levelPage(String content, UiLayout l) {
        levelPage(content, l.code);
    }
    private void levelPage(String content, Rectangle area) {
        Shape clip = g.getClip(); g.clip(area);
        int x = area.x + 8, y = area.y + 26 - pageScroll, width = area.width - 16;
        for (AssignmentText.Block block : AssignmentText.blocks(content)) {
            y = switch (block.kind()) {
                case SPACE -> y + 14;
                case HEADING -> wrapped(block.text(), x, y, width, 21, 31, GREEN, true) + 10;
                case CODE -> assignmentCode(block.text(), x, y, width);
                case TEXT -> assignmentParagraph(block.text(), x, y, width);
            };
        }
        finishPage(area, y);
        g.setClip(clip);
    }

    private int assignmentCode(String code, int x, int y, int width) {
        double cellWidth = textRenderer.measure(g, FontBook.Face.CODE, 16, "M");
        int columns = Math.max(1, (int) ((width - 24) / cellWidth));
        var rows = new ArrayList<String>();
        for (String line : code.replace("\t", "    ").split("\n", -1)) {
            if (line.isEmpty()) { rows.add(""); continue; }
            while (line.codePointCount(0, line.length()) > columns) {
                int end = line.offsetByCodePoints(0, columns);
                rows.add(line.substring(0, end)); line = line.substring(end);
            }
            rows.add(line);
        }
        int top = y - 18, height = 48 + rows.size() * 26;
        fill(x, top, width, height, new Color(0x0e181e), 8);
        g.setColor(BORDER); g.setStroke(new BasicStroke(1));
        g.drawRoundRect(x, top, width - 1, height, 8, 8);
        text("JAVA", x + 12, top + 17, 10, MUTED, true);
        Shape clip = g.getClip(); g.clipRect(x + 8, top + 23, width - 16, height - 28);
        int baseline = top + 44;
        for (String row : rows) { syntax(row, x + 12, baseline, 16, cellWidth); baseline += 26; }
        g.setClip(clip);
        return top + height + 24;
    }

    private int assignmentParagraph(String value, int x, int y, int width) {
        double cursor = x, right = x + width;
        double codeCell = textRenderer.measure(g, FontBook.Face.CODE, 16, "M");
        for (AssignmentText.Span span : AssignmentText.spans(value)) {
            // Kód držíme pohromadě, běžný text zalamujeme po slovech.
            String[] pieces = span.code() ? new String[]{span.text()} : span.text().split("(?<=\\s)|(?=\\s)");
            for (String piece : pieces) {
                if (piece.isBlank()) {
                    if (cursor > x) cursor += textRenderer.measure(g, FontBook.Face.UI, 16, " ");
                    continue;
                }
                int padding = span.code() ? 8 : 0;
                FontBook.Face face = span.code() ? FontBook.Face.CODE : FontBook.Face.UI;
                double measured = textRenderer.measure(g, face, 16, piece) + padding;
                if (cursor > x && cursor + measured > right) { cursor = x; y += 26; }
                String rest = piece;
                while (!rest.isEmpty()) {
                    int end = rest.length();
                    while (end > 0 && textRenderer.measure(g, face, 16, rest.substring(0, end)) + padding > right - cursor)
                        end = rest.offsetByCodePoints(end, -1);
                    if (end == 0) { cursor = x; y += 26; continue; }
                    String part = rest.substring(0, end);
                    double partWidth = textRenderer.measure(g, face, 16, part) + padding;
                    if (span.code()) {
                        fill((int) cursor, y - 18, (int) Math.ceil(partWidth), 24, new Color(0x273640), 4);
                        syntax(part, cursor + 4, y, 16, codeCell);
                    } else text(part, (int) Math.round(cursor), y, 16, WHITE, false);
                    cursor += partWidth;
                    rest = rest.substring(end);
                    if (!rest.isEmpty()) { cursor = x; y += 26; }
                }
            }
        }
        return y + 26;
    }

    private void levelMenu(Game game, UiLayout l) {
        fill(0, 64, l.width + 1, l.height - 63, new Color(7, 12, 16, 220), 0);
        Rectangle dialog = l.levelDialog;
        panel(dialog, PANEL);
        text("VÝBĚR ÚROVNĚ", dialog.x + 20, dialog.y + 32, 21, GREEN, true);
        boolean overview = game.materialsOverview();
        text(overview ? "Vyber kapitolu a otevři ji tlačítkem nebo Enterem." : "Vyber úroveň a potvrď tlačítkem Zapnout nebo Enterem.", dialog.x + 20, dialog.y + 60, 12, MUTED, false);
        int robotCount = (int) game.levels.levels().stream().filter(Game.LevelChapter.ROBOT::contains).count();
        int materialsCount = game.levels.levels().size() - robotCount;
        boolean robotChapter = game.levelChapter == Game.LevelChapter.ROBOT;
        button(l.levelRobot, "Robot (" + robotCount + ")", "", robotChapter ? GREEN : BORDER, robotChapter ? BG : WHITE);
        button(l.levelMaterials, "Podklady (" + materialsCount + ")", "", robotChapter ? BORDER : GREEN, robotChapter ? WHITE : BG);
        if (!robotChapter && !overview) {
            button(l.levelBack, "Zpět na kapitoly", "", BORDER, WHITE);
            text("Podklady / " + game.materialsChapter.label, l.levelBack.x + l.levelBack.width + 16, l.levelBack.y + 20, 12, MUTED, true);
        } else text(overview ? "KAPITOLY PODKLADŮ" : "ROBOT", l.levelBack.x, l.levelBack.y + 20, 12, MUTED, true);
        var choices = game.levelChoices();
        Shape clip = g.getClip(); g.clip(l.levelList);
        int first = game.firstLevelVisible();
        if (!overview && choices.isEmpty()) wrapped("V této kapitole nejsou žádné viditelné úrovně.", l.levelList.x + 12, l.levelList.y + 28, l.levelList.width - 24, 12, 20, MUTED, false);
        for (int row = 0; row < l.levelRows() && first + row < game.levelMenuSize(); row++) {
            int index = first + row;
            String name = overview ? Game.MaterialsChapter.values()[index].label : choices.get(index).name();
            Rectangle item = new Rectangle(l.levelList.x, l.levelList.y + row * l.levelRowHeight, l.levelList.width, l.levelRowHeight - 6);
            boolean selected = index == game.levelSelection;
            fill(item.x, item.y, item.width, item.height, selected ? new Color(0x30432d) : new Color(0x202d35), 8);
            text(shortened(name, item.width - (overview ? 125 : 24), 15), item.x + 12, item.y + (item.height + 15) / 2 - 2, 15, selected ? GREEN : WHITE, true);
            if (overview) {
                long count = game.levels.levels().stream().filter(Game.MaterialsChapter.values()[index]::contains).count();
                String amount = count + " úrovní";
                double amountWidth = textRenderer.measure(g, FontBook.Face.CODE, 11, amount);
                mono(amount, item.x + item.width - 14 - amountWidth, item.y + (item.height + 11) / 2 - 2, 11, MUTED);
            }
        }
        g.setClip(clip);
        String hint = game.compiling() ? "Probíhá překlad. Úroveň lze zapnout po jeho dokončení."
                : game.levels.errors().isEmpty() ? !robotChapter && !overview ? "Esc / Zpět: kapitoly · ↑ ↓ úrovně · Enter otevřít · F4 obnovit" : "← → Robot / Podklady · ↑ ↓ výběr · Enter otevřít · F4 obnovit"
                : "Chyby (" + game.levels.errors().size() + "): " + game.levels.errors().getFirst();
        text(shortened(hint, dialog.width - 40, 11), dialog.x + 20, l.levelActivate.y - 17, 11, game.levels.errors().isEmpty() ? MUTED : ORANGE, false);
        boolean unavailable = game.compiling() || game.levelMenuSize() == 0;
        button(l.levelActivate, game.compiling() ? "Počkej na překlad" : overview ? "Otevřít kapitolu" : choices.isEmpty() ? "Žádná úroveň" : "Zapnout", "Enter", unavailable ? BORDER : GREEN, unavailable ? MUTED : BG);
        button(l.levelReload, "Obnovit", "F4", BORDER, WHITE);
        button(l.levelClose, "Zavřít", "Esc", BORDER, WHITE);
    }

    private void finishPage(UiLayout l, int y) {
        finishPage(l.code, y);
    }
    private void finishPage(Rectangle area, int y) {
        maxPageScroll = Math.max(0, y + pageScroll - area.y - area.height + 20);
        pageScroll = Math.min(pageScroll, maxPageScroll);
    }

    private void button(Rectangle r, String label, String shortcut, Color bg, Color fg) {
        fill(r.x, r.y, r.width, r.height, bg, 9);
        int fontSize = r.width < 80 ? 11 : 13;
        int textWidth = (int) Math.ceil(textRenderer.measure(g, FontBook.Face.BOLD, fontSize, label));
        int x = shortcut.isEmpty() ? r.x + Math.max(4, (r.width - textWidth) / 2) : r.x + 12;
        text(label, x, r.y + (r.height + fontSize) / 2 - 2, fontSize, fg, true);
        if (!shortcut.isEmpty() && r.width - textWidth > 42) text(shortcut, r.x + r.width - 26, r.y + (r.height + 10) / 2 - 2, 10, fg, false);
    }
    private int wrapped(String value, int x, int y, int width, int size, int lineHeight, Color color, boolean bold) {
        for (String line : wrap(value, width, size, bold)) { text(line, x, y, size, color, bold); y += lineHeight; }
        return y;
    }
    private List<String> wrap(String value, int width, int size, boolean bold) {
        List<String> result = new ArrayList<>();
        for (String paragraph : value.split("\\R", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split("\\s+")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (!line.isEmpty() && textRenderer.measure(g, bold ? FontBook.Face.BOLD : FontBook.Face.UI, size, candidate) > width) { result.add(line.toString()); line.setLength(0); }
                if (!line.isEmpty()) line.append(' ');
                line.append(word);
            }
            result.add(line.toString());
        }
        return result;
    }
    private void bar(int x, int y, int width, double value, Color color) {
        fill(x, y, width, 4, new Color(0x35424a), 4);
        fill(x, y, (int) (width * Math.clamp(value, 0, 1)), 4, color, 4);
    }
    private void panel(Rectangle r, Color color) {
        fill(r.x, r.y, r.width, r.height, color, 18);
        g.setColor(BORDER); g.setStroke(new BasicStroke(1));
        g.drawRoundRect(r.x, r.y, r.width, r.height, 18, 18);
    }
    private void fill(int x, int y, int width, int height, Color color, int radius) {
        textRenderer.occlude(g, new Rectangle(x, y, width, height));
        g.setColor(color); g.fillRoundRect(x, y, width, height, radius, radius);
    }
    private void text(String value, int x, int y, int size, Color color, boolean bold) {
        textRenderer.draw(g, bold ? FontBook.Face.BOLD : FontBook.Face.UI, size, value, x, y, color);
    }
    private void mono(String value, double x, double y, int size, Color color) {
        textRenderer.draw(g, FontBook.Face.CODE, size, value, x, y, color);
    }
}
