package org.example;

import static org.lwjgl.glfw.GLFW.*;

public final class CompletionChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        CodeCompletion completion = new CodeCompletion();
        for (String primitive : new String[]{"byte", "short", "int", "long", "float", "double", "char", "boolean"}) {
            CodeEditor primitiveEditor = editor(primitive);
            completion.refresh(primitiveEditor, true);
            int index = java.util.stream.IntStream.range(0, completion.items().size())
                    .filter(i -> completion.items().get(i).name().equals(primitive)).findFirst().orElseThrow();
            completion.move(index);
            check(completion.accept(primitiveEditor) && primitiveEditor.text.equals(primitive + " "), "Doplnění primitivního typu " + primitive);
        }
        CodeEditor e = editor("mo");
        completion.refresh(e, false);
        check(completion.isOpen() && completion.items().size() == 1, "Automatická nabídka pohybu");
        check(completion.accept(e) && e.text.equals("move(Direction.RIGHT);") && e.selection().equals("Direction.RIGHT"), "Doplnění s vybraným parametrem");
        e.insert("Direction.LEFT");
        check(e.text.equals("move(Direction.LEFT);"), "Přepsání označeného parametru");
        e.undo(); e.undo();
        check(e.text.equals("mo"), "Undo vrátí původní prefix");
        e = editor("m");
        completion.refresh(e, false);
        check(!completion.isOpen(), "Jeden znak sám neotevře nabídku");
        completion.refresh(e, true);
        check(completion.isOpen(), "Ruční vyvolání funguje i pro jeden znak");
        completion.move(-1);
        check(completion.selected() == completion.items().size() - 1, "Výběr cykluje přes okraj nabídky");
        e = editor("move(Direction.LEFT);"); e.move(2, false);
        completion.refresh(e, true); completion.accept(e);
        check(e.text.equals("move(Direction.LEFT);"), "Doplnění uprostřed názvu zachová argumenty");
        e = editor("pi;"); e.move(2, false);
        completion.refresh(e, true); completion.accept(e);
        check(e.text.equals("pick();"), "Středník se nezdvojí");
        e = editor("Robot.mo");
        completion.refresh(e, false); completion.accept(e);
        check(e.text.equals("Robot.move(Direction.RIGHT);") && e.selection().equals("Direction.RIGHT"), "Doplnění kvalifikovaného pohybu a parametru");
        e = editor("array2");
        completion.refresh(e, false); completion.accept(e);
        check(e.text.equals("int[][] mapa = new int[3][3];"), "Šablona dvourozměrného pole");
        for (String removed : new String[]{"left", "right", "top", "down", "up", "locate"}) {
            e = editor("Robot." + removed); completion.refresh(e, true);
            check(!completion.isOpen(), "Odstraněná metoda se nenabízí: " + removed);
        }
        e = editor("mo");
        completion.refresh(e, false); completion.accept(e);
        check(e.text.equals("move(Direction.RIGHT);") && e.selection().equals("Direction.RIGHT"), "Doplnění pohybu s vybraným směrem");
        e = editor("if (tileT");
        completion.refresh(e, false); completion.accept(e);
        check(e.text.equals("if (tileType()"), "Dotaz na políčko je výraz bez středníku");
        e = editor("int x = robotCoordinatesX");
        completion.refresh(e, false); completion.accept(e);
        check(e.text.equals("int x = robotCoordinatesX()"), "Souřadnice X se doplní jako číselný výraz");
        e = editor("Robot.robotCoordinatesY");
        completion.refresh(e, false); completion.accept(e);
        check(e.text.equals("Robot.robotCoordinatesY()"), "Souřadnice Y se doplní i s kvalifikátorem Robot");
        e = editor("elseif");
        completion.refresh(e, false); completion.accept(e);
        check(e.text.equals("else if (true) {\n    \n}") && e.selection().equals("true"), "Zkratka elseif vloží platnou Java větev else if");
        e = editor("else");
        completion.refresh(e, false); completion.accept(e);
        check(e.text.equals("else {\n    \n}"), "Else vloží blok pro jinou větev");
        e = editor("shelfT");
        completion.refresh(e, false); completion.accept(e);
        check(e.text.equals("shelfType()"), "Doplnění dotazu na sortiment");
        e = editor("cargoT");
        completion.refresh(e, false); completion.accept(e);
        check(e.text.equals("cargoType()"), "Doplnění typu neseného balíku");
        e = editor("orderR");
        completion.refresh(e, false); completion.accept(e);
        check(e.text.equals("orderRemaining(ShelfType.PARTS)") && e.selection().equals("ShelfType.PARTS"), "Doplnění dotazu na aktuální zakázku");
        for (String[] entry : new String[][]{{"Direction.", "LEFT"}, {"Direction.R", "RIGHT"}, {"TileType.SH", "SHELF"},
                {"ShelfType.E", "ELECTRONICS"}, {"ShelfType.T", "TOOLS"}, {"ShelfType.M", "MATERIALS"}, {"TileType.I", "INBOUND"},
                {"Robot.Direction.T", "TOP"}, {"robot.api.Robot.TileType.D", "DOCK"}}) {
            e = editor(entry[0]);
            completion.refresh(e, false); completion.accept(e);
            check(e.text.equals(entry[0].substring(0, entry[0].lastIndexOf('.')+1) + entry[1]), "Doplnění konstanty " + entry[0]);
        }
        e = editor("Robot.");
        completion.refresh(e, false);
        check(completion.items().stream().allMatch(CodeCompletion.Item::call) && completion.isOpen(), "Za Robot. jsou jen metody API");
        e = editor("System.out.pr");
        completion.refresh(e, false); completion.accept(e);
        check(e.text.equals("System.out.println(\"text\");") && e.selection().equals("\"text\""), "Doplnění println");
        e = editor("warehouse.ri");
        completion.refresh(e, true);
        check(!completion.isOpen(), "Cizím objektům nenabízíme robotovy metody");
        for (String source : new String[]{"// ri", "/* ri", "String s = \"ri", "char c = 'r", "String s = \"\"\"\nri", "String s = \"escaped \\\" ri"}) {
            completion.refresh(editor(source), true);
            check(!completion.isOpen(), "Bez návrhů uvnitř komentáře či řetězce: " + source);
        }
        completion.refresh(editor("/* comment */ mo"), false);
        check(completion.isOpen(), "Za uzavřeným komentářem opět doplňujeme");
        completion.refresh(editor("// comment\nmo"), false);
        check(completion.isOpen(), "Za řádkovým komentářem opět doplňujeme");
        e = editor("        fo");
        completion.refresh(e, false); completion.accept(e);
        check(e.text.equals("        for (int i = 0; i < 3; i++) {\n            \n        }"), "Šablona cyklu zachová odsazení");
        check(e.line() == 1 && e.column() == 12, "Kurzor skončí uvnitř těla cyklu");
        e = editor("public static void ma");
        completion.refresh(e, false); completion.accept(e);
        check(e.text.startsWith("public static void main(String[] args)"), "Main nezdvojí už napsané modifikátory");
        check(!e.text.contains("void public"), "Vstupní metoda zůstane platná");
        e = editor("cont");
        completion.refresh(e, false); completion.accept(e);
        String nextIteration = e.text;
        check(nextIteration.equals("continue;"), "Doplnění continue");
        e = editor("brak;"); e.move(4, false);
        completion.refresh(e, false); completion.accept(e);
        String exitLoop = e.text;
        check(exitLoop.equals("break;"), "Oprava brake na break zachová jediný středník");
        e = editor("bre");
        completion.refresh(e, false); completion.accept(e);
        check(e.text.equals("break;"), "Doplnění správného zápisu break");
        e = editor("        swi");
        completion.refresh(e, false); completion.accept(e);
        check(e.selection().equals("cargoType()") && e.text.contains("\n            case PARTS -> {")
                && e.text.contains("\n            case null -> {"), "Switch vybere výraz a zachová odsazení větví");
        e.insert("ShelfType.PARTS");
        String branching = e.text.replace("case PARTS -> {", "case PARTS -> { move(Direction.RIGHT);");
        var controlFlow = new RobotProgram().compile(CodeEditor.wrapLegacyBody(
                "for (int i = 0; i < 6; i++) {\nif (i == 1) " + nextIteration + "\n"
                        + branching + "\nif (i == 3) " + exitLoop + "\n}"));
        check(controlFlow.size() == 3 && controlFlow.stream().allMatch(c -> c.name().equals("right")),
                "Doplněné continue, break a switch se skutečně přeloží a provedou správné větve a iterace");
        e = editor("mo");
        completion.refresh(e, true); completion.accept(e);
        var commands = new RobotProgram().compile(CodeEditor.wrapLegacyBody(e.text));
        check(commands.size() == 1 && commands.getFirst().name().equals("right"), "Doplněný příkaz projde skutečným Java překladačem");
        e = editor("x".repeat(29_998) + "mo");
        e.text = " ".repeat(29_998) + "mo";
        completion.refresh(e, true);
        check(!completion.accept(e) && e.text.length() == 30_000 && e.caret <= e.text.length(), "Doplnění respektuje délkový limit editoru");

        Game game = new Game();
        game.world.setLevel(Level.read(new java.io.StringReader("id=completion-test\nname=Našeptávač\nwidth=5\nheight=4\nstart=0,0\ndock=0,0\ngoal.orange=1\nshelves=orange,4,3,1\n"), "completion-test"));
        game.editor.text = "ro"; game.editor.move(2, false);
        var key = Game.class.getDeclaredMethod("key", int.class, int.class);
        key.setAccessible(true);
        key.invoke(game, GLFW_KEY_SPACE, GLFW_MOD_CONTROL);
        check(game.completion.isOpen(), "Ctrl+mezerník otevře skutečnou nabídku hry");
        key.invoke(game, GLFW_KEY_DOWN, 0);
        key.invoke(game, GLFW_KEY_TAB, 0);
        check(game.editor.text.equals("robotCoordinatesY()") && !game.completion.isOpen(), "Šipka a Tab doplní zvolenou variantu");
        game.editor.text = "ro"; game.editor.move(2, false);
        key.invoke(game, GLFW_KEY_SPACE, GLFW_MOD_CONTROL);
        String status = game.status;
        key.invoke(game, GLFW_KEY_ESCAPE, 0);
        check(!game.completion.isOpen() && game.status.equals(status), "Escape zavře jen nabídku");

        for (int[] size : new int[][]{{640,440}, {800,600}, {1366,768}, {1920,1080}, {3840,2160}}) {
            for (double dpi : new double[]{1, 1.25, 1.5, 2}) {
                UiLayout l = new UiLayout(size[0], size[1], dpi);
                for (int line : new int[]{0, l.visibleRows() - 1}) {
                    e = editor("\n".repeat(line) + "ro");
                    completion.refresh(e, true);
                    var popup = game.dashboard.completionPopup(e, l, completion);
                    check(l.code.contains(popup.bounds()), "Našeptávač zůstane uvnitř editoru i u spodního okraje");
                    check(popup.rows() > 0 && popup.first() + popup.rows() <= completion.items().size(), "Platné viditelné položky nabídky");
                }
            }
        }
        System.out.println("PASS: " + checks + " completion checks (API, snippets, contexts, keys, compilation, DPI).");
    }

    private static CodeEditor editor(String source) {
        CodeEditor e = new CodeEditor(); e.text = source; e.move(source.length(), false); return e;
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
