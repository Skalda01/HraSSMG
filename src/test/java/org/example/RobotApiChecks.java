package org.example;

import java.util.List;
import java.util.concurrent.TimeUnit;
import robot.api.Robot.TileType;
import robot.api.Robot.ShelfType;

/** Typy a větvení ve skutečném hráčově programu, včetně neplatných akcí. */
public final class RobotApiChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        check(java.util.Arrays.stream(robot.api.Robot.class.getDeclaredMethods())
                        .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                        .map(java.lang.reflect.Method::getName).collect(java.util.stream.Collectors.toSet())
                        .equals(Level.API_KEYWORDS), "Veřejné Robot API obsahuje přesně dokumentované příkazy");
        for (String removed : new String[]{"left", "right", "top", "down", "up", "locate"}) {
            check(java.util.Arrays.stream(robot.api.Robot.class.getDeclaredMethods()).noneMatch(m -> m.getName().equals(removed)), "Metoda již není součástí API: " + removed);
        }
        String source = program("""
                if (orderRemaining(ShelfType.PARTS) != 3 || orderRemaining(ShelfType.TOOLS) != 0) throw new IllegalStateException("Počáteční požadavky zakázky");
                if (tileType() != TileType.FLOOR || shelfType() != null) throw new IllegalStateException("Start musí být podlaha");
                move(Direction.RIGHT);
                move(Direction.DOWN);
                move(Direction.LEFT);
                move(Direction.TOP);
                presunNa(4,1);
                if (tileType() != TileType.SHELF || shelfType() != ShelfType.PARTS) throw new IllegalStateException("Regál součástek");
                pick();
                move(Direction.RIGHT);
                if (tileType() != TileType.FLOOR || shelfType() != null) throw new IllegalStateException("Podlaha s nákladem");
                presunNa(0,3);
                if (tileType() != TileType.DOCK || shelfType() != null) throw new IllegalStateException("Expedice");
                drop();
                presunNa(6,3);
                switch (shelfType()) {
                    case ELECTRONICS -> pick();
                    case PARTS -> throw new IllegalStateException("Chybný sortiment");
                }
                presunNa(0,3);
                switch (tileType()) {
                    case DOCK -> drop();
                    default -> throw new IllegalStateException("Chybné políčko");
                }
                for (int i=0;i<3;i++) {
                    presunNa(4,1);
                    pick();
                    presunNa(0,3);
                    drop();
                }
                presunNa(4,1);
                if (tileType() != TileType.SHELF || shelfType() != ShelfType.PARTS) throw new IllegalStateException("Prázdný regál nemění typ");
                if (orderRemaining(ShelfType.PARTS) != 0 || orderRemaining(ShelfType.ELECTRONICS) != 1) throw new IllegalStateException("Zbývající požadavky zakázky");
                """);
        Warehouse world = new Warehouse(warehouseLevel());
        run(source, world);
        check(world.orange == 4 && world.purple == 1 && world.cargo == null, "Typované podmínky a switch řídí správné doručení");
        check(world.orangeStock == 4 && world.stock(world.level.shelves().getFirst()) == 0, "Zásoba se mění jen v příslušném regálu");
        check(world.tileType() == TileType.SHELF && world.shelfType() == ShelfType.PARTS, "Prázdný regál vrací typ i sortiment");
        expect(world::pick, "prázdný");
        check(world.cargo == null && world.orangeStock == 4, "Neúspěšné vyzvednutí nemění náklad ani zásobu");
        world.x = 4; world.y = 5; world.pick();
        long stock = world.orangeStock;
        expect(world::pick, "už nese");
        check(world.orangeStock == stock && world.cargo.equals("orange"), "Plný robot nevezme druhý balíček");
        expect(world::drop, "expedici");
        check(world.cargo.equals("orange") && world.totalDelivered == 5, "Nepovolené položení náklad neztratí");
        world.x = 0; world.y = 3; world.drop();
        expect(world::drop, "nenese");
        world.x = 1; world.y = 3;
        expect(world::pick, "Zde není");
        check(world.tileType() == TileType.FLOOR && world.shelfType() == null, "Podlaha nemá sortiment");

        List<RobotProgram.Command> moves = new RobotProgram().compile(program("""
                for (Direction direction : Direction.values()) move(direction);
                robot.api.Robot.move(robot.api.Robot.Direction.RIGHT);
                """));
        check(moves.stream().map(RobotProgram.Command::name).toList().equals(List.of("left", "right", "top", "down", "right")), "Všechny směry a plný název API se přeloží");
        check(moves.stream().allMatch(c -> c.line() > 0), "move zachovává řádek hráčova zdroje");

        Warehouse unchanged = new Warehouse(warehouseLevel());
        expect(() -> run(program("move(null);"), unchanged), "Direction");
        expect(() -> run(program("orderRemaining(null);"), new Warehouse(warehouseLevel())), "ShelfType");
        check(unchanged.steps == 0 && unchanged.x == 2 && unchanged.y == 3, "Neplatný směr nemění polohu");
        expect(() -> run(program("presunNa(0,0); move(Direction.LEFT);"), new Warehouse(warehouseLevel())), "opustil sklad");
        expect(() -> run(program("presunNa(8,1); move(Direction.RIGHT);"), new Warehouse(warehouseLevel())), "překážka");
        expect(() -> run(program("pick();"), new Warehouse(warehouseLevel())), "Zde není");
        Warehouse loaded = new Warehouse(warehouseLevel());
        expect(() -> run(program("presunNa(4,1); pick(); drop();"), loaded), "expedici");
        check(loaded.cargo.equals("orange") && loaded.orangeStock == 7 && loaded.delivered() == 0, "Chyba drop v živém programu zachová náklad");
        System.out.println("PASS: " + checks + " robot API checks (typed live queries, all directions, null, empty shelves, cargo, errors, switches, line numbers).");
    }

    static Level warehouseLevel() throws Exception {
        return Level.read(new java.io.StringReader("""
                id=api-fixture
                name=API fixture
                width=10
                height=8
                start=2,3
                dock=0,3
                goal.orange=3
                goal.purple=2
                shelves=orange,4,1,4; orange,4,5,4; purple,6,3,6
                obstacles=9,1
                """), "api-fixture");
    }
    private static String program(String body) {
        String source=CodeEditor.wrapLegacyBody(body);
        return source.substring(0,source.length()-2)+"""
                private static void presunNa(int x,int y) {
                    while(robotCoordinatesX()<x) move(Direction.RIGHT);
                    while(robotCoordinatesX()>x) move(Direction.LEFT);
                    while(robotCoordinatesY()<y) move(Direction.DOWN);
                    while(robotCoordinatesY()>y) move(Direction.TOP);
                }
            }
            """;
    }

    private static void run(String source, Warehouse world) throws Exception {
        try (var session = new RobotProgram().start(source)) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            while (!session.finished()) {
                if (System.nanoTime() > deadline) throw new AssertionError("Program se nedokončil");
                var command = session.poll();
                if (command == null) { Thread.sleep(1); continue; }
                int[] response;
                if (command.query()) {
                    int x = world.x, y = world.y, steps = world.steps;
                    long orange = world.orangeStock, purple = world.purpleStock;
                    String cargo = world.cargo;
                    response = world.query(command);
                    check(world.x == x && world.y == y && world.steps == steps && world.orangeStock == orange && world.purpleStock == purple
                            && java.util.Objects.equals(world.cargo, cargo), "Dotaz nemění stav skladu");
                } else {
                    int guard = 0;
                    while (!world.execute(command)) if (++guard > 100) throw new AssertionError("Nedokončený pohyb");
                    response = new int[]{world.x, world.y};
                }
                session.acknowledge(response[0],response[1],world.completedOrders+1);
            }
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
    private static void expect(Action action, String fragment) throws Exception {
        try { action.run(); }
        catch (Exception e) { check(e.getMessage().contains(fragment), "Očekáváno " + fragment + ": " + e); return; }
        throw new AssertionError("Chybí chyba: " + fragment);
    }
    @FunctionalInterface private interface Action { void run() throws Exception; }
}
