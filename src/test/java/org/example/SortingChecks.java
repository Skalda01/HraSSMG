package org.example;

import java.io.StringReader;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Čistá Java, skutečný proces a zrcadlení int[] do canvasu. */
public final class SortingChecks {
    static int checks;
    static String solution(Level level) {
        return CodeEditor.starter(level).replace("\n\n    }", """

                        for (int end = pole.length - 1; end > 0; end--) {
                            for (int j = 0; j < end; j++) {
                                if (pole[j] > pole[j + 1]) {
                                    int temp = pole[j];
                                    pole[j] = pole[j + 1];
                                    pole[j + 1] = temp;
                                }
                            }
                        }

                    }""");
    }
    static Level level(String values) throws Exception {
        return Level.read(new StringReader("id=test\nname=Test\nmode=bubblesort\narray=" + values), "test");
    }
    static String program(Level level, String body) {
        return CodeEditor.starter(level).replace("\n\n    }", "\n" + body + "\n    }");
    }
    public static void main(String[] args) throws Exception {
        var catalog = new LevelCatalog(Path.of("target", "no-sort-level-overrides"));
        Level level = bundled(Level::sorting);
        check(catalog.errors().isEmpty() && level.allowedKeywords().isEmpty(), "Žádná vlastní klíčová slova ani whitelist");
        check(!CodeEditor.starter(level).contains("import") && CodeEditor.starter(level).contains("int[] pole"), "Pouze běžná Java deklarace pole");
        var state = new SortState(); state.reset(level);
        run(solution(level), level, state);
        state.finish();
        check(state.complete && state.reads > 132 && state.writes > 0, "Skutečný bubble sort přímo nad int[]");
        for (int i = 0; i < state.length(); i++) check(state.value(i) == i + 1, "Canvas odpovídá výslednému Java poli");

        // Tentýž zdroj běží také bez přidání sledování a bez herních metod.
        try (var standalone = new RobotProgram().start(solution(level))) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            while (!standalone.finished()) {
                if (System.nanoTime() > deadline) throw new AssertionError("Standalone Java se nezastavila");
                Thread.sleep(2);
            }
        }
        checks++;

        Level small = level("3,1,2");
        state.reset(small);
        String helpers = program(small, "sort(pole);") + """

                class Helpers {}
                """;
        helpers = helpers.replace("sort(pole);", "for (int end = pole.length - 1; end > 0; end--) for (int j=0;j<end;j++) if(compare(pole,j,j+1)>0) swap(pole,j,j+1);");
        int brace = helpers.indexOf("\n}");
        helpers = helpers.substring(0, brace) + """

                    static int compare(int[] a, int i, int j) { return a[i] - a[j]; }
                    static void swap(int[] a, int i, int j) {
                        int temp = a[i]; a[i] = a[j]; a[j] = temp;
                    }
                """ + helpers.substring(brace);
        run(helpers, small, state); state.finish();
        check(state.complete, "Vlastní compare/swap a pole předané parametrům");

        Level duplicates = level("5,2,5,1,2");
        state.reset(duplicates); run(solution(duplicates), duplicates, state); state.finish();
        check(state.complete && state.value(0) == 1 && state.value(4) == 5, "Zachování duplicitních hodnot");

        state.reset(level); run(CodeEditor.starter(level), level, state); state.finish();
        check(!state.complete && state.writes == 0, "Výchozí deklarace neobsahuje řešení");

        state.reset(level);
        run(program(level, "for (int i=0; i<pole.length; i++) pole[i] = 1;"), level, state); state.finish();
        check(state.sorted() && !state.complete, "Přepsání hodnot není správné třídění");

        // Přiřazení vrací hodnotu, index s vedlejším účinkem se vyhodnotí právě jednou.
        state.reset(small);
        run(program(small, "int i=0; int x = (pole[i++] = pole[1]); if(i!=1 || x!=1) throw new AssertionError(); pole[1]=3;"), small, state);
        check(state.value(0) == 1 && state.value(1) == 3, "Sémantika přiřazení a vyhodnocení indexu");

