package org.example;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import robot.api.Robot.TileType;
import robot.api.Robot.ShelfType;

/** Herní pravidla nezávislá na okně a vykreslování. */
final class Warehouse {
    Level level;
    long completedOrders, totalDelivered, totalStored;
    private final Map<Level.Cell, Long> stock = new HashMap<>();
    final List<String> log = new ArrayList<>();
    int x, y, orange, purple, tools, materials, steps;
    long orangeStock, purpleStock;
    String cargo, incoming;
    String consoleOutput = "";
    boolean programFinished;
    final java.util.Set<Level.Cell> printedTiles = new java.util.HashSet<>();
    private int consoleLineStart;
    private String tileOutputError;
    private boolean perimeterFinished;
    private boolean cargoNeedsStorage;
    private int arrivalIndex;
    private final int[] orderGoals = new int[ShelfType.values().length];
    private final java.util.Random orderRandom = new java.util.Random();

    Warehouse() { this(LevelCatalog.defaultLevel()); }
    Warehouse(Level level) { setLevel(level); }
    void setLevel(Level level) { this.level = java.util.Objects.requireNonNull(level); reset(); }
    long stock(Level.Shelf shelf) { return stock.getOrDefault(shelf.cell(), 0L); }
    int delivered(String type) {
        return switch (type) { case "orange" -> orange; case "purple" -> purple; case "tools" -> tools; case "materials" -> materials; default -> 0; };
    }
    int delivered() {
        int total = 0;
        for (ShelfType type : ShelfType.values()) total += Math.min(delivered(type.key()), goal(type.key()));
        return total;
    }
    int goal(String type) { return orderGoals[ShelfType.fromKey(type).ordinal()]; }
    int totalGoal() { return java.util.Arrays.stream(orderGoals).sum(); }
    int orderRemaining(ShelfType type) { return Math.max(0, goal(type.key()) - delivered(type.key())); }
    long stockCount(String type) {
        return level.shelves().stream().filter(s -> s.type().equals(type)).mapToLong(this::stock).sum();
    }

    void reset() {
        completedOrders = totalDelivered = totalStored = 0;
        consoleOutput = ""; programFinished = false;
        printedTiles.clear(); consoleLineStart=0; tileOutputError=null;
        perimeterFinished=false;
        x = level.start().x(); y = level.start().y();
        orange = purple = tools = materials = steps = 0;
        for (ShelfType type : ShelfType.values()) orderGoals[type.ordinal()] = level.goal(type.key());
        stock.clear();
        for (Level.Shelf shelf : level.shelves()) stock.put(shelf.cell(), (long) shelf.stock());
        refreshStockTotals();
        cargo = incoming = null; cargoNeedsStorage = false; arrivalIndex = 0;
        if (level.endless()) spawnIncoming();
        log.clear(); message("WRK-01 připojen. Čekám na program.");
    }

