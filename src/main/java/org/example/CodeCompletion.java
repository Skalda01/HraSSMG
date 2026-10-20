package org.example;

import java.util.List;
import java.util.Locale;
import robot.api.Robot;

/** Lokální návrhy pro API robota a běžné Java konstrukce, bez zásahu do překladače. */
final class CodeCompletion {
    record Item(String name, String label, String template, String description, boolean call) {}
    private static Item call(String name, String parameters, String template, String description) {
        return new Item(name, name + "(" + parameters + ")", template, description, true);
    }
    private static Item keyword(String word) { return new Item(word, word, word + " |", "Klíčové slovo jazyka Java", false); }
    private static Item primitive(String word, String description) { return new Item(word, word, word + " |", description, false); }
    private static Item symbol(String word, String description) { return new Item(word, word, word + "|", description, false); }
    private static final List<Item> CATALOG = List.of(
            new Item("array", "array — jednorozměrné pole", "int[] hodnoty = {|1, 2, 3~};", "Vloží běžnou Java deklaraci pole", false),
            new Item("array2d", "array2d — dvourozměrné pole", "int[][] mapa = new int[|3~][3];", "Vloží běžnou Java deklaraci 2D pole", false),
            new Item("newlist", "newlist — dynamický seznam", "java.util.ArrayList<Integer> seznam = new java.util.ArrayList<>();|", "Vloží ArrayList<Integer> bez nutnosti doplňovat import", false),
            new Item("ArrayList", "ArrayList<E>", "java.util.ArrayList<|Integer~>", "Standardní java.util.ArrayList s proměnlivou velikostí", false),
            new Item("List", "List<E>", "java.util.List<|Integer~>", "Standardní rozhraní java.util.List", false),
            new Item("Random", "java.util.Random", "java.util.Random|", "Generátor náhodných hodnot; vloží plný název bez nutnosti importu", false),
            new Item("newrandom", "newrandom — generátor náhodných hodnot", "java.util.Random nahoda = new java.util.Random();|", "Vytvoří běžný Java generátor Random", false),
            symbol("Math", "Matematické metody a konstanty; Math nepotřebuje import"),
            call("move", "Direction direction", "move(|Direction.RIGHT~);", "Posun o jedno pole: LEFT, RIGHT, TOP nebo DOWN"),
            call("tileType", "", "tileType()|", "Typ políčka: FLOOR, SHELF, DOCK nebo INBOUND"),
            call("robotCoordinatesX", "", "robotCoordinatesX()|", "Aktuální souřadnice X robota jako int; od nuly zleva"),
            call("robotCoordinatesY", "", "robotCoordinatesY()|", "Aktuální souřadnice Y robota jako int; od nuly shora"),
            call("shelfType", "", "shelfType()|", "Sortiment police, mimo polici null"),
            call("cargoType", "", "cargoType()|", "Typ neseného balíku, bez nákladu null"),
            call("orderRemaining", "ShelfType type", "orderRemaining(|ShelfType.PARTS~)", "Kolik balíků daného typu zbývá dodat do aktuální zakázky"),
            call("pick", "", "pick();|", "Vezmi jeden balík z příjmu nebo police"),
            call("drop", "", "drop();|", "Ulož balík do správné police nebo odešli na docku"),
            new Item("for", "for — cyklus", "for (int i = 0; i < 3; i++) {\n    |\n}", "Cyklus se třemi opakováními", false),
            new Item("foreach", "for-each — prvky pole", "for (int hodnota : |pole~) {\n    \n}", "Projde všechny prvky pole nebo kolekce; vloží běžný Java for", false),
            new Item("if", "if — podmínka", "if (|true~) {\n    \n}", "Podmíněné provedení bloku", false),
            new Item("else", "else — jiná větev", "else {\n    |\n}", "Provede se, pokud předchozí podmínky neplatí", false),
            new Item("elseif", "else if — další podmínka", "else if (|true~) {\n    \n}", "Zkratka elseif vloží správný Java zápis else if", false),
            new Item("while", "while — cyklus", "while (|false~) {\n    \n}", "Opakování, dokud je podmínka pravdivá", false),
            new Item("do", "do-while — alespoň jednou", "do {\n    \n} while (|false~);", "Tělo se provede alespoň jednou, podmínka se kontroluje na konci", false),
            new Item("continue", "continue — další iterace", "continue;|", "Přeskočí zbytek aktuální iterace cyklu", false),
            new Item("break", "break — ukončení cyklu", "break;|", "Ukončí nejbližší cyklus nebo klasický switch", false),
            new Item("brake", "brake → break", "break;|", "Vloží správný Java zápis break;", false),
            new Item("switch", "switch — větvení podle typu", "switch (|cargoType()~) {\n    case PARTS -> {\n        \n    }\n    case ELECTRONICS -> {\n        \n    }\n    case TOOLS -> {\n        \n    }\n    case MATERIALS -> {\n        \n    }\n    case null -> {\n        \n    }\n}", "Větvení podle typu balíku; case null obslouží robota bez nákladu", false),
            new Item("main", "main — vstupní metoda", "public static void main(String[] args) {\n    |\n}", "Vlož vstupní metodu programu", false),
            new Item("psvm", "psvm — public static void main", "public static void main(String[] args) {\n    |\n}", "Zkratka pro vstupní metodu main()", false),
            new Item("sout", "sout — System.out.println", "System.out.println(|\"text\"~);", "Vypiš text do standardního výstupu", false),
            call("println", "String text", "println(|\"text\"~);", "Vypiš text do standardního výstupu"),
            keyword("public"), keyword("private"), keyword("protected"), keyword("static"), keyword("void"),
            primitive("byte", "8bitové celé číslo: −128 až 127"),
            primitive("short", "16bitové celé číslo: −32768 až 32767"),
            primitive("int", "32bitové celé číslo; běžný typ pro indexy a počítadla"),
            primitive("long", "64bitové celé číslo; velký literál zapisuj s příponou L"),
            primitive("float", "32bitové desetinné číslo; literál zapisuj s příponou f"),
            primitive("double", "64bitové desetinné číslo; výchozí typ desetinných literálů"),
            primitive("char", "16bitová kódová jednotka UTF-16, například 'A'"),
            primitive("boolean", "Logická hodnota true nebo false"),
            keyword("String"),
            keyword("return"), keyword("new"), keyword("class"), keyword("import"), keyword("final"),
            symbol("Direction", "Enum směru: LEFT, RIGHT, TOP, DOWN"),
            symbol("TileType", "Enum políčka: FLOOR, SHELF, DOCK, INBOUND"),
            symbol("ShelfType", "Enum sortimentu: PARTS, ELECTRONICS, TOOLS, MATERIALS"),
            new Item("true", "true", "true|", "Pravdivá logická hodnota", false),
            new Item("false", "false", "false|", "Nepravdivá logická hodnota", false)
    );
    private static final List<Item> ARRAY_MEMBERS = List.of(
            new Item("length", "length — počet prvků", "length|", "Délka pole bez závorek; u 2D pole počet řádků", false),
            call("clone", "", "clone()|", "Kopie pole; u 2D pole kopíruje pouze vnější pole")
    );
    private static final List<Item> LIST_MEMBERS = List.of(
            call("add", "E value", "add(|null~);", "Přidá prvek na konec seznamu"),
            call("add", "int index, E value", "add(|0~, null);", "Vloží prvek na zadaný index"),
            call("get", "int index", "get(|0~)", "Přečte prvek na indexu"),
            call("set", "int index, E value", "set(|0~, null);", "Nahradí prvek na indexu"),
            call("remove", "int index", "remove(|0~);", "Odstraní prvek na indexu a posune další prvky"),
            call("remove", "Object value", "remove(|null~);", "Odstraní první výskyt hodnoty"),
            call("size", "", "size()|", "Aktuální počet prvků seznamu"),
            call("isEmpty", "", "isEmpty()|", "Vrátí true, pokud je seznam prázdný"),
            call("contains", "Object value", "contains(|null~)", "Zjistí, zda seznam obsahuje hodnotu"),
            call("indexOf", "Object value", "indexOf(|null~)", "Index prvního výskytu, při nenalezení -1"),
            call("lastIndexOf", "Object value", "lastIndexOf(|null~)", "Index posledního výskytu, při nenalezení -1"),
            call("removeIf", "Predicate condition", "removeIf(|hodnota -> false~);", "Odstraní všechny prvky splňující podmínku"),
            call("sort", "Comparator order", "sort(|null~);", "Seřadí seznam; null použije přirozené pořadí"),
            call("clear", "", "clear();|", "Odstraní všechny prvky seznamu"),
            call("addAll", "Collection values", "addAll(|java.util.List.of()~);", "Přidá prvky kolekce"),
            call("toArray", "", "toArray()|", "Vrátí pole Object[] s prvky seznamu")
    );
    private static final List<Item> MATH_MEMBERS = List.of(
            call("abs", "number value", "abs(|0~)", "Absolutní hodnota; u nejmenšího int/long může dojít k přetečení"),
            call("min", "number a, number b", "min(|0~, 1)", "Menší ze dvou hodnot"),
            call("max", "number a, number b", "max(|0~, 1)", "Větší ze dvou hodnot"),
            call("clamp", "number value, number min, number max", "clamp(|0~, 0, 10)", "Omezí hodnotu do intervalu včetně hranic"),
            call("sqrt", "double value", "sqrt(|4.0~)", "Druhá odmocnina jako double"),
            call("cbrt", "double value", "cbrt(|8.0~)", "Třetí odmocnina jako double"),
            call("pow", "double base, double exponent", "pow(|2.0~, 3.0)", "Mocnina jako double"),
            call("round", "double value", "round(|2.5~)", "Zaokrouhlení; double vrací long, float vrací int"),
            call("floor", "double value", "floor(|2.5~)", "Zaokrouhlení dolů, vrací double"),
            call("ceil", "double value", "ceil(|2.5~)", "Zaokrouhlení nahoru, vrací double"),
            call("random", "", "random()|", "Náhodný double od 0 včetně do 1 bez horní hranice"),
            call("sin", "double radians", "sin(|0.0~)", "Sinus úhlu v radiánech"),
            call("cos", "double radians", "cos(|0.0~)", "Kosinus úhlu v radiánech"),
            call("tan", "double radians", "tan(|0.0~)", "Tangens úhlu v radiánech"),
            call("toRadians", "double degrees", "toRadians(|90.0~)", "Převede stupně na radiány"),
            call("toDegrees", "double radians", "toDegrees(|Math.PI~)", "Převede radiány na stupně"),
            call("log", "double value", "log(|1.0~)", "Přirozený logaritmus"),
            call("log10", "double value", "log10(|10.0~)", "Dekadický logaritmus"),
            call("exp", "double value", "exp(|1.0~)", "Exponenciální funkce e na zadanou mocninu"),
            call("hypot", "double x, double y", "hypot(|3.0~, 4.0)", "Délka přepony nebo vzdálenost z rozdílů souřadnic"),
            symbol("PI", "Konstanta π jako double"), symbol("E", "Eulerovo číslo jako double")
    );
    private static final List<Item> RANDOM_MEMBERS = List.of(
            call("nextInt", "int bound", "nextInt(|10~)", "Náhodný int od 0 včetně do bound bez horní hranice; bound musí být kladné"),
            call("nextInt", "int origin, int bound", "nextInt(|1~, 7)", "Náhodný int od origin včetně do bound bez horní hranice"),
            call("nextInt", "", "nextInt()|", "Náhodný int z celého rozsahu typu int"),
            call("nextDouble", "", "nextDouble()|", "Náhodný double od 0 včetně do 1 bez horní hranice"),
            call("nextDouble", "double origin, double bound", "nextDouble(|0.0~, 1.0)", "Náhodný double z intervalu [origin, bound)"),
            call("nextBoolean", "", "nextBoolean()|", "Náhodná hodnota true nebo false"),
            call("nextLong", "", "nextLong()|", "Náhodná hodnota typu long"),
            call("nextFloat", "", "nextFloat()|", "Náhodný float od 0 včetně do 1 bez horní hranice"),
            call("nextGaussian", "", "nextGaussian()|", "Náhodný double s normálním rozdělením, průměrem 0 a odchylkou 1"),
            call("setSeed", "long seed", "setSeed(|42L~);", "Nastaví seed; stejný seed a stejná volání reprodukují posloupnost")
    );

