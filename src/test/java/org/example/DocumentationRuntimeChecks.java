package org.example;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.lwjgl.opengl.GL;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/** Příklady čte přímo z dokumentace a spouští je přes editor a živou hru. */
public final class DocumentationRuntimeChecks {
    private static int checks;
    private static final String IMPORTS = "import java.util.ArrayList;\nimport static robot.api.Robot.*;\n";

    public static void main(String[] args) throws Exception {
        var catalog = new DocumentationCatalog();
        check(catalog.errors().isEmpty(), "Dokumentace se musí načíst celá: " + catalog.errors());
        var chapter = catalog.chapters().stream().filter(c -> c.id().equals("07-arraylist")).findFirst().orElseThrow();
        List<String> examples = Pattern.compile("```java\\n(.*?)```", Pattern.DOTALL).matcher(chapter.content())
                .results().map(m -> m.group(1)).toList();
        check(examples.size() == 50, "Při změně příkladů aktualizuj kontexty a očekávané výsledky: " + examples.size());
        var directory = Files.createTempDirectory("hrassmg-documentation-check-");
        Game game = new Game(directory.resolve("levels"), directory.resolve("programs"), directory.resolve("first.txt"));
        try {
            method("init", boolean.class).invoke(game, false);
            Level endless = game.levels.levels().stream().filter(Level::endless).findFirst().orElseThrow();
            check(endless.allowedKeywords().isEmpty(), "Nekonečný level dovoluje všechny Java konstrukce");
            check(game.levels.levels().stream().filter(l -> l.id().equals("01-first-shift"))
                    .findFirst().orElseThrow().allowedKeywords().equals(java.util.Set.of("move", "pick", "drop")),
                    "První lekce si zachovává svoje omezení");
            game.activateLevel(endless);
            run(game, arrayListExamples(examples));
            check(game.world.x == 2 && game.world.y == 0, "Kompletní příklad 07 skončí na [2,0]");
            check(game.world.steps > 10, "Příklady opravdu pohybovaly robotem v nekonečném levelu");
            System.out.println("PASS: all 50 literal Java examples from 07.properties, including contextual fragments.");

            String features = Files.readString(Path.of("src/test/resources/documentation/endless-features.java.txt"));
            run(game, features);
            check(game.world.x == 0 && game.world.y == 1 && game.world.totalDelivered == 1,
                    "Java konstrukce a Robot API provedly skutečný příjem, skladování a expedici");
            check(game.world.cargo == null && game.world.totalStored == 1, "Balík nezmizel a není duplikovaný");
            System.out.println("PASS: chapters 01-10 in live endless Game (8 primitives, operators, branches, all loops, switch/yield, jumps, arrays/2D, collections, classes/access, package, algorithms, Robot queries).");

            // Balíček musí fungovat také při offline překladu, komentář nesmí ovlivnit jeho detekci.
            var commands = new RobotProgram(endless.allowedKeywords()).compile("""
                    /* package neexistuje; */
                    package sklad.test;
                    import static robot.api.Robot.*;
                    public class Main { public static void main(String[] args) { move(Direction.RIGHT); } }
                    """);
            check(commands.size() == 1 && commands.getFirst().name().equals("right"), "Offline spuštění používá správný balíček");
            for (String name : List.of("lastIndexOf", "removeIf", "sort")) {
                var editor = new CodeEditor();
                editor.setText("java.util.ArrayList<Integer> cisla = new java.util.ArrayList<>(); cisla." + name);
                editor.caret = editor.anchor = editor.text.length();
                var completion = new CodeCompletion(); completion.refresh(editor, true, endless.allowedKeywords());
                check(completion.items().stream().anyMatch(item -> item.name().equals(name)), "Nápověda obsahuje " + name);
            }
            System.out.println("PASS: " + checks + " host checks; all assertions inside both player programs passed.");
        } finally {
            method("closeSession").invoke(game);
            var compiler = Game.class.getDeclaredField("compiler"); compiler.setAccessible(true);
            ((java.util.concurrent.ExecutorService) compiler.get(game)).shutdownNow();
            game.textRenderer.close();
            long window = glfwGetCurrentContext();
            if (window != 0) { org.lwjgl.glfw.Callbacks.glfwFreeCallbacks(window); glfwDestroyWindow(window); }
            GL.setCapabilities(null); glfwTerminate();
            try (var files = Files.walk(directory)) {
                for (var path : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static String arrayListExamples(List<String> examples) {
        String numbers = "ArrayList<Integer> cisla = new ArrayList<>(java.util.List.of(10,20,30));\n";
        String groups = "ArrayList<ArrayList<Integer>> skupiny = new ArrayList<>(); skupiny.add(new ArrayList<>(java.util.List.of(10,20))); skupiny.add(new ArrayList<>(java.util.List.of(30,40)));\n";
        String history = "ArrayList<Integer> historieX = new ArrayList<>(java.util.List.of(0,1)); ArrayList<Integer> historieY = new ArrayList<>(java.util.List.of(0,0));\n";
        Map<Integer,String> contexts = Map.ofEntries(
                Map.entry(8, "ArrayList<Integer> pocetKroku = new ArrayList<>(java.util.List.of(5,10));\n"),
                Map.entry(18, numbers), Map.entry(22, numbers), Map.entry(24, "ArrayList<Integer> cisla = new ArrayList<>();\n"),
                Map.entry(26, numbers), Map.entry(28, "ArrayList<String> ukoly = new ArrayList<>(java.util.List.of(\"Start\",\"Pohyb\",\"Cil\"));\n"),
                Map.entry(38, "ArrayList<Integer> a = new ArrayList<>(java.util.List.of(10,20));\n"),
                Map.entry(41, groups), Map.entry(42, groups), Map.entry(48, history), Map.entry(49, history));
        Map<Integer,String> assertions = Map.ofEntries(
                Map.entry(8,"kroky == 5"), Map.entry(9,"cisla.equals(java.util.List.of(10,20,30))"),
                Map.entry(10,"cisla.equals(java.util.List.of(5,10,15))"), Map.entry(11,"cisla.equals(java.util.List.of(10,20,30))"),
                Map.entry(13,"a == 10 && b == 20 && c == 30"), Map.entry(14,"cisla.equals(java.util.List.of(10,50,30))"),
                Map.entry(15,"pocet == 3"), Map.entry(16,"prvni == 10 && posledni == 30"), Map.entry(17,"cisla.size() == 1"),
                Map.entry(19,"ukoly.equals(java.util.List.of(\"Start\",\"Cil\"))"), Map.entry(20,"ukoly.size() == 2"),
                Map.entry(21,"cisla.equals(java.util.List.of(10,30))"), Map.entry(22,"cisla.equals(java.util.List.of(10,20))"),
                Map.entry(23,"cisla.isEmpty()"), Map.entry(24,"cisla.get(0) == 50"), Map.entry(25,"existuje"),
                Map.entry(27,"index == 1"), Map.entry(29,"index == 2"), Map.entry(32,"i == 3"),
                Map.entry(33,"cisla.equals(java.util.List.of(2,4,6))"), Map.entry(34,"cisla.equals(java.util.List.of(1,3,5))"),
                Map.entry(35,"cisla.equals(java.util.List.of(1,3))"), Map.entry(36,"cisla.equals(java.util.List.of(10,20,30))"),
                Map.entry(37,"a == b && a.size() == 3"), Map.entry(38,"a != b && a.equals(b)"), Map.entry(41,"hodnota == 30"),
                Map.entry(44,"robotCoordinatesX() == 1 && robotCoordinatesY() == 1"),
                Map.entry(45,"trasa.equals(java.util.List.of(Direction.RIGHT,Direction.DOWN))"),
                Map.entry(46,"trasa.equals(java.util.List.of(Direction.RIGHT,Direction.TOP,Direction.DOWN,Direction.LEFT))"),
                Map.entry(47,"historieX.equals(java.util.List.of(0,1)) && historieY.equals(java.util.List.of(0,0))"),
                Map.entry(48,"x == 0 && y == 0"), Map.entry(49,"x == 1 && y == 0"));
        StringBuilder methods = new StringBuilder(), calls = new StringBuilder(), classes = new StringBuilder();
        for (int index=0; index<examples.size(); index++) {
            int number = index+1;
            String code = examples.get(index).replaceAll("(?m)^import [^;]+;\\s*", "");
            calls.append("presunNa(0,0); example").append(number).append("();\n");
            methods.append("static void example").append(number).append("() {\n");
            if (code.contains("public class Main")) {
                classes.append(code.replace("public class Main", "class Example" + number)).append('\n');
                methods.append("Example").append(number).append(".main(new String[0]);\n");
                if (number == 50) methods.append("require(robotCoordinatesX()==2 && robotCoordinatesY()==0,50);\n");
            } else {
                methods.append(contexts.getOrDefault(number, "")).append(code);
                if (assertions.containsKey(number)) methods.append("\nrequire(").append(assertions.get(number)).append(',').append(number).append(");\n");
            }
            methods.append("}\n");
        }
        return IMPORTS + "public class Main { public static void main(String[] args) {\n" + calls + "}\n"
                + "static void require(boolean ok,int example) { if(!ok) throw new AssertionError(\"07 example \"+example); }\n"
                + methods + "static void presunNa(int x,int y) { while(robotCoordinatesX()<x) move(Direction.RIGHT); while(robotCoordinatesX()>x) move(Direction.LEFT); while(robotCoordinatesY()<y) move(Direction.DOWN); while(robotCoordinatesY()>y) move(Direction.TOP); }\n}\n" + classes;
    }

    private static void run(Game game, String source) throws Exception {
        game.editor.setText(source);
        method("play").invoke(game);
        var session = Game.class.getDeclaredField("session"); session.setAccessible(true);
        var pending = Game.class.getDeclaredField("pending"); pending.setAccessible(true);
        var update = method("update"); var execute = method("execute");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (pending.get(game) != null) {
            update.invoke(game); Thread.sleep(1);
            if (System.nanoTime() > deadline) throw new AssertionError("Překlad: " + game.status);
        }
        if (session.get(game) == null) throw new AssertionError("Překlad: " + game.status + " " + game.world.log);
        while (session.get(game) != null) {
            execute.invoke(game); Thread.sleep(1);
            if (System.nanoTime() > deadline) throw new AssertionError("Běh: " + game.status);
        }
        check(game.status.startsWith("Program skončil."), "Běh hráčova programu: " + game.status + " " + game.world.log);
        method("render").invoke(game); glFinish();
        check(glGetError() == GL_NO_ERROR, "Vykreslení po vykonání příkladů");
    }
    private static java.lang.reflect.Method method(String name, Class<?>... args) throws Exception {
        var method = Game.class.getDeclaredMethod(name, args); method.setAccessible(true); return method;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
}