    boolean blocked(int px, int py) { return level.obstacles().contains(new Level.Cell(px, py)); }
    private boolean orderComplete() { return delivered() == totalGoal(); }
    boolean complete() { return !level.arrayLevel() && !level.endless() && orderComplete()
            && (level.cargoGoal() == null || level.cargoGoal().equals(cargo))
            && (level.positionGoal() == null || programFinished && atGoal()) && outputComplete(); }
    boolean atGoal() { return level.positionGoal() != null && x == level.positionGoal().x() && y == level.positionGoal().y(); }
    boolean outputComplete() { return (!level.checksOutput() || programFinished && level.outputMatches(consoleOutput))
            && (!level.visitsTiles() || programFinished && tileOutputError==null && printedTiles.size()==level.tileGoal())
            && (!level.visitPerimeter() || perimeterFinished && atStart()); }
    void captureOutput(String output) {
        consoleOutput=output;
        if (!level.visitsTiles()) return;
        int end;
        while ((end=output.indexOf('\n',consoleLineStart))>=0) {
            checkTileLine(output.substring(consoleLineStart,end)); consoleLineStart=end+1;
        }
    }
    void finishProgram() {
        if (level.visitsTiles() && consoleLineStart<consoleOutput.length()) {
            checkTileLine(consoleOutput.substring(consoleLineStart)); consoleLineStart=consoleOutput.length();
        }
        programFinished=true;
    }
    private void checkTileLine(String line) {
        if (tileOutputError!=null) return;
        if (level.visitPerimeter()) {
            if (perimeterFinished) { tileOutputError="Po HOTOVO už nesmí následovat další výpis."; return; }
            if (line.equals("HOTOVO")) {
                if (!atStart()) tileOutputError="HOTOVO vypiš až po návratu na výchozí políčko.";
                else if (printedTiles.size()!=level.tileGoal()) tileOutputError="Před HOTOVO musíš obejít a vypsat celý obvod.";
                else perimeterFinished=true;
                return;
            }
        }
        String expected="tile("+x+","+y+")";
        if (!line.equals(expected)) tileOutputError="Na ["+x+","+y+"] se očekává výpis "+expected+".";
        else if (!printedTiles.add(new Level.Cell(x,y))) tileOutputError="Políčko ["+x+","+y+"] bylo vypsáno vícekrát.";
    }
    String outputProblem() {
        if (tileOutputError!=null) return tileOutputError;
        if (level.visitsTiles() && printedTiles.size()!=level.tileGoal()) return "Vypsaná políčka: "+printedTiles.size()+" / "+level.tileGoal()+".";
        if (level.visitPerimeter() && !atStart()) return "Vrať robota na výchozí políčko a vypiš HOTOVO.";
        if (level.visitPerimeter() && !perimeterFinished) return "Po návratu na začátek vypiš HOTOVO místo opakování souřadnic.";
        return "Výstup konzole neodpovídá zadání. Otevři záložku Konzole.";
    }
    private boolean atStart() { return x==level.start().x() && y==level.start().y(); }
    private boolean onDock() { return level.dock()!=null && x == level.dock().x() && y == level.dock().y(); }
    private boolean onInbound() { return level.inbound() != null && x == level.inbound().x() && y == level.inbound().y(); }
    private Level.Shelf currentShelf() {
        return level.shelves().stream().filter(s -> s.cell().x() == x && s.cell().y() == y).findFirst().orElse(null);
    }

    TileType tileType() {
        if (onDock()) return TileType.DOCK;
        if (onInbound()) return TileType.INBOUND;
        return currentShelf() != null ? TileType.SHELF : TileType.FLOOR;
    }
    ShelfType shelfType() {
        Level.Shelf shelf = currentShelf();
        return shelf == null ? null : ShelfType.fromKey(shelf.type());
    }
    ShelfType cargoType() { return cargo == null ? null : ShelfType.fromKey(cargo); }

    /** Dotazy nemění polohu, náklad, zásoby ani počet kroků. */
    int[] query(RobotProgram.Command command) {
        int value = switch (command.name()) {
            case "robotCoordinatesX" -> x;
            case "robotCoordinatesY" -> y;
            case "tileType" -> tileType().ordinal();
            case "shelfType" -> shelfType() == null ? -1 : shelfType().ordinal();
            case "cargoType" -> cargoType() == null ? -1 : cargoType().ordinal();
            case "orderRemaining" -> {
                if (command.a() < 0 || command.a() >= ShelfType.values().length) throw new IllegalArgumentException("Neplatný typ zboží pro zakázku.");
                yield orderRemaining(ShelfType.values()[command.a()]);
            }
            default -> throw new IllegalArgumentException("Neznámý dotaz: " + command.name());
        };
        return new int[]{value, 0};
    }

    /** Provede jeden příkaz hráčova programu. */
    boolean execute(RobotProgram.Command command) {
        switch (command.name()) {
            case "left" -> move(-1, 0);
            case "right" -> move(1, 0);
            case "top" -> move(0, -1);
            case "down" -> move(0, 1);
            case "pick" -> pick();
            case "drop" -> drop();
            default -> throw new IllegalStateException("Neznámý příkaz.");
        }
        return true;
    }

