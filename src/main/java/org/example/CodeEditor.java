package org.example;

import java.util.ArrayDeque;
import java.util.Deque;

final class CodeEditor {
    static final String EXAMPLE = starter(LevelCatalog.defaultLevel());

    static String wrapLegacyBody(String body) {
        return "import static robot.api.Robot.*;\n\npublic class Main {\n"
                + "    public static void main(String[] args) throws Exception {\n"
                + body.stripTrailing().indent(8) + "    }\n}\n";
    }

    static String migrateSavedProgram(String source) {
        // Staré uložené tělo metody zachováme a vložíme do viditelné třídy.
        boolean fullSource = java.util.regex.Pattern.compile(
                "(?m)^\\h*(?:import\\b|package\\b|(?:(?:public|final|abstract)\\h+)*class\\h+)").matcher(source).find();
        return fullSource ? source : wrapLegacyBody(source);
    }

    static String starter(Level level) {
        if (level.initialCode() != null) return level.initialCode();
        if (level.endless()) return warehouseStarter(level);
        if (level.arrayLevel()) return "public class Main {\n    public static void main(String[] args) {\n        int[] pole = {"
                + level.array().stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(", ")) + "};\n"
                + (level.searching() ? "        int cil = " + level.target() + ";\n        int vysledek = -1;\n" : "") + "\n    }\n}\n";
        return wrapLegacyBody("// " + level.name() + "\n// Start: " + level.start() + ", expedice: " + level.dock()
                + "\n// Součástky: " + level.orangeGoal() + ", elektronika: " + level.purpleGoal()
                + "\n// Umístění regálů a překážek najdeš v záložce Zadání.\n");
    }
    private static String warehouseStarter(Level level) {
        int[] shelfX = new int[4], shelfY = new int[4];
        long[] initialStock = new long[4];
        for (Level.Shelf shelf : level.shelves()) {
            int type = robot.api.Robot.ShelfType.fromKey(shelf.type()).ordinal();
            shelfX[type] = shelf.cell().x(); shelfY[type] = shelf.cell().y(); initialStock[type] = shelf.stock();
        }
        String source = wrapLegacyBody("// Příjem -> správná police -> expedice podle zakázky.\n"
                + "int[] policeX = " + java.util.Arrays.toString(shelfX).replace('[', '{').replace(']', '}') + ";\n"
                + "int[] policeY = " + java.util.Arrays.toString(shelfY).replace('[', '{').replace(']', '}') + ";\n"
                + "long[] zasoba = " + java.util.Arrays.toString(initialStock).replace('[', '{').replace(']', '}') + ";\n"
                + "ShelfType[] typy = ShelfType.values();\n"
                + "while (true) {\n"
                + "    presunNa(" + level.inbound().x() + ", " + level.inbound().y() + ");\n"
                + "    pick();\n    ShelfType balik = cargoType();\n    int typ = balik.ordinal();\n"
                + "    presunNa(policeX[typ], policeY[typ]);\n"
                + "    if (shelfType() != balik) throw new IllegalStateException(\"Nesprávná police\");\n"
                + "    drop();\n    zasoba[typ]++;\n"
                + "    for (int t = 0; t < 4; t++) {\n"
                + "        while (zasoba[t] > 0 && orderRemaining(typy[t]) > 0) {\n"
                + "            presunNa(policeX[t], policeY[t]);\n            pick();\n"
                + "            presunNa(" + level.dock().x() + ", " + level.dock().y() + ");\n            drop();\n"
                + "            zasoba[t]--;\n        }\n    }\n}");
        return source.substring(0, source.length() - 2) + """

                private static void presunNa(int x, int y) {
                    int aktualniX = robotCoordinatesX();
                    int aktualniY = robotCoordinatesY();
                    while (aktualniX < x) { move(Direction.RIGHT); aktualniX++; }
                    while (aktualniX > x) { move(Direction.LEFT); aktualniX--; }
                    while (aktualniY < y) { move(Direction.DOWN); aktualniY++; }
                    while (aktualniY > y) { move(Direction.TOP); aktualniY--; }
                }
            }
            """;
    }
    String text = EXAMPLE;
    int caret, anchor, scroll;
    int visibleRows = 20;
    boolean focused = true;
    private record Snapshot(String text, int caret, int anchor, int scroll) {}
    private final Deque<Snapshot> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    void setText(String source) { text = source; caret = anchor = scroll = 0; undo.clear(); redo.clear(); }
    void replaceText(String source) {
        source = source.replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ");
        if (source.length() > 30_000 || source.equals(text)) return;
        remember();
        text = source;
        caret = anchor = scroll = 0;
    }
    private void remember() {
        undo.push(snapshot());
        if (undo.size() > 100) undo.removeLast();
        redo.clear();
    }
    private Snapshot snapshot() { return new Snapshot(text, caret, anchor, scroll); }
    private void restore(Snapshot state) {
        text = state.text(); caret = state.caret(); anchor = state.anchor(); scroll = state.scroll();
    }
    boolean undo() {
        if (undo.isEmpty()) return false;
        redo.push(snapshot()); restore(undo.pop()); return true;
    }
    boolean redo() {
        if (redo.isEmpty()) return false;
        undo.push(snapshot()); restore(redo.pop()); return true;
    }
    String[] lines() { return text.split("\n", -1); }
    int line() { return (int) text.substring(0, caret).chars().filter(c -> c == '\n').count(); }
    int column() { return caret - text.lastIndexOf('\n', caret - 1) - 1; }
    boolean selected() { return caret != anchor; }
    void insert(String value) {
        value = value.replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ");
        int start = Math.min(caret, anchor), end = Math.max(caret, anchor);
        if (text.length() - (end - start) + value.length() > 30_000) return;
        if (start == end && value.isEmpty()) return;
        remember();
        text = text.substring(0, start) + value + text.substring(end);
        caret = anchor = start + value.length();
        reveal();
    }
    void backspace() { if (!selected() && caret > 0) anchor = caret - 1; insert(""); }
    void delete() { if (!selected() && caret < text.length()) anchor = caret + 1; insert(""); }
    void move(int position, boolean select) {
        caret = Math.clamp(position, 0, text.length());
        if (!select) anchor = caret;
        reveal();
    }
    void vertical(int delta, boolean select) { at(Math.clamp(line() + delta, 0, lines().length - 1), column(), select); }
    void at(int row, int column, boolean select) {
        String[] rows = lines();
        row = Math.clamp(row, 0, rows.length - 1);
        int position = Math.clamp(column, 0, rows[row].length());
        for (int i = 0; i < row; i++) position += rows[i].length() + 1;
        move(position, select);
    }
    void newline() {
        String row = lines()[line()];
        int spaces = 0;
        while (spaces < row.length() && row.charAt(spaces) == ' ') spaces++;
        if (text.substring(0, caret).stripTrailing().endsWith("{")) spaces += 4;
        insert("\n" + " ".repeat(spaces));
    }
    String selection() { return text.substring(Math.min(anchor, caret), Math.max(anchor, caret)); }
    void reveal() {
        if (line() < scroll) scroll = line();
        if (line() >= scroll + visibleRows) scroll = line() - visibleRows + 1;
    }
}
