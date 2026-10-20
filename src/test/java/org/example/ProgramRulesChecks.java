package org.example;

import java.io.StringReader;
import java.util.Set;

public final class ProgramRulesChecks {
    private static int checks;
    private static final String DEFINITION = """
            id=rules-test
            name=Pravidla
            width=5
            height=4
            start=0,0
            dock=0,0
            goal.orange=1
            shelves=orange,4,3,1
            """;
    public static void main(String[] args) throws Exception {
        Set<String> basic = Set.of("move", "pick", "drop");
        check(parse("").allowedKeywords().isEmpty() && parse("allowedKeywords=   \n").allowedKeywords().isEmpty(), "Chybějící i prázdná položka dovolí vše");
        check(parse("allowedKeywords= move, pick; drop move\n").allowedKeywords().equals(basic), "Seznam přijme oddělovače a odstraní duplicity");
        check(parse("allowedKeywords=foreach,brake\n").allowedKeywords().equals(Set.of("for", "break")), "Aliasy sdílejí oprávnění");
        expect(() -> parse("allowedKeywords=mvoe\n"), "allowedKeywords:");
        ProgramRules.check(program("while (true) { cargoType(); }"), Set.of());
        ProgramRules.check(program("String text = \"for while cargoType()\"; // switch if\n/* continue */ move(Direction.RIGHT);"), basic);
        check(true, "Prázdný seznam a slova v řetězcích či komentářích nic nezakazují");
        expect(() -> ProgramRules.check(program("cargoType();"), basic), "editor:5:");
        expect(() -> ProgramRules.check(program("robot.api.Robot.cargoType();"), basic), "cargoType");
        expect(() -> ProgramRules.check(program("java.util.function.Supplier<robot.api.Robot.ShelfType> f = robot.api.Robot::cargoType;"), basic), "cargoType");
        expect(() -> ProgramRules.check(program("for (int i = 0; i < 2; i++) move(Direction.RIGHT);"), basic), "for");
        expect(() -> ProgramRules.check(program("for (Direction d : Direction.values()) move(d);"), basic), "for");
        expect(() -> ProgramRules.check(program("while (false) {}"), basic), "while");
        expect(() -> ProgramRules.check(program("if (true) pick();"), basic), "if");
        expect(() -> ProgramRules.check(program("do { move(Direction.RIGHT); } while (false);"), Set.of("move", "do")), "while");
        expect(() -> ProgramRules.check(program("switch (1) { default -> {} }"), basic), "switch");
        expect(() -> ProgramRules.check("public class Main { public static void main(String[] args) { helper(); } static void helper() { while (true) {} } }", basic), "while");
        var allowed = Set.of("move", "for", "if", "continue", "break");
        var commands = new RobotProgram(allowed).compile(program("for (int i=0;i<4;i++) { if(i==1) continue; if(i==3) break; move(Direction.RIGHT); }"));
        check(commands.size() == 2, "Povolené konstrukce se skutečně přeloží a vykonají");
        check(parse("allowedKeywords=if,else,elseif,move\n").allowedKeywords().contains("elseif"), "Nastavení přijímá else i elseif");
        var branches = new RobotProgram(Set.of("move", "for", "if", "else", "elseif")).compile(program(
                "for (int i=0;i<3;i++) { if (i==0) move(Direction.RIGHT); else if (i==1) move(Direction.TOP); else move(Direction.LEFT); }"));
        check(branches.stream().map(RobotProgram.Command::name).toList().equals(java.util.List.of("right", "top", "left")),
                "Řetězec if, else if a else vykoná právě správné větve");
        expect(() -> ProgramRules.check(program("if (true) {} else {}"), Set.of("if")), "else");
        expect(() -> ProgramRules.check(program("if (true) {} else if (false) {}"), Set.of("if")), "elseif");
        ProgramRules.check(program("if (true) {} else if (false) {}"), Set.of("if", "elseif"));
        ProgramRules.check(program("if (true) {} else if (false) {} else {}"), Set.of("if", "else"));
        check(true, "Else if lze povolit samostatně i standardní dvojicí if a else");
        expect(() -> new RobotProgram(basic).compile(program("cargoType();")), "cargoType");

        String ownMethods = """
                import static robot.api.Robot.*;
                public class Main {
                    static void cargoType(int x, int y) { move(Direction.RIGHT); }
                    static void cargoType(String text) { move(Direction.LEFT); }
                    public static void main(String[] args) {
                        cargoType(1, 1);
                        Main.cargoType("text");
                        java.util.function.BiConsumer<Integer,Integer> f = Main::cargoType;
                        f.accept(2, 2);
                        new Helper().cargoType(3, 3);
                    }
                }
                class Helper { void cargoType(int x, int y) { move(Direction.DOWN); } }
                """;
        var custom = new RobotProgram(basic).compile(ownMethods);
        check(custom.stream().map(RobotProgram.Command::name).toList().equals(java.util.List.of("right", "left", "right", "down")),
                "Vlastní cargoType, přetížení, kvalifikované volání i reference na metodu jsou povolené");
        StringBuilder declarations = new StringBuilder(), calls = new StringBuilder();
        for (String name : Level.API_KEYWORDS) {
            declarations.append("static void ").append(name).append("() {}\n");
            calls.append(name).append("(); Main.").append(name).append("();\n");
        }
        new RobotProgram(basic).compile("public class Main {" + declarations
                + "public static void main(String[] args) {" + calls + "robot.api.Robot.move(robot.api.Robot.Direction.RIGHT); }}");
        check(true, "Vlastní metody mohou mít libovolný název shodný s Robot API");
        new RobotProgram(basic).compile("""
                public class Main {
                    static class Robot { static void cargoType(int x,int y) {} }
                    public static void main(String[] args) {
                        Robot.cargoType(1,1);
                        java.util.function.BiConsumer<Integer,Integer> f=Robot::cargoType;
                        f.accept(2,2);
                        robot.api.Robot.move(robot.api.Robot.Direction.RIGHT);
                    }
                }
                """);
        check(true, "Vlastní třída Robot není zaměněna za robot.api.Robot");
        expect(() -> ProgramRules.check(ownMethods.replace("move(Direction.RIGHT);", "robot.api.Robot.cargoType();"), basic), "cargoType");
        expect(() -> ProgramRules.check(ownMethods.replace("move(Direction.RIGHT);", "while (true) {}"), basic), "while");
        expect(() -> new RobotProgram(basic).compile(program("missingMethod();")), "editor:5:");
        verifyCustomNavigation();

        CodeEditor editor = new CodeEditor();
        CodeCompletion completion = new CodeCompletion();
        editor.setText("ca"); editor.caret = editor.anchor = 2;
        completion.refresh(editor, true, basic);
        check(!completion.isOpen(), "Zakázané příkazy se nenabízejí");
        editor.setText("mo"); editor.caret = editor.anchor = 2;
        completion.refresh(editor, true, basic);
        check(completion.accept(editor) && editor.text.equals("move(Direction.RIGHT);"), "Povolený příkaz se doplní");
        editor.setText("fo"); editor.caret = editor.anchor = 2;
        completion.refresh(editor, true, basic);
        check(!completion.isOpen(), "Zakázaný cyklus se nenabízí");
        completion.refresh(editor, true, Set.of());
        check(completion.isOpen(), "Neomezený level obnoví všechny návrhy");
        System.out.println("PASS: " + checks + " keyword rule checks (configuration, syntax, aliases, line numbers, execution, completion).");
    }
    private static Level parse(String extra) throws Exception { return Level.read(new StringReader(DEFINITION + extra), "rules-test"); }
    private static void verifyCustomNavigation() throws Exception {
        Level level = new LevelCatalog(java.nio.file.Path.of("target/no-rule-overrides")).levels().stream()
                .filter(l -> l.id().equals("03-custom-navigation")).findFirst().orElseThrow();
        String source = CodeEditor.starter(level).replace("static void presunNa(int x, int y) {", """
                static void presunNa(int x, int y) {
                    while (robotCoordinatesX() < x) move(Direction.RIGHT);
                    while (robotCoordinatesX() > x) move(Direction.LEFT);
                    while (robotCoordinatesY() < y) move(Direction.DOWN);
                    while (robotCoordinatesY() > y) move(Direction.TOP);
                """);
        var world = new Warehouse(level);
        try (var session = new RobotProgram(level.allowedKeywords()).start(source)) {
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            while (!world.complete()) {
                if (System.nanoTime() > deadline || session.finished()) throw new AssertionError("Navigace nedoručila zakázku");
                var command = session.poll();
                if (command == null) { Thread.sleep(1); continue; }
                int[] response;
                if (command.query()) response = world.query(command);
                else {
                    check(java.util.Set.of("left","right","top","down","pick","drop").contains(command.name()), "Vlastní navigace používá jednotlivé kroky, ne vestavěné presunNa");
                    while (!world.execute(command)) {}
                    response = new int[]{world.x, world.y};
                }
                session.acknowledge(response[0], response[1], world.completedOrders+1);
            }
        }
        check(world.delivered()==3 && world.x==level.dock().x() && world.y==level.dock().y(),
                "03-shift: vlastní presunNa se současným allowedKeywords skutečně doručí všechny tři balíčky");
    }
    private static String program(String body) { return CodeEditor.wrapLegacyBody(body); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
    private static void expect(Action action, String text) throws Exception {
        try { action.run(); } catch (Exception e) { check(e.getMessage().contains(text), "Očekáváno " + text + ": " + e); return; }
        throw new AssertionError("Chybí chyba: " + text);
    }
    @FunctionalInterface interface Action { void run() throws Exception; }
}