    void move(int dx, int dy) {
        int nx = x + dx, ny = y + dy;
        if (nx < 0 || nx >= level.width() || ny < 0 || ny >= level.height())
            throw new IllegalStateException("Robot by opustil sklad na [" + nx + ", " + ny + "].");
        if (blocked(nx, ny)) throw new IllegalStateException("Na [" + nx + ", " + ny + "] je překážka.");
        if (level.visitPerimeter() && !level.onPerimeter(nx,ny)) throw new IllegalStateException("V této úrovni se pohybuj pouze po obvodu mapy.");
        if (level.visitPerimeter() && perimeterFinished) throw new IllegalStateException("Po výpisu HOTOVO už robota neposouvej.");
        x = nx; y = ny; steps++;
    }

    void pick() {
        if (cargo != null) throw new IllegalStateException("Robot už nese zásilku. Nejprve zavolej drop().");
        if (onInbound()) {
            if (incoming == null) throw new IllegalStateException("Na příjmu není balík.");
            cargo = incoming; cargoNeedsStorage = true;
            spawnIncoming();
            message("Příjem: " + cargoType().label() + ". Naskladni balík do správné police.");
            return;
        }
        Level.Shelf shelf = currentShelf();
        if (shelf == null) throw new IllegalStateException("Zde není zásilka. Umístění regálů najdeš v zadání úrovně.");
        long remaining = stock(shelf);
        if (remaining == 0) throw new IllegalStateException("Tento regál je prázdný. Nejprve ho naskladni.");
        cargo = shelf.type(); cargoNeedsStorage = false;
        stock.put(shelf.cell(), remaining - 1); refreshStockTotals();
        message("Vyzvednuto: " + cargoType().label() + ".");
    }

    void drop() {
        if (cargo == null) throw new IllegalStateException("Robot nenese zásilku. Použij pick() u regálu nebo na příjmu.");
        Level.Shelf shelf = currentShelf();
        if (level.endless() && shelf != null) {
            if (!shelf.type().equals(cargo)) throw new IllegalStateException("Nesprávná police. " + cargoType().label() + " patří do police stejného typu.");
            stock.put(shelf.cell(), stock(shelf) + 1);
            String label = cargoType().label();
            cargo = null; cargoNeedsStorage = false; totalStored++; refreshStockTotals();
            message("Naskladněno: " + label + ". V polici: " + stock(shelf) + ".");
            return;
        }
        if (!onDock()) throw new IllegalStateException("Balík polož do správné police nebo na expedici " + level.dock() + ".");
        if (cargoNeedsStorage) throw new IllegalStateException("Balík z příjmu musíš nejprve naskladnit do správné police.");
        if (level.endless() && delivered(cargo) >= goal(cargo)) throw new IllegalStateException("Tento typ balíku už aktuální zakázka nepotřebuje.");
        switch (cargo) { case "orange" -> orange++; case "purple" -> purple++; case "tools" -> tools++; case "materials" -> materials++; default -> throw new IllegalStateException("Neznámý náklad."); }
        cargo = null; cargoNeedsStorage = false; totalDelivered++;
        message("Zásilka odeslána. " + delivered() + "/" + totalGoal());
        if (orderComplete()) {
            if (level.endless()) {
                completedOrders++;
                orange = purple = tools = materials = 0;
                nextOrder();
                message("Zakázka " + completedOrders + " splněna. Začíná další, police i zásoby zůstávají.");
            } else message("SMĚNA SPLNĚNA! Všechny objednávky jsou doručeny.");
        }
    }

    private void nextOrder() {
        int[] next = new int[orderGoals.length];
        do {
            for (int i = 0; i < next.length; i++) next[i] = orderRandom.nextInt(5);
        } while (java.util.Arrays.equals(next, orderGoals) || java.util.Arrays.stream(next).sum() == 0);
        System.arraycopy(next, 0, orderGoals, 0, next.length);
    }

    private void spawnIncoming() {
        // Pravidelné střídání všech typů zaručuje zásobování bez vyhladovění některé zakázky.
        ShelfType[] types = ShelfType.values();
        incoming = types[Math.floorMod(arrivalIndex++, types.length)].key();
    }
    private void refreshStockTotals() {
        orangeStock = stockCount("orange"); purpleStock = stockCount("purple");
    }
    void message(String text) {
        log.add(text);
        if (log.size() > 100) log.removeFirst();
    }
}
