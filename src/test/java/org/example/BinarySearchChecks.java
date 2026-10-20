package org.example;

import java.io.StringReader;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Čistý Java program, nalezený i chybějící cíl, skutečné čtení a návratový index. */
public final class BinarySearchChecks {
    static int checks;
    static Level level(String values, int target) throws Exception {
        return Level.read(new StringReader("id=test-search\nname=Test\nmode=binarysearch\narray=" + values + "\ntarget=" + target), "test");
    }
    static String program(Level level, String body) {
        return CodeEditor.starter(level).replace("\n\n    }", "\n" + body + "\n    }");
    }
    static String solution(Level level) {
        return program(level, """
                        int leva = 0;
                        int prava = pole.length - 1;
                        while (leva <= prava) {
                            int stred = leva + (prava - leva) / 2;
                            int hodnota = pole[stred];
                            if (hodnota == cil) {
                                vysledek = stred;
                                break;
                            } else if (hodnota < cil) {
                                leva = stred + 1;
                            } else {
                                prava = stred - 1;
                            }
                        }
                """);
    }
    public static void main(String[] args) throws Exception {
        Level bundled = SortingChecks.bundled(Level::searching);
        check(bundled.arrayLevel() && !bundled.sorting() && bundled.allowedKeywords().isEmpty(), "Samostatný Java level bez vlastních klíčových slov");
        check(!CodeEditor.starter(bundled).contains("import") && CodeEditor.starter(bundled).contains("int vysledek = -1"), "Pouze vstupní deklarace");
        var state = new SortState();
        state.reset(bundled); run(solution(bundled), bundled, state); state.finish();
        check(state.complete && state.reportedResult == 12 && state.reads == 4, "Skutečné binární hledání 38: čtyři kontroly");
        check(state.visited[7] && state.visited[11] && state.visited[13] && state.visited[12], "Canvas zaznamená pořadí půlení");
        for (int target : new int[]{2, 23, 44, 1, 45, 24}) {
            Level test = level("2,5,8,11,14,17,20,23,26,29,32,35,38,41,44", target);
            state.reset(test); run(solution(test), test, state); state.finish();
            check(state.complete, "Cíl " + target + ": hranice, střed a nenalezení");
        }
        Level duplicate = level("2,5,5,5,8", 5);
        state.reset(duplicate); run(solution(duplicate), duplicate, state); state.finish();
        check(state.complete && state.reportedResult >= 1 && state.reportedResult <= 3, "Libovolný správný index duplicitní hodnoty");
        state.reset(bundled); run(CodeEditor.starter(bundled), bundled, state); state.finish();
        check(!state.complete && state.reads == 0, "Prázdná kostra neuspěje");
        state.reset(bundled); run(program(bundled, "int x = pole[7]; vysledek = 7;"), bundled, state); state.finish();
        check(!state.complete, "Nesprávný index neuspěje");
        state.reset(bundled); run(program(bundled, "vysledek = 12;"), bundled, state); state.finish();
        check(!state.complete, "Pouhé napsání odpovědi bez hledání nestačí");
        state.reset(bundled); run(program(bundled, "int x = pole[12]; vysledek = 12; pole[0] = 99;"), bundled, state); state.finish();
        check(!state.complete, "Hledání nesmí měnit pole");

        // Rekurzivní metoda a obyčejná návratová hodnota přiřazená v main.
        String recursive = program(bundled, "vysledek = najdi(pole, cil, 0, pole.length - 1);");
        int brace = recursive.lastIndexOf('}');
        recursive = recursive.substring(0, brace) + """
                    static int najdi(int[] a, int cil, int leva, int prava) {
                        if (leva > prava) return -1;
                        int stred = leva + (prava - leva) / 2;
                        if (a[stred] == cil) return stred;
                        if (a[stred] < cil) return najdi(a, cil, stred + 1, prava);
                        return najdi(a, cil, leva, stred - 1);
                    }
                }
                """;
        state.reset(bundled); run(recursive, bundled, state); state.finish();
        check(state.complete, "Vlastní rekurzivní metoda v čisté Javě");
        try (var standalone = new RobotProgram().start(solution(bundled))) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!standalone.finished()) { Thread.sleep(2); if (System.nanoTime() > deadline) throw new AssertionError("Standalone timeout"); }
            checks++;
        }
        try { level("5,1,3", 3); throw new AssertionError("Neseřazený vstup přijat"); }
        catch (IllegalArgumentException expected) { checks++; }
        System.out.println("PASS: " + checks + " binary search checks (pure Java, live probes, result, boundaries, missing values, duplicates, recursion).");
    }
    static void run(String code, Level level, SortState state) throws Exception {
        try (var session = new RobotProgram(level.allowedKeywords(), level.array(), true).start(code)) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            while (!session.finished()) {
                var command = session.poll();
                if (command != null) {
                    state.execute(command);
                    if (state.animating()) {
                        check(state.probe >= 0 || state.writes > 0, "Animace navazuje na skutečnou operaci");
                        state.advance(0.5, 0.5);
                    }
                    session.acknowledge(0, 0, 1);
                } else Thread.sleep(1);
                if (System.nanoTime() > deadline) throw new AssertionError("Timeout");
            }
        }
    }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
}
