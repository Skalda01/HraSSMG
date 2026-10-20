package org.example;

import java.io.Reader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import robot.api.Robot.ShelfType;

/** Neměnná definice úrovně; stav zásob a robota patří do Warehouse. */
record Level(String id, String name, String description, int width, int height, Cell start, Cell dock,
             int orangeGoal, int purpleGoal, List<Shelf> shelves, Set<Cell> obstacles, boolean endless,
             Cell inbound, int toolsGoal, int materialsGoal,
             String initialCode, String assignment, String documentation, Set<String> allowedKeywords,
             boolean sorting, List<Integer> array, boolean searching, int target,
             String expectedOutput, String cargoGoal, boolean visitAllTiles, boolean visitPerimeter,
             Cell positionGoal, Set<String> requiredKeywords, boolean isVisible,
             boolean arrayPractice, List<Integer> expectedArray, boolean arraySandbox) {
    record Cell(int x, int y) {
        @Override public String toString() { return "[" + x + "," + y + "]"; }
    }
    record Shelf(Cell cell, String type, int stock) {}
    private static final Set<String> KEYS = Set.of("id", "name", "description", "isVisible", "width", "height", "start", "dock", "goal.orange", "goal.purple", "goal.tools", "goal.materials", "goal.cargo", "goal.visitAllTiles", "goal.visitPerimeter", "goal.position", "goal.array", "expectedOutput", "shelves", "obstacles", "mode", "inbound", "initialCode", "assignment", "documentation", "allowedKeywords", "requiredKeywords", "array", "target");
    static final Set<String> API_KEYWORDS = Set.of("move", "pick", "drop", "tileType", "shelfType", "cargoType", "orderRemaining", "robotCoordinatesX", "robotCoordinatesY");
    static final Set<String> CONTROL_KEYWORDS = Set.of("for", "while", "do", "if", "else", "elseif", "switch", "break", "continue", "return", "try", "throw", "assert", "synchronized");

    static String canonicalKeyword(String name) { return name.equals("brake") ? "break" : name.equals("foreach") ? "for" : name; }
    static boolean permits(Set<String> allowed, String name) {
        name = canonicalKeyword(name);
        if (name.equals("elseif") && allowed.contains("if") && allowed.contains("else")) return true;
        return allowed.isEmpty() || (!API_KEYWORDS.contains(name) && !CONTROL_KEYWORDS.contains(name)) || allowed.contains(name);
    }

    Level {
        if (id == null || !id.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}")) throw new IllegalArgumentException("id: použij 1–64 písmen, číslic, pomlček nebo podtržítek");
        if (name == null || name.isBlank() || name.length() > 80 || name.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("name: požaduji název do 80 znaků na jednom řádku");
        if (description == null || description.length() > 2000) throw new IllegalArgumentException("description: nejvýše 2000 znaků");
        initialCode = initialCode != null && initialCode.isBlank() ? "" : content(initialCode, "initialCode", 30_000);
        assignment = content(assignment, "assignment", 30_000);
        if (expectedOutput != null) {
            expectedOutput = expectedOutput.replace("\r\n", "\n").replace('\r', '\n');
            if (expectedOutput.length() > 16_000) throw new IllegalArgumentException("expectedOutput: nejvýše 16000 znaků");
        }
        if (cargoGoal != null) { cargoGoal = cargoGoal.strip(); ShelfType.fromKey(cargoGoal); }
        if (visitAllTiles && visitPerimeter) throw new IllegalArgumentException("Zvol pouze jeden cíl: goal.visitAllTiles nebo goal.visitPerimeter");
        if ((visitAllTiles || visitPerimeter) && (sorting || searching || arrayPractice || endless)) throw new IllegalArgumentException("Průchod mapou vyžaduje skladovou úroveň bez nekonečného režimu");
        // Starý klíč přijímáme kvůli kompatibilitě definic, ale dokumentaci nenačítáme.
        documentation = null;
        allowedKeywords = Set.copyOf(allowedKeywords);
        for (String keyword : allowedKeywords) if (!API_KEYWORDS.contains(keyword) && !CONTROL_KEYWORDS.contains(keyword))
            throw new IllegalArgumentException("allowedKeywords: neznámý příkaz nebo konstrukce: " + keyword);
        requiredKeywords = Set.copyOf(requiredKeywords);
        for (String keyword : requiredKeywords) {
            if (!API_KEYWORDS.contains(keyword) && !CONTROL_KEYWORDS.contains(keyword))
                throw new IllegalArgumentException("requiredKeywords: neznámý příkaz nebo konstrukce: " + keyword);
            if (!permits(allowedKeywords, keyword)) throw new IllegalArgumentException("requiredKeywords: požadovaný příkaz není povolen: " + keyword);
        }
        if (positionGoal != null && (sorting || searching || arrayPractice || endless))
            throw new IllegalArgumentException("goal.position vyžaduje skladovou úroveň bez nekonečného režimu");
        array = List.copyOf(array);
        expectedArray = List.copyOf(expectedArray);
        if (!arrayPractice && !expectedArray.isEmpty()) throw new IllegalArgumentException("goal.array lze použít pouze v režimu array");
        if (sorting || searching || arrayPractice) {
            if (cargoGoal != null) throw new IllegalArgumentException("goal.cargo lze použít pouze ve skladové úrovni");
            if (endless) throw new IllegalArgumentException("Třídění nemůže být nekonečný sklad");
            if (arraySandbox) {
                if (!array.isEmpty() || !expectedArray.isEmpty() || expectedOutput != null || !allowedKeywords.isEmpty() || !requiredKeywords.isEmpty())
                    throw new IllegalArgumentException("arraysandbox nemá předepsané pole, výstup ani omezení příkazů");
            } else if (arrayPractice) {
                if (array.isEmpty() || array.size() > 32) throw new IllegalArgumentException("array: očekávám 1–32 celočíselných hodnot");
                if (expectedArray.size() != array.size()) throw new IllegalArgumentException("goal.array musí obsahovat stejný počet hodnot jako array");
            } else if (array.size() < 2 || array.size() > 32 || array.stream().anyMatch(n -> n < 1 || n > 999))
                throw new IllegalArgumentException("array: očekávám 2–32 hodnot v rozsahu 1–999");
            if (searching) {
                for (int i = 1; i < array.size(); i++) if (array.get(i - 1) > array.get(i))
                    throw new IllegalArgumentException("Binary search vyžaduje vzestupně seřazené array");
            }
        } else if (!array.isEmpty()) throw new IllegalArgumentException("array lze použít pouze v algoritmických úrovních");
        shelves = List.copyOf(shelves); obstacles = Set.copyOf(obstacles);
        if (!sorting && !searching && !arrayPractice) {
            if (width < 2 || width > 25 || height < 2 || height > 25) throw new IllegalArgumentException("Rozměry musí být 2–25 sloupců a 2–25 řádků");
            if (java.util.stream.IntStream.of(orangeGoal, purpleGoal, toolsGoal, materialsGoal).anyMatch(n -> n < 0 || n > 99)
                    || orangeGoal + purpleGoal + toolsGoal + materialsGoal == 0 && (endless || expectedOutput == null && cargoGoal == null && !visitAllTiles && !visitPerimeter && positionGoal == null))
                throw new IllegalArgumentException("Cíle musí být 0–99 a alespoň jeden kladný, nebo nastav expectedOutput, goal.cargo, goal.visitAllTiles či goal.position");
            inside(start, width, height, "start");
            if (positionGoal != null) inside(positionGoal, width, height, "goal.position");
            if (visitPerimeter && start.x()!=0 && start.y()!=0 && start.x()!=width-1 && start.y()!=height-1)
                throw new IllegalArgumentException("goal.visitPerimeter: start musí ležet na obvodu mapy");
            if (dock != null) inside(dock, width, height, "dock");
            else if (endless || orangeGoal + purpleGoal + toolsGoal + materialsGoal > 0)
                throw new IllegalArgumentException("dock: expedice je povinná pro doručování balíků");
            for (Cell cell : obstacles) {
                inside(cell, width, height, "obstacles");
                if (visitPerimeter && (cell.x()==0 || cell.y()==0 || cell.x()==width-1 || cell.y()==height-1))
                    throw new IllegalArgumentException("goal.visitPerimeter: na obvodu nesmí být překážka");
            }
            if (obstacles.contains(start) || dock != null && obstacles.contains(dock)) throw new IllegalArgumentException("Start ani expedice nesmí být v překážce");
            if (endless) {
                inside(inbound, width, height, "inbound");
                if (inbound.equals(dock) || obstacles.contains(inbound)) throw new IllegalArgumentException("Příjem se nesmí překrývat s expedicí ani překážkou");
            } else if (inbound != null) throw new IllegalArgumentException("Příjem je zatím dostupný pouze v nekonečné úrovni");
            Set<Cell> occupied = new HashSet<>();
            for (Shelf shelf : shelves) {
                inside(shelf.cell(), width, height, "shelves");
                ShelfType.fromKey(shelf.type());
                if (shelf.stock() < (endless ? 0 : 1) || shelf.stock() > 999) throw new IllegalArgumentException("Počáteční zásoba regálu musí být " + (endless ? "0" : "1") + "–999");
                if (!occupied.add(shelf.cell()) || obstacles.contains(shelf.cell()) || shelf.cell().equals(dock) || shelf.cell().equals(inbound)) throw new IllegalArgumentException("Regál se překrývá s jiným regálem, překážkou, příjmem nebo expedicí: " + shelf.cell());
            }
            Set<Cell> reachable = new HashSet<>();
            ArrayDeque<Cell> queue = new ArrayDeque<>(); queue.add(start); reachable.add(start);
            while (!queue.isEmpty()) {
                Cell cell = queue.removeFirst();
                for (int[] delta : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                    Cell next = new Cell(cell.x() + delta[0], cell.y() + delta[1]);
                    if (next.x() >= 0 && next.x() < width && next.y() >= 0 && next.y() < height
                            && !obstacles.contains(next) && reachable.add(next)) queue.add(next);
                }
            }
            if (dock != null && !reachable.contains(dock)) throw new IllegalArgumentException("Expedice není dosažitelná ze startu");
            if (positionGoal != null && !reachable.contains(positionGoal)) throw new IllegalArgumentException("goal.position: cíl není dosažitelný ze startu");
            if (visitAllTiles && reachable.size() != width * height - obstacles.size())
                throw new IllegalArgumentException("goal.visitAllTiles: všechna průchozí políčka musí být dosažitelná ze startu");
            for (ShelfType type : ShelfType.values()) {
                int goal = switch (type.key()) { case "orange" -> orangeGoal; case "purple" -> purpleGoal; case "tools" -> toolsGoal; default -> materialsGoal; };
                int available = shelves.stream().filter(s -> s.type().equals(type.key()) && reachable.contains(s.cell())).mapToInt(Shelf::stock).sum();
                if (!endless && available < goal + (type.key().equals(cargoGoal) ? 1 : 0)) throw new IllegalArgumentException("Na dosažitelných regálech není dost zásilek pro splnění cílů");
                if (endless && shelves.stream().filter(s -> s.type().equals(type.key()) && reachable.contains(s.cell())).count() != 1)
                    throw new IllegalArgumentException("Nekonečný sklad potřebuje jednu dosažitelnou polici pro každý ze čtyř typů");
            }
            if (endless && !reachable.contains(inbound)) throw new IllegalArgumentException("Příjem není dosažitelný ze startu");
        }
    }

    boolean arrayLevel() { return sorting || searching || arrayPractice; }
    boolean checksOutput() { return expectedOutput != null; }
    boolean visitsTiles() { return visitAllTiles || visitPerimeter; }
    boolean checksConsole() { return checksOutput() || visitsTiles(); }
    boolean onPerimeter(int x,int y) { return x==0 || y==0 || x==width-1 || y==height-1; }
    int tileGoal() { return visitPerimeter ? 2*width+2*height-4 : width * height - obstacles.size(); }
    boolean outputMatches(String output) { return !checksOutput() || withoutFinalNewline(expectedOutput).equals(withoutFinalNewline(output)); }
    private static String withoutFinalNewline(String text) {
        text = text.replace("\r\n", "\n").replace('\r', '\n');
        return text.endsWith("\n") ? text.substring(0, text.length()-1) : text;
    }

    int goal(String type) { return switch (type) { case "orange" -> orangeGoal; case "purple" -> purpleGoal; case "tools" -> toolsGoal; case "materials" -> materialsGoal; default -> 0; }; }
    int totalGoal() { return orangeGoal + purpleGoal + toolsGoal + materialsGoal; }

    /** Zakázka kampaně uvádí jen požadované zboží; nekonečný sklad používá všechny typy. */
    List<ShelfType> orderTypes() {
        return java.util.Arrays.stream(ShelfType.values()).filter(type -> endless || goal(type.key()) > 0).toList();
    }

    /** Spodní lišta odpovídá sortimentu polic v pořadí z definice, ne jejich aktuální zásobě. */
    List<ShelfType> inventoryTypes() {
        return shelves.stream().map(Shelf::type).distinct().map(ShelfType::fromKey).toList();
    }

    static Level read(Reader reader, String defaultId) throws Exception {
        Properties p = new Properties(); p.load(reader);
        for (String key : p.stringPropertyNames()) if (!KEYS.contains(key)) throw new IllegalArgumentException("Neznámá vlastnost: " + key);
        List<Shelf> shelves = new ArrayList<>();
        for (String entry : entries(p.getProperty("shelves", ""))) {
            String[] parts = entry.split(",", -1);
            if (parts.length != 4) throw new IllegalArgumentException("shelves: očekávám typ,x,y,zásoba");
            shelves.add(new Shelf(new Cell(integer(parts[1], "shelves.x"), integer(parts[2], "shelves.y")), parts[0].strip(), integer(parts[3], "shelves.stock")));
        }
        Set<Cell> obstacles = new HashSet<>();
        for (String entry : entries(p.getProperty("obstacles", ""))) {
            if (!obstacles.add(cell(entry, "obstacles"))) throw new IllegalArgumentException("Duplicitní překážka: " + entry);
        }
        String mode = p.getProperty("mode", "campaign").strip();
        if (!Set.of("campaign", "endless", "bubblesort", "binarysearch", "array", "arraysandbox").contains(mode)) throw new IllegalArgumentException("mode musí být campaign, endless, bubblesort, binarysearch, array nebo arraysandbox: " + mode);
        boolean sorting = mode.equals("bubblesort"), searching = mode.equals("binarysearch"), arraySandbox = mode.equals("arraysandbox"), arrayPractice = mode.equals("array") || arraySandbox;
        boolean arrayLevel = sorting || searching || arrayPractice;
        return new Level(p.getProperty("id", defaultId).strip(), required(p,"name"), p.getProperty("description", ""),
                arrayLevel ? 10 : integer(required(p,"width"), "width"), arrayLevel ? 10 : integer(required(p,"height"), "height"),
                arrayLevel ? new Cell(0, 0) : cell(required(p,"start"), "start"), arrayLevel ? new Cell(0, 0)
                        : p.getProperty("dock", "").isBlank() ? null : cell(p.getProperty("dock"), "dock"),
                integer(p.getProperty("goal.orange", "0"), "goal.orange"), integer(p.getProperty("goal.purple", "0"), "goal.purple"), shelves, obstacles, mode.equals("endless"),
                p.containsKey("inbound") ? cell(p.getProperty("inbound"), "inbound") : null,
                integer(p.getProperty("goal.tools", "0"), "goal.tools"), integer(p.getProperty("goal.materials", "0"), "goal.materials"),
                p.getProperty("initialCode"), p.getProperty("assignment"), null,
                java.util.Arrays.stream(p.getProperty("allowedKeywords", "").split("[,;\\s]+"))
                        .filter(s -> !s.isBlank()).map(Level::canonicalKeyword).collect(java.util.stream.Collectors.toSet()),
                sorting, p.getProperty("array", "").isBlank() ? List.of() : java.util.Arrays.stream(p.getProperty("array").split("[,;\\s]+"))
                        .map(value -> integer(value, "array")).toList(), searching,
                searching ? integer(required(p, "target"), "target") : 0,
                p.getProperty("expectedOutput"), p.getProperty("goal.cargo"), booleanProperty(p,"goal.visitAllTiles"), booleanProperty(p,"goal.visitPerimeter"),
                p.containsKey("goal.position") ? cell(p.getProperty("goal.position"), "goal.position") : null,
                java.util.Arrays.stream(p.getProperty("requiredKeywords", "").split("[,;\\s]+"))
                        .filter(s -> !s.isBlank()).map(Level::canonicalKeyword).collect(java.util.stream.Collectors.toSet()),
                booleanProperty(p, "isVisible", true), arrayPractice,
                p.getProperty("goal.array", "").isBlank() ? List.of() : java.util.Arrays.stream(p.getProperty("goal.array").split("[,;\\s]+"))
                        .map(value -> integer(value, "goal.array")).toList(), arraySandbox);
    }

    private static String content(String value, String key, int limit) {
        if (value == null || value.isBlank()) return null;
        value = value.replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ");
        if (value.length() > limit) throw new IllegalArgumentException(key + ": nejvýše " + limit + " znaků");
        return value;
    }

    private static List<String> entries(String value) { return value.isBlank() ? List.of() : java.util.Arrays.stream(value.split(";", -1)).map(String::strip).filter(s -> !s.isEmpty()).toList(); }
    private static String required(Properties p, String key) {
        String value = p.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Chybí vlastnost: " + key);
        return value.strip();
    }
    private static boolean booleanProperty(Properties p,String key) {
        return booleanProperty(p, key, false);
    }
    private static boolean booleanProperty(Properties p,String key,boolean defaultValue) {
        String value=p.getProperty(key,Boolean.toString(defaultValue)).strip();
        if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException(key+": očekávám true nebo false");
        return value.equals("true");
    }
    private static int integer(String value, String key) {
        try { return Integer.parseInt(value.strip()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException(key + ": očekávám celé číslo"); }
    }
    private static Cell cell(String value, String key) {
        String[] parts = value.split(",", -1);
        if (parts.length != 2) throw new IllegalArgumentException(key + ": očekávám x,y");
        return new Cell(integer(parts[0], key), integer(parts[1], key));
    }
    private static void inside(Cell cell, int width, int height, String key) {
        if (cell == null || cell.x() < 0 || cell.x() >= width || cell.y() < 0 || cell.y() >= height) throw new IllegalArgumentException(key + ": souřadnice mimo sklad: " + cell);
    }
}
