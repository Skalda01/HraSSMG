package org.example;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import robot.api.Robot.ShelfType;
import robot.api.Robot.TileType;

/** Příjem, správné police a nepřetržitá expedice ve skutečném hráčově procesu. */
public final class EndlessChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Level level = new LevelCatalog(Path.of("target/no-custom-levels")).levels().stream().filter(Level::endless).findFirst().orElseThrow();
        check(level.dock().equals(new Level.Cell(0,1)) && level.inbound().equals(new Level.Cell(1,0)), "Expedice a příjem mají zadané souřadnice");
        check(level.shelves().size() == 4, "Čtyři police");
        for (int i=0;i<4;i++) check(level.shelves().get(i).cell().equals(new Level.Cell(1+2*i,level.height()-1)), "Police na spodním řádku s mezerou");
        Warehouse world = new Warehouse(level);
        check(world.stockCount("orange") == 0 && world.incoming.equals("orange"), "Prázdný sklad a první příchozí balík");
        world.x = 1; world.y = 0;
        check(world.tileType() == TileType.INBOUND && world.shelfType() == null && world.cargoType() == null, "Typ příjmu a dotazy bez nákladu");
        world.pick();
        check(world.cargoType() == ShelfType.PARTS && world.incoming.equals("purple"), "Vyzvednutí a další balík na příjmu");
        String waiting = world.incoming;
        expect(world::pick, "už nese");
        check(world.incoming.equals(waiting) && world.cargo.equals("orange"), "Druhý balík nejde vzít ani ztratit");
        world.x = 0; world.y = 1;
        expect(world::drop, "nejprve naskladnit");
        check(world.cargo.equals("orange") && world.totalDelivered == 0, "Příjem nelze odeslat bez naskladnění");
        Level.Shelf wrong = level.shelves().get(1);
        world.x = wrong.cell().x(); world.y = wrong.cell().y();
        expect(world::drop, "Nesprávná police");
        check(world.stock(wrong) == 0 && world.cargo.equals("orange"), "Chybná police nepozmění balík ani zásobu");
        world.x = 2; world.y = 2; expect(world::drop, "expedici");
        Level.Shelf correct = level.shelves().getFirst();
        world.x = correct.cell().x(); world.y = correct.cell().y(); world.drop();
        check(world.stock(correct) == 1 && world.cargo == null && world.totalStored == 1, "Správné naskladnění");
        world.pick(); world.drop();
        check(world.stock(correct) == 1 && world.cargo == null, "Vyzvednutí a vrácení balíku nevytváří další kus");
        world.x = wrong.cell().x(); world.y = wrong.cell().y(); expect(world::pick, "prázdný");

        world.reset();
        Set<ShelfType> seen = new HashSet<>();
        for (int i=0;i<4100;i++) {
            world.x = 1; world.y = 0; world.pick();
            ShelfType type = world.cargoType(); seen.add(type);
            Level.Shelf shelf = level.shelves().stream().filter(s -> s.type().equals(type.key())).findFirst().orElseThrow();
            world.x = shelf.cell().x(); world.y = shelf.cell().y(); world.drop();
        }
        check(seen.size() == 4 && Arrays.stream(ShelfType.values()).allMatch(t -> world.stockCount(t.key()) == 1025), "Všechny typy a kapacita nad 999 bez omezení");
        check(world.totalStored == 4100 && world.totalDelivered == 0, "Naskladnění se nepočítá jako expedice");
        world.reset();
        check(world.totalStored == 0 && world.totalDelivered == 0 && world.cargo == null && world.level.equals(level)
                && Arrays.stream(ShelfType.values()).allMatch(t -> world.stockCount(t.key()) == 0), "Reset skladu, statistik a nákladu");

        try (var session = new RobotProgram().start(CodeEditor.starter(level))) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
            int requests = 0; long round = 0, expectedDelivered = 0;
            while (world.completedOrders < 50 || requests <= 2000) {
                if (System.nanoTime() > deadline) throw new AssertionError("Program se zastavil: zakázky=" + world.completedOrders + ", příkazy=" + requests);
                var command = session.poll();
                if (command == null) { Thread.yield(); continue; }
                long[] stockBefore = Arrays.stream(ShelfType.values()).mapToLong(t -> world.stockCount(t.key())).toArray();
                int[] goalsBefore = Arrays.stream(ShelfType.values()).mapToInt(t -> world.goal(t.key())).toArray();
                apply(session, world, command); requests++;
                if (world.completedOrders != round) {
                    round = world.completedOrders;
                    check(world.level.equals(level), "Police a mapa zůstávají pevné");
                    expectedDelivered += Arrays.stream(goalsBefore).sum();
                    check(world.totalDelivered == expectedDelivered && world.delivered() == 0 && world.cargo == null, "Další zakázka po správném doručení");
                    int[] nextGoals = Arrays.stream(ShelfType.values()).mapToInt(t -> world.goal(t.key())).toArray();
                    check(!Arrays.equals(goalsBefore, nextGoals) && Arrays.stream(nextGoals).allMatch(n -> n >= 0 && n <= 4)
                            && world.totalGoal() > 0, "Nová zakázka má jiné platné požadavky");
                    check(Arrays.equals(stockBefore, Arrays.stream(ShelfType.values()).mapToLong(t -> world.stockCount(t.key())).toArray()), "Nová zakázka nemění zásoby");
                    check(!world.complete() && world.incoming != null, "Nekonečný příjem i zakázky pokračují");
                }
            }
            check(requests > 2000 && world.totalStored >= world.totalDelivered, "Živý program projde příjmem, policemi i dockem přes limit jedné zakázky");
        }
        try (var session = new RobotProgram().start(CodeEditor.wrapLegacyBody("move(Direction.RIGHT);"))) {
            var command = awaitCommand(session); Thread.sleep(3200); session.check();
            check(command.name().equals("right"), "Pauza nevyčerpává čas výpočtu");
            session.acknowledge(3,3,1); awaitEnd(session);
        }
        expectProgram("move(null);", "Direction");
        expectProgram("throw new IllegalStateException(\"test řádku\");", "editor:");
        expectProgram("while (true) {}", "časový limit");
        try (var session = new RobotProgram().start(CodeEditor.wrapLegacyBody("for(int i=0;i<2001;i++) robotCoordinatesX();"))) {
            expect(() -> driveToEnd(session, new Warehouse(level)), "2000");
        }
        System.out.println("PASS: " + checks + " endless checks (50+ live orders, fixed shelves, inbound, 4 types, 4100 stored packages, correct placement, limits).");
    }

    private static void apply(RobotProgram.Session session, Warehouse world, RobotProgram.Command command) {
        int[] response;
        if (command.query()) response = world.query(command);
        else {
            int guard = 0;
            while (!world.execute(command)) if (++guard > 100) throw new AssertionError("Nedokončený pohyb");
            response = new int[]{world.x,world.y};
        }
        session.acknowledge(response[0],response[1],world.completedOrders+1);
    }
    private static RobotProgram.Command awaitCommand(RobotProgram.Session session) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            var command = session.poll(); if (command != null) return command;
            if (session.finished()) throw new AssertionError("Chybí příkaz");
            Thread.sleep(1);
        }
        throw new AssertionError("Čekání na příkaz překročilo limit");
    }
    private static void driveToEnd(RobotProgram.Session session, Warehouse world) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
        while (!session.finished()) {
            if (System.nanoTime() > deadline) throw new AssertionError("Program neskončil");
            var command = session.poll();
            if (command == null) Thread.sleep(1); else apply(session, world, command);
        }
    }
    private static void awaitEnd(RobotProgram.Session session) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!session.finished()) {
            if (System.nanoTime() > deadline) throw new AssertionError("Program neskončil");
            Thread.sleep(1);
        }
    }
    private static void expectProgram(String body, String fragment) throws Exception {
        try (var session = new RobotProgram().start(CodeEditor.wrapLegacyBody(body))) { expect(() -> driveToEnd(session, new Warehouse()), fragment); }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
    private static void expect(Action action, String fragment) throws Exception {
        try { action.run(); } catch (Exception e) { check(e.getMessage().contains(fragment), "Očekáváno " + fragment + ": " + e); return; }
        throw new AssertionError("Chybí chyba: " + fragment);
    }
    @FunctionalInterface private interface Action { void run() throws Exception; }
}
