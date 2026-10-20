package org.example;

import java.nio.file.Path;
import java.util.List;

/** Pole, 2D pole a ArrayList jsou standardní Java ve všech levelech. */
public final class DataStructuresChecks {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var levels = new LevelCatalog(Path.of("target/no-data-overrides")).levels();
        for (Level level : levels) {
            complete("int[] hodnoty={1,2}; hodnoty.le", "length", "hodnoty.length", level);
            complete("int[][] mapa={{1},{2,3}}; mapa.le", "length", "mapa.length", level);
            complete("int[][] mapa={{1},{2,3}}; mapa[1].le", "length", "mapa[1].length", level);
            complete("int hodnoty[]={1,2}; hodnoty.cl", "clone", "hodnoty.clone()", level);
            complete("var hodnoty=new int[2][3]; hodnoty[0].le", "length", "hodnoty[0].length", level);
            for (String declaration : List.of("java.util.ArrayList<Integer> cisla = new java.util.ArrayList<>();",
                    "import java.util.ArrayList;\nArrayList<Integer> cisla = new ArrayList<>();",
                    "java.util.List<Integer> cisla = new java.util.ArrayList<>();",
                    "var cisla = new java.util.ArrayList<Integer>();")) {
                complete(declaration + " cisla.ge", "get", "cisla.get(0)", level);
                complete(declaration + " cisla.si", "size", "cisla.size()", level);
                complete(declaration + " cisla.ad", "add", "cisla.add(null);", level);
            }
            String body = """
                    int[] hodnoty = {10,20};
                    int[][] mapa = {{1,2},{3}};
                    int[] radek = mapa[0];
                    radek[1] = hodnoty[0];
                    java.util.ArrayList<Integer> cisla = new java.util.ArrayList<>();
                    cisla.add(10); cisla.add(20); cisla.add(1,15);
                    cisla.set(0,5); cisla.remove(Integer.valueOf(15)); cisla.remove(1);
                    java.util.ArrayList<java.util.ArrayList<Integer>> tabulka = new java.util.ArrayList<>();
                    tabulka.add(cisla);
                    java.util.Objects.checkIndex(tabulka.get(0).get(0)-5,1);
                    java.util.Objects.checkIndex(cisla.size()-1,1);
                    java.util.Objects.checkIndex(mapa[0][1]-10,1);
                    cisla.clear(); java.util.Objects.checkIndex(cisla.size(),1);
                    """;
            if (level.arrayLevel()) {
                var state = new SortState(); state.reset(level);
                SortingChecks.run(SortingChecks.program(level, body), level, state);
                check(state.value(0) == level.array().getFirst(), "Pomocné struktury zachovají vstup " + level.id());
            } else {
                var commands = new RobotProgram(level.allowedKeywords()).compile(CodeEditor.wrapLegacyBody(body + "move(Direction.RIGHT);"));
                check(commands.size() == 1 && commands.getFirst().name().equals("right"), "Struktury skutečně fungují i v omezeném levelu " + level.id());
            }
        }
        Level sorting = levels.stream().filter(Level::sorting).findFirst().orElseThrow();
        var state = new SortState(); state.reset(sorting);
        SortingChecks.run(SortingChecks.program(sorting, "int[][] mapa={pole}; java.util.ArrayList<int[]> radky=new java.util.ArrayList<>(); radky.add(mapa[0]); radky.get(0)[0]=radky.get(0)[1];"), sorting, state);
        check(state.reads == 1 && state.writes == 1 && state.value(0) == sorting.array().get(1), "Canvas sleduje vstup i přes 2D alias a ArrayList<int[]>");
        check(AssignmentText.blocks("ArrayList<Integer> cisla = new ArrayList<>();").getFirst().kind() == AssignmentText.Kind.CODE, "Formátování deklarací ArrayList");
        System.out.println("PASS: " + checks + " data structure checks across " + levels.size() + " levels (arrays, 2D arrays, ArrayList, completion, execution, canvas aliases).");
    }
    private static void complete(String source, String name, String ending, Level level) {
        var editor = new CodeEditor(); editor.setText(source); editor.caret=editor.anchor=source.length();
        var completion = new CodeCompletion(); completion.refresh(editor,true,level.allowedKeywords(),level.arrayLevel());
        int item=java.util.stream.IntStream.range(0,completion.items().size()).filter(i->completion.items().get(i).name().equals(name)).findFirst().orElseThrow(()->new AssertionError(source));
        completion.move(item);
        check(completion.accept(editor) && editor.text.endsWith(ending), "Doplnění " + source + " v " + level.id());
    }
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); checks++; }
}
