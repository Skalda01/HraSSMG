package org.example;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/** Skutečné provádění čtyř cyklů, šablony, pravidla levelů a živé čtení for-each. */
public final class LoopChecks {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var completion = new CodeCompletion();
        for (String name : new String[]{"for", "while", "do", "foreach"}) {
            var editor = editor(name);
            completion.refresh(editor, true);
            int index = java.util.stream.IntStream.range(0, completion.items().size()).filter(i -> completion.items().get(i).name().equals(name)).findFirst().orElseThrow();
            completion.move(index); completion.accept(editor);
            check(!editor.text.contains("foreach") && (name.equals("for") || !editor.selection().isBlank()), "Platná Java šablona " + name);
            if (name.equals("do")) check(editor.text.endsWith("while (false);"), "Do-while včetně středníku");
            if (name.equals("foreach")) check(editor.text.contains("for (int hodnota : pole)"), "Enhanced for místo vlastního klíčového slova");
        }
        var editor = editor("foreach");
        completion.refresh(editor, true, Set.of("move")); check(!completion.isOpen(), "For-each se řídí oprávněním for");
        completion.refresh(editor, true, Set.of("for")); check(completion.isOpen(), "Povolený for zpřístupní for-each");
        editor = editor("do"); completion.refresh(editor, true, Set.of("do"));
        check(completion.items().stream().noneMatch(item -> item.name().equals("do")), "Do-while potřebuje i while");
        completion.refresh(editor, true, Set.of("do", "while"));
        check(completion.items().stream().anyMatch(item -> item.name().equals("do")), "Povolený do-while se nabídne");
        var commands = new RobotProgram(Set.of("move", "for", "while", "do")).compile(CodeEditor.wrapLegacyBody("""
                for (int i=0;i<2;i++) move(Direction.RIGHT);
                int i=0;
                while (i<2) { move(Direction.LEFT); i++; }
                i=0;
                do { move(Direction.DOWN); i++; } while(i<2);
                for (Direction smer : new Direction[]{Direction.TOP,Direction.TOP}) move(smer);
                """));
        check(commands.stream().map(RobotProgram.Command::name).toList().equals(List.of("right","right","left","left","down","down","top","top")), "Všechny čtyři cykly skutečně řídí robota");
        check(AssignmentText.blocks("do {\nmove(Direction.RIGHT);\n} while (false);").stream().allMatch(block -> block.kind() == AssignmentText.Kind.CODE), "Formátování do-while v dokumentaci");

        Level level = new LevelCatalog(Path.of("target/no-loop-overrides")).levels().stream().filter(Level::sorting).findFirst().orElseThrow();
        var state = new SortState(); state.reset(level);
        SortingChecks.run(SortingChecks.program(level, "int sum=0; for (int cislo : pole) sum+=cislo; if(sum!=78) throw new AssertionError();"), level, state);
        check(state.reads == 12 && state.writes == 0, "Canvas zachytí každý automaticky čtený prvek");
        state.reset(level);
        SortingChecks.run(SortingChecks.program(level, """
                int count=0;
                outer: for (var cislo : pole) {
                    if(cislo==3) continue outer;
                    count++;
                    if(cislo==5) break outer;
                }
                if(count!=3) throw new AssertionError("label");
                """), level, state);
        check(state.reads == 4, "Labeled continue a break zachovají význam");
        state.reset(level);
        String types = SortingChecks.program(level, """
                for (var cislo : pole) overloaded(cislo);
                for (Integer cislo : pole) { if(cislo==null) throw new AssertionError(); }
                """);
        int brace = types.lastIndexOf('}');
        types = types.substring(0,brace) + "static void overloaded(int v) {} static void overloaded(Integer v) { throw new AssertionError(\"boxing\"); }" + types.substring(brace);
        SortingChecks.run(types, level, state);
        check(state.reads == 24, "Var zachová int a explicitní Integer zachová boxing");
        state.reset(level);
        SortingChecks.run(SortingChecks.program(level, """
                int calls=0;
                int[][] polePole={pole,pole};
                for(int cislo : polePole[calls++]) { }
                if(calls!=1) throw new AssertionError("expression");
                """), level, state);
        check(state.reads == 12, "Výraz pole se vyhodnotí jen jednou");
        state.reset(level);
        String multiline = SortingChecks.program(level, "for (int cislo\n : pole)\n{ int x=cislo; }");
        check(multiline.lines().count() == JavaArrayTracing.instrument(multiline).lines().count(), "For-each zachová čísla řádků");
        SortingChecks.run(multiline, level, state);
        check(state.reads == 12, "Víceřádkový enhanced for");
        state.reset(level);
        SortingChecks.run(SortingChecks.program(level, """
                int[] a=pole;
                for(int cislo : a) { a=new int[]{99}; }
                """), level, state);
        check(state.reads == 12, "Změna proměnné pole nepřeruší běžící for-each");
        System.out.println("PASS: " + checks + " loop checks (for, while, do-while, for-each, templates, permissions, native semantics, live canvas).");
    }
    private static CodeEditor editor(String text) { var editor = new CodeEditor(); editor.setText(text); editor.caret=editor.anchor=text.length(); return editor; }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
}