        // Compound assignment musí načíst levý prvek před vyhodnocením pravé strany.
        state.reset(small);
        run(program(small, "int i=0; pole[i++] += (pole[0]=5); if(i!=1 || pole[0]!=8) throw new AssertionError();"
                + " int old=pole[0]++; if(old!=8 || pole[0]!=9) throw new AssertionError();"
                + " int next=--pole[0]; if(next!=8) throw new AssertionError();"
                + " pole[0] *= 0.5; pole[0] <<= 1L; pole[0] -= 5L;"), small, state);
        check(state.value(0) == 3, "Compound assignment, postfix, prefix, float a long");

        state.reset(small);
        run(program(small, "java.util.Arrays.sort(pole);"), small, state); state.finish();
        check(state.complete, "Závěrečná kontrola čte skutečné pole i po standardní knihovní operaci");

        state.reset(small);
        run(program(small, "String[] text={\"x\"}; if(!text[0].equals(\"x\")) throw new AssertionError();"
                + " char[] letters={'a'}; letters[0]='b'; int[] alias=pole;"
                + " int temp=alias[0]; alias[0]=alias[1]; alias[1]=temp;"), small, state);
        check(state.value(0)==1 && state.value(1)==3, "Aliasy fungují; jiné typy polí zůstávají standardní Javou");

        String multiline = program(small, "pole\n[0]\n = pole\n[1];");
        String traced = JavaArrayTracing.instrument(multiline);
        check(multiline.lines().count() == traced.lines().count(), "Zachování čísel řádků");
        state.reset(small);
        try {
            run(program(small, "pole[999] = 1;"), small, state);
            throw new AssertionError("Chybí standardní chyba indexu");
        } catch (IllegalStateException expected) { check(expected.getMessage().contains("editor:4"), "Chyba odkazuje na původní řádek editoru"); }

        state.reset(small);
        try {
            run("public class Main { public static void main(String[] args) { int[] jine = {9,8}; }}", small, state);
            throw new AssertionError("Chybí vstupní pole");
        } catch (IllegalArgumentException expected) { checks++; }

        var editor = new CodeEditor(); editor.setText("");
        var completion = new CodeCompletion(); completion.refresh(editor, true, level.allowedKeywords(), true);
        check(completion.items().stream().noneMatch(item -> java.util.Set.of("move", "value", "setValue", "compare", "swap").contains(item.name()))
                && completion.items().stream().anyMatch(item -> item.name().equals("for")), "Pouze standardní Java návrhy");
        System.out.println("PASS: " + checks + " pure Java sorting checks (native int[], standalone run, source lines, semantics, canvas, preservation).");
    }
    static void run(String source, Level level, SortState state) throws Exception {
        try (var session = new RobotProgram(level.allowedKeywords(), level.array()).start(source)) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            while (!session.finished()) {
                var command = session.poll();
                if (command != null) {
                    int reply = state.execute(command);
                    if (state.animating()) {
                        double start = state.displayValue(state.left);
                        state.advance(0.12, 0.24);
                        check(state.animating() && state.displayValue(state.left) != start, "Animace Java zápisu");
                        state.advance(0.12, 0.24);
                    }
                    session.acknowledge(reply, 0, 1);
                } else Thread.sleep(1);
                if (System.nanoTime() > deadline) throw new AssertionError("Program se nezastavil");
            }
        }
    }
    static Level bundled(java.util.function.Predicate<Level> mode) throws Exception {
        // Skryté lekce zůstávají testovatelné bez změny uživatelova isVisible.
        try (var paths = java.nio.file.Files.list(Path.of("src/main/resources/levels"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".properties")).sorted().toList()) {
                try (var reader = java.nio.file.Files.newBufferedReader(path)) {
                    Level level = Level.read(reader, path.getFileName().toString().replace(".properties", ""));
                    if (mode.test(level)) return level;
                }
            }
        }
        throw new AssertionError("Chybí konfigurace testované lekce");
    }
    static void check(boolean result, String message) { if (!result) throw new AssertionError(message); checks++; }
}
