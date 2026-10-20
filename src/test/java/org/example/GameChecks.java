package org.example;

/** Spusť main() s plným JDK. Bez závislosti na okně a testovacím frameworku. */
public final class GameChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        RobotProgram compiler = new RobotProgram();
        var solution = compiler.compile(program("""
                for (int i=0;i<3;i++) {
                    for(int j=0;j<(i==0?2:4);j++) move(Direction.RIGHT);
                    for(int j=0;j<2;j++) move(Direction.TOP);
                    pick();
                    for(int j=0;j<4;j++) move(Direction.LEFT);
                    for(int j=0;j<2;j++) move(Direction.DOWN);
                    drop();
                }
                for(int i=0;i<2;i++) {
                    for(int j=0;j<6;j++) move(Direction.RIGHT);
                    pick();
                    for(int j=0;j<6;j++) move(Direction.LEFT);
                    drop();
                }
                """));
        check(solution.size() == 68, "Java cykly musí vytvořit 68 příkazů");
        check(solution.getFirst().line() == 6, "Příkaz musí odkazovat na řádek 6 celé třídy v editoru");
        Warehouse world = new Warehouse(RobotApiChecks.warehouseLevel());
        for (var command : solution) {
            world.execute(command);
        }
        check(world.complete() && world.orange == 3 && world.purple == 2, "Celá směna musí jít dokončit");
        check(world.steps == 58 && world.x == 0 && world.y == 3 && world.cargo == null, "Správná trasa a prázdný robot v expedici");
        world.reset();
        check(world.orangeStock == 8 && world.purpleStock == 6 && !world.complete(), "Reset obnoví stav skladu");
        for (var command : compiler.compile(program("for(int i=0;i<2;i++) move(Direction.RIGHT); move(Direction.TOP); move(Direction.DOWN); for(int i=0;i<2;i++) move(Direction.LEFT);"))) world.execute(command);
        check(world.x == 2 && world.y == 3 && world.steps == 6, "Všechny směry a počty kroků");
        expectFailure(world::pick, "Zde není zásilka");
        expectFailure(world::drop, "nenese zásilku");
        world.x = 8; world.y = 1;
        expectFailure(() -> world.move(1, 0), "překážka");
        check(world.x == 8 && world.steps == 6, "Chyba nezmění polohu");
        world.x = 0; world.y = 0;
        expectFailure(() -> world.move(-1, 0), "opustil sklad");
        world.x = 4; world.y = 1;
        world.pick();
        expectFailure(world::pick, "už nese");
        expectFailure(world::drop, "expedici");
        check(world.cargo != null && world.orangeStock == 7, "Neplatné doručení zachová náklad");
        expectFailure(() -> compiler.compile(program("move(;")), "editor:5");
        expectFailure(() -> compiler.compile(program("int n = 42;")), "žádné příkazy");
        expectFailure(() -> compiler.compile(program("for (int i=0;i<2001;i++) move(Direction.RIGHT);")), "2000");
        expectFailure(() -> compiler.compile(program("throw new IllegalStateException(\"Česká chyba před příkazem\");")), "Česká chyba před příkazem");
        expectFailure(() -> compiler.compile("public class Main { public static void run() {} }"), "main");
        var helper = compiler.compile("""
                import static robot.api.Robot.*;
                import java.util.List;
                public class Main {
                    public static void main(String[] args) {
                        for (int count : List.of(1, 2)) {
                            walk(count);
                        }
                    }
                    private static void walk(int count) {
                        for(int i=0;i<count;i++) move(Direction.RIGHT);
                    }
                }
                """);
        check(helper.size() == 3, "Skutečný main musí podporovat importy i vlastní pomocné metody");
        check(helper.stream().allMatch(command -> command.line() == 10), "Příkazy z pomocné metody zvýrazní její skutečný řádek");
        String legacy = "for(int i=0;i<2;i++) move(Direction.RIGHT);\nmove(Direction.TOP);\nmove(Direction.DOWN);\nfor(int i=0;i<2;i++) move(Direction.LEFT);";
        String migrated = CodeEditor.migrateSavedProgram(legacy);
        check(migrated.contains("public class Main") && migrated.contains("public static void main(String[] args)"), "Starý program musí získat třídu a vstupní metodu");
        check(CodeEditor.migrateSavedProgram(migrated).equals(migrated), "Hotovou třídu už znovu neobalujeme");
        check(CodeEditor.migrateSavedProgram(CodeEditor.EXAMPLE).equals(CodeEditor.EXAMPLE), "Úplný Java zdroj se zachová beze změn");
        var migratedCommands = compiler.compile(migrated);
        check(migratedCommands.size() == 6 && migratedCommands.getFirst().line() == 5, "Migrace zachová příkazy i správné nové řádky");
        long start = System.nanoTime();
        expectFailure(() -> compiler.compile(program("while (true) {}")), "časový limit");
        check(System.nanoTime() - start < 15_000_000_000L, "Nekonečný cyklus nesmí zablokovat hru");
        CodeEditor editor = new CodeEditor();
        editor.anchor = 0; editor.caret = editor.text.length();
        editor.insert("move(Direction.RIGHT);\r\nmove(Direction.LEFT);");
        check(editor.lines().length == 2 && !editor.text.contains("\r"), "Vložení normalizuje konce řádků");
        editor.undo();
        check(editor.text.equals(CodeEditor.EXAMPLE), "Undo obnoví původní program");
        editor.redo();
        check(editor.text.equals("move(Direction.RIGHT);\nmove(Direction.LEFT);"), "Redo obnoví změnu");
        System.out.println("PASS: " + checks + " checks (Java compiler, execution, limits, warehouse, editor).");
    }

    private static String program(String body) { return CodeEditor.wrapLegacyBody(body); }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
    private static void expectFailure(CheckedAction action, String text) throws Exception {
        try { action.run(); }
        catch (Exception e) {
            check(e.getMessage() != null && e.getMessage().contains(text), "Očekáváno: " + text + "; obdrženo: " + e);
            return;
        }
        throw new AssertionError("Očekávána chyba: " + text);
    }
    @FunctionalInterface private interface CheckedAction { void run() throws Exception; }
}