    private List<Item> items = List.of();
    private int selected, start, end;

    List<Item> items() { return items; }
    int selected() { return selected; }
    boolean isOpen() { return !items.isEmpty(); }
    void close() { items = List.of(); selected = 0; }
    void move(int delta) { if (isOpen()) selected = Math.floorMod(selected + delta, items.size()); }
    int firstVisible(int rows) { return Math.max(0, selected - rows + 1); }

    void refresh(CodeEditor editor, boolean explicit) {
        refresh(editor, explicit, java.util.Set.of());
    }

    void refresh(CodeEditor editor, boolean explicit, java.util.Set<String> allowedKeywords) {
        refresh(editor, explicit, allowedKeywords, false);
    }

    void refresh(CodeEditor editor, boolean explicit, java.util.Set<String> allowedKeywords, boolean sorting) {
        close();
        if (editor.selected() || !inCode(editor.text, editor.caret)) return;
        start = editor.caret;
        end = editor.caret;
        while (start > 0 && Character.isJavaIdentifierPart(editor.text.charAt(start - 1))) start--;
        while (end < editor.text.length() && Character.isJavaIdentifierPart(editor.text.charAt(end))) end++;
        String typedPrefix = editor.text.substring(start, editor.caret);
        String prefix = typedPrefix.toLowerCase(Locale.ROOT);
        int qualifierStart = start;
        while (qualifierStart > 0 && (Character.isJavaIdentifierPart(editor.text.charAt(qualifierStart - 1))
                || editor.text.charAt(qualifierStart - 1) == '.')) qualifierStart--;
        String qualifier = editor.text.substring(qualifierStart, start);
        var members = qualifier.equals("Math.") || qualifier.equals("java.lang.Math.")
                ? MATH_MEMBERS : collectionMembers(editor.text.substring(0, start));
        if (members != null) {
            items = members.stream().filter(item -> item.name().toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
            return;
        }
        Enum<?>[] constants = switch (qualifier) {
            case "Direction.", "Robot.Direction.", "robot.api.Robot.Direction." -> Robot.Direction.values();
            case "TileType.", "Robot.TileType.", "robot.api.Robot.TileType." -> Robot.TileType.values();
            case "ShelfType.", "Robot.ShelfType.", "robot.api.Robot.ShelfType." -> Robot.ShelfType.values();
            default -> null;
        };
        if (constants != null) {
            items = java.util.Arrays.stream(constants).filter(value -> value.name().toLowerCase(Locale.ROOT).startsWith(prefix))
                    .map(value -> symbol(value.name(), qualifier + value.name())).toList();
            return;
        }
        boolean robot = qualifier.equals("Robot.") || qualifier.equals("robot.api.Robot.");
        boolean output = qualifier.equals("System.out.");
        if (!qualifier.isEmpty() && !robot && !output) return;
        if (!explicit && prefix.length() < 2 && !robot && !output) return;
        items = CATALOG.stream().filter(item -> item.name().toLowerCase(Locale.ROOT).startsWith(prefix))
                .filter(item -> !sorting || !Level.API_KEYWORDS.contains(Level.canonicalKeyword(item.name())))
                .filter(item -> !sorting || !java.util.Set.of("Direction", "TileType", "ShelfType", "switch").contains(item.name()))
                .filter(item -> Level.permits(allowedKeywords, item.name()))
                .filter(item -> !item.name().equals("do") || Level.permits(allowedKeywords, "while"))
                .filter(item -> !item.name().equals("elseif") || Level.permits(allowedKeywords, "if"))
                .filter(item -> !item.name().equals("switch") || Level.permits(allowedKeywords, "cargoType"))
                .filter(item -> output ? item.name().equals("println") : !item.name().equals("println"))
                .filter(item -> !robot || (item.call() && !item.name().equals("println")))
                .sorted(java.util.Comparator.comparingInt(item -> item.name().startsWith(typedPrefix) ? 0 : 1))
                .toList();
    }

    /** Rozpozná deklarované pole i řádek mapa[index] a běžné List/ArrayList proměnné. */
    private List<Item> collectionMembers(String beforePrefix) {
        int cursor = beforePrefix.length() - 1;
        if (cursor < 0 || beforePrefix.charAt(cursor) != '.') return null;
        cursor--;
        while (cursor >= 0 && Character.isWhitespace(beforePrefix.charAt(cursor))) cursor--;
        int indexes = 0;
        while (cursor >= 0 && beforePrefix.charAt(cursor) == ']') {
            int depth = 1; cursor--;
            while (cursor >= 0 && depth > 0) {
                char ch = beforePrefix.charAt(cursor--);
                if (ch == ']') depth++;
                else if (ch == '[') depth--;
            }
            if (depth != 0) return null;
            indexes++;
            while (cursor >= 0 && Character.isWhitespace(beforePrefix.charAt(cursor))) cursor--;
        }
        int end = cursor + 1;
        while (cursor >= 0 && Character.isJavaIdentifierPart(beforePrefix.charAt(cursor))) cursor--;
        if (end == cursor + 1) return null;
        String name = java.util.regex.Pattern.quote(beforePrefix.substring(cursor + 1, end));
        String source = beforePrefix.substring(0, cursor + 1);
        var array = java.util.regex.Pattern.compile("\\b[\\w$.]+(?:\\s*<[^;={}]+>)?\\s*((?:\\[\\s*\\]\\s*)*)"
                + name + "\\s*((?:\\[\\s*\\]\\s*)*)(?=\\s*[=;,:)])").matcher(source);
        int dimensions = 0;
        while (array.find()) dimensions = (int) (array.group(1) + array.group(2)).chars().filter(ch -> ch == '[').count();
        if (dimensions == 0) {
            var inferred = java.util.regex.Pattern.compile("\\bvar\\s+" + name + "\\s*=\\s*new\\s+[\\w$.]+((?:\\s*\\[[^\\]\\n]*\\])+)").matcher(source);
            while (inferred.find()) dimensions = (int) inferred.group(1).chars().filter(ch -> ch == '[').count();
        }
        if (dimensions > indexes) return ARRAY_MEMBERS;
        if (indexes == 0 && java.util.regex.Pattern.compile("\\b(?:java\\.util\\.)?(?:ArrayList|List)(?:\\s*<[^;={}]+>)?\\s+" + name + "\\b"
                + "|\\bvar\\s+" + name + "\\s*=\\s*new\\s+(?:java\\.util\\.)?ArrayList\\b").matcher(source).find()) return LIST_MEMBERS;
        if (indexes == 0 && java.util.regex.Pattern.compile("\\b(?:java\\.util\\.)?Random\\s+" + name + "\\b"
                + "|\\bvar\\s+" + name + "\\s*=\\s*new\\s+(?:java\\.util\\.)?Random\\b").matcher(source).find()) return RANDOM_MEMBERS;
        return null;
    }

    boolean accept(CodeEditor editor) {
        if (!isOpen()) return false;
        Item item = items.get(selected);
        String template = item.template();
        int replaceEnd = end;
        int after = end;
        while (after < editor.text.length() && (editor.text.charAt(after) == ' ' || editor.text.charAt(after) == '\t')) after++;
        if (item.call() && after < editor.text.length() && editor.text.charAt(after) == '(') {
            // Při opravě jména zachováme už napsané argumenty a středník.
            template = item.name() + "|";
        } else if (template.replace("|", "").replace("~", "").endsWith(";")
                && after < editor.text.length() && editor.text.charAt(after) == ';') {
            replaceEnd = after + 1;
        }
        int rowStart = editor.text.lastIndexOf('\n', start - 1) + 1;
        String leading = editor.text.substring(rowStart, start);
        int spaces = 0;
        while (spaces < leading.length() && leading.charAt(spaces) == ' ') spaces++;
        String indentation = " ".repeat(spaces);
        if (item.name().equals("main")) {
            String modifiers = leading.strip();
            if (List.of("public", "public static", "public static void").contains(modifiers))
                template = template.substring(modifiers.length() + 1);
        }
        template = template.replace("\n", "\n" + indentation);
        int marker = template.indexOf('|');
        int selectionEnd = template.indexOf('~');
        String replacement = template.replace("|", "").replace("~", "");
        if (editor.text.length() - (replaceEnd - start) + replacement.length() > 30_000) { close(); return false; }
        int insertedAt = start;
        editor.anchor = start;
        editor.caret = replaceEnd;
        editor.insert(replacement);
        editor.caret = insertedAt + (marker < 0 ? replacement.length() : marker);
        editor.anchor = selectionEnd < 0 ? editor.caret : insertedAt + selectionEnd - 1;
        editor.reveal();
        close();
        return true;
    }

    /** Rozlišuje komentáře a řetězce, aby návrhy nerušily při psaní textu. */
    private boolean inCode(String source, int caret) {
        int state = 0; // 0 kód, 1 řádkový komentář, 2 blokový, 3 string, 4 char, 5 textový blok
        for (int i = 0; i < caret; i++) {
            char c = source.charAt(i);
            char next = i + 1 < caret ? source.charAt(i + 1) : '\0';
            if (state == 1) { if (c == '\n') state = 0; }
            else if (state == 2) { if (c == '*' && next == '/') { state = 0; i++; } }
            else if (state == 3 || state == 4) {
                if (c == '\\') i++;
                else if (c == (state == 3 ? '"' : '\'')) state = 0;
            } else if (state == 5) {
                if (c == '\\') i++;
                else if (c == '"' && next == '"' && i + 2 < caret && source.charAt(i + 2) == '"') { state = 0; i += 2; }
            } else if (c == '/' && next == '/') { state = 1; i++; }
            else if (c == '/' && next == '*') { state = 2; i++; }
            else if (c == '"') {
                if (next == '"' && i + 2 < caret && source.charAt(i + 2) == '"') { state = 5; i += 2; }
                else state = 3;
            } else if (c == '\'') state = 4;
        }
        return state == 0;
    }
}
