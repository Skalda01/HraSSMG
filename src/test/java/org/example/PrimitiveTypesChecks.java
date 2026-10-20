package org.example;

import java.nio.file.Path;
import java.util.List;

public final class PrimitiveTypesChecks {
    public static void main(String[] args) throws Exception {
        List<String> types = List.of("byte", "short", "int", "long", "float", "double", "char", "boolean");
        var chapters = new DocumentationCatalog();
        var chapter = chapters.chapters().stream().filter(c -> c.id().equals("primitivni-typy")).findFirst().orElseThrow();
        for (String type : types) {
            if (!chapter.content().contains(type)) throw new AssertionError("Chybí dokumentace " + type);
            var blocks = AssignmentText.blocks(type + " promenna;");
            if (blocks.size() != 1 || blocks.getFirst().kind() != AssignmentText.Kind.CODE) throw new AssertionError("Nerozpoznaná deklarace " + type);
            if (AssignmentText.blocks(type + "[] pole;").getFirst().kind() != AssignmentText.Kind.CODE) throw new AssertionError("Nerozpoznaná deklarace pole " + type);
        }
        Level level = new LevelCatalog(Path.of("target/no-primitive-overrides")).levels().stream().filter(Level::sorting).findFirst().orElseThrow();
        String code = SortingChecks.program(level, """
                byte male = 12;
                short stredni = 2000;
                int cele = 100000;
                long velke = 3000000000L;
                float rychlost = 1.5f;
                double vzdalenost = 12.75;
                char znak = 'A';
                boolean nalezeno = true;
                byte[] bytes = {male}; bytes[0]++;
                short[] shorts = {stredni}; shorts[0] += 2;
                int[] ints = {cele}; ints[0] += 3;
                long[] longs = {velke}; longs[0] += 4;
                float[] floats = {rychlost}; floats[0] *= 2;
                double[] doubles = {vzdalenost}; doubles[0] /= 2;
                char[] chars = {znak}; chars[0]++;
                boolean[] flags = {nalezeno}; flags[0] = !flags[0];
                if (bytes[0] != 13 || shorts[0] != 2002 || ints[0] != 100003 || longs[0] != 3000000004L
                        || floats[0] != 3.0f || doubles[0] != 6.375 || chars[0] != 'B' || flags[0]) {
                    throw new AssertionError("Primitivní typy nedodržely sémantiku Javy");
                }
                """);
        var state = new SortState(); state.reset(level);
        SortingChecks.run(code, level, state);
        if (state.writes != 0 || state.value(0) != level.array().getFirst()) throw new AssertionError("Pomocné proměnné a pole změnily vstup");
        System.out.println("PASS: all 8 primitive types, scalar and array operations, documentation and declaration formatting.");
    }
}
