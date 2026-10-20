package org.example;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class LevelChecks {
    private static int checks;
    private static final String FIRST_SOLUTION = CodeEditor.wrapLegacyBody("""
            move(Direction.RIGHT);
            move(Direction.RIGHT);
            pick();
            move(Direction.LEFT);
            move(Direction.LEFT);
            move(Direction.LEFT);
            move(Direction.LEFT);
            drop();
            """);
    private static final String CUSTOM = """
            id=custom-test
            name=Česká zkušební úroveň
            description=Doruč dva různé typy nákladu.
            width=5
            height=4
            start=1,2
            dock=0,2
            goal.orange=1
            goal.purple=1
            shelves=orange,3,0,2; purple,4,2,1
            obstacles=2,0; 2,1
            """;

    public static void main(String[] args) throws Exception {
        Level level = parse(CUSTOM);
        check(level.initialCode() == null && level.assignment() == null && level.documentation() == null,
                "Starší definice bez vlastních textů zůstávají podporované");
        String configuredCode = CodeEditor.wrapLegacyBody("// Český text a Java escape sekvence\nString text = \"řádek\\nnový\";\nmove(Direction.RIGHT);");
        String configuredAssignment = "# Vlastní zadání\n\nDoruč balík: pravidla = vlastní.";
        String configuredDocs = "# Vlastní dokumentace\n\nJen informace pro tento level.";
        var properties = new java.util.Properties();
        properties.load(new StringReader(CUSTOM));
        properties.setProperty("id", "content-test");
        properties.setProperty("initialCode", configuredCode);
        properties.setProperty("assignment", configuredAssignment);
        properties.setProperty("documentation", configuredDocs);
        var writer = new java.io.StringWriter(); properties.store(writer, null);
        Level configured = parse(writer.toString());
        check(configured.initialCode().equals(configuredCode) && CodeEditor.starter(configured).equals(configuredCode),
                "Celá Java třída z properties zachová odsazení, diakritiku a escape sekvence");
        check(configured.assignment().equals(configuredAssignment) && configured.documentation() == null,
                "Zadání se načte, původní dokumentace v konfiguraci se ignoruje");
        var configuredCommands = new RobotProgram().compile(CodeEditor.starter(configured));
        check(configuredCommands.size() == 1 && configuredCommands.getFirst().name().equals("right"),
                "Počáteční kód z definice se skutečně přeloží a vykoná");
        Level firstContent = LevelCatalog.defaultLevel();
        check(firstContent.initialCode().equals(CodeEditor.EXAMPLE) && firstContent.initialCode().contains("\n    public static void main"),
                "První úroveň čte počáteční kód a odsazení z víceřádkové properties hodnoty");
        check(!firstContent.assignment().isBlank() && firstContent.documentation() == null,
                "První úroveň načte zadání a ponechá dokumentaci prázdnou");
        check(firstContent.orderTypes().equals(List.of(robot.api.Robot.ShelfType.PARTS))
                        && firstContent.inventoryTypes().equals(List.of(robot.api.Robot.ShelfType.PARTS)),
                "První úroveň načte jedinou objednanou a skladovanou položku z properties");
        Warehouse firstWorld = new Warehouse(firstContent);
        check(firstWorld.goal("orange") == 1 && firstWorld.stockCount("orange") == 1,
                "Objednávka a skladový počet odpovídají aktuálnímu nastavení první úrovně");
        for (var command : new RobotProgram(firstContent.allowedKeywords()).compile(FIRST_SOLUTION)) {
            int guard = 0;
            while (!firstWorld.execute(command)) if (++guard > 100) throw new AssertionError("Počáteční program nedokončil příkaz");
        }
        check(firstWorld.complete() && firstWorld.delivered() == 1 && firstWorld.stockCount("orange") == 0
                        && firstContent.inventoryTypes().size() == 1,
                "Doručení aktualizuje objednávku i sklad; prázdná skladová položka zůstane vidět");
        firstWorld.reset();
        check(firstWorld.delivered() == 0 && firstWorld.stockCount("orange") == 1,
                "Reset obnoví počty objednávky i dolního menu podle konfigurace");
        Level mixed = parse(CUSTOM.replace("goal.orange=1", "goal.orange=0").replace("goal.purple=1", "goal.purple=0")
                .replace("shelves=orange,3,0,2; purple,4,2,1", "shelves=materials,4,2,1; tools,3,0,2; tools,3,3,3; purple,4,3,2")
                + "goal.tools=2\ngoal.materials=1\n");
        check(mixed.orderTypes().equals(List.of(robot.api.Robot.ShelfType.TOOLS, robot.api.Robot.ShelfType.MATERIALS))
                        && mixed.inventoryTypes().equals(List.of(robot.api.Robot.ShelfType.MATERIALS, robot.api.Robot.ShelfType.TOOLS, robot.api.Robot.ShelfType.ELECTRONICS)),
                "Objednávka může chtít jen nástroje a materiál, menu respektuje jiné pořadí a další skladované zboží");
        Warehouse mixedWorld = new Warehouse(mixed);
        check(mixedWorld.goal("tools") == 2 && mixedWorld.stockCount("tools") == 5, "Více polic stejného typu tvoří společnou skladovou položku");
        move(mixedWorld, 3, 3); mixedWorld.pick(); move(mixedWorld, 0, 3); move(mixedWorld, 0, 2); mixedWorld.drop();
        check(mixedWorld.delivered("tools") == 1 && mixedWorld.orderRemaining(robot.api.Robot.ShelfType.TOOLS) == 1
                        && mixedWorld.stockCount("tools") == 4, "Odebrání z druhé police a expedice mění správný typ objednávky i součet skladu");
        check(parse(CUSTOM + "initialCode=   \nassignment=\ndocumentation=\n").initialCode().isEmpty(),
                "Výslovně prázdný počáteční kód zůstává prázdný");
        expect(() -> parse(CUSTOM + "initialCode=" + "x".repeat(30_001)), "initialCode:");
        expect(() -> parse(CUSTOM + "assignment=" + "x".repeat(30_001)), "assignment:");
        check(parse(CUSTOM + "documentation=" + "x".repeat(30_001)).documentation() == null, "Dokumentace z konfigurace se vůbec nepoužívá");
        check(level.width() == 5 && level.height() == 4 && level.name().startsWith("Česká"), "Rozměry a UTF-8 text definice");
        Warehouse world = new Warehouse(level);
        check(world.x == 1 && world.y == 2 && world.blocked(2,0) && !world.blocked(3,0), "Start a překážky podle definice");
        move(world,3,0); world.pick();
        check(world.cargo.equals("orange") && world.stock(level.shelves().getFirst()) == 1, "Zásoba příslušného regálu se sníží");
        move(world,3,2); move(world,0,2); world.drop();
        check(world.delivered() == 1 && !world.complete(), "Částečné splnění cíle");
        move(world,4,2); world.pick(); move(world,0,2); world.drop();
        check(world.complete() && world.delivered() == 2, "Jiná expedice, cíle a rozměry se dají dokončit");
        world.reset();
        check(world.stock(level.shelves().getFirst()) == 2 && world.orange == 0 && world.cargo == null, "Reset obnoví původní zásoby a stav");
        world.x = 4; world.y = 3;
        expect(() -> world.move(1,0), "opustil sklad");
        Level orangeOnly = parse(CUSTOM.replace("goal.purple=1", "goal.purple=0"));
        world.setLevel(orangeOnly);
        move(world,3,0); world.pick(); move(world,3,2); move(world,0,2); world.drop();
        check(world.complete() && world.delivered() == 1, "Úroveň může požadovat jen jeden typ zásilky");
        world.setLevel(level);
        var first = world.level.shelves().getFirst();
        var second = world.level.shelves().get(1);
        world.x = first.cell().x(); world.y = first.cell().y(); world.pick();
        check(world.stock(first) == 1 && world.stock(second) == 1, "Regály nesdílejí jednu zásobu");
        world.cargo = null;
        world.pick(); world.cargo = null;
        expect(world::pick, "prázdný");
        expect(() -> parse(CUSTOM.replace("width=5", "width=26")), "Rozměry");
        expect(() -> parse(CUSTOM.replace("start=1,2", "start=-1,2")), "mimo sklad");
        expect(() -> parse(CUSTOM.replace("obstacles=2,0; 2,1", "obstacles=1,2")), "Start");
        expect(() -> parse(CUSTOM.replace("goal.orange=1", "goal.orange=8")), "dost zásilek");
        expect(() -> parse(CUSTOM.replace("goal.orange=1", "goal.orange=0").replace("goal.purple=1", "goal.purple=0")), "alespoň jeden");
        expect(() -> parse(CUSTOM.replace("purple,4,2,1", "purple,3,0,1")), "překrývá");
        expect(() -> parse(CUSTOM.replace("orange,3,0,2", "orange,0,2,2")), "překrývá");
        expect(() -> parse(CUSTOM.replace("orange,3,0,2", "green,3,0,2")), "Nezn\u00e1m\u00fd typ");
        expect(() -> parse(CUSTOM + "typo=1\n"), "Neznámá vlastnost");
        expect(() -> parse(CUSTOM + "mode=unknown\n"), "mode musí být");
        expect(() -> parse(CUSTOM.replace("id=custom-test", "id=../escape")), "id:");
        String sealed = CUSTOM.replace("obstacles=2,0; 2,1", "obstacles=2,0; 2,1; 2,2; 2,3");
        expect(() -> parse(sealed), "dost zásilek");
        expect(() -> parse(sealed.replace("start=1,2", "start=3,2")), "Expedice není dosažitelná");

        Path temp = Files.createTempDirectory("hrassmg-level-check-");
        try {
            Path definitions = Files.createDirectories(temp.resolve("levels"));
            Path customFile = definitions.resolve("custom.properties");
            Files.writeString(customFile, CUSTOM, StandardCharsets.UTF_8);
            LevelCatalog catalog = new LevelCatalog(definitions);
            int validCount = catalog.levels().size();
            check(validCount >= 4 && catalog.errors().isEmpty(), "Automaticky nalezené přibalené a vlastní úrovně");
            Files.writeString(definitions.resolve("bad.properties"), CUSTOM.replace("width=5", "width=-1"));
            Files.writeString(definitions.resolve("duplicate.properties"), CUSTOM);
            catalog.reload();
            check(catalog.levels().size() == validCount && catalog.errors().size() == 2, "Vadné a duplicitní úrovně neodstaví katalog");
            Files.delete(definitions.resolve("bad.properties")); Files.delete(definitions.resolve("duplicate.properties"));
            Files.writeString(customFile, CUSTOM.replace("goal.orange=1", "goal.orange=2"));
            catalog.reload();
            check(catalog.levels().stream().filter(l -> l.id().equals(level.id())).findFirst().orElseThrow().orangeGoal() == 2, "Obnovení načte upravenou definici");
            Game game = new Game(definitions, temp.resolve("programs"), temp.resolve("first.java.txt"));
            String originalCode = game.editor.text + "\n// vlastní první řešení\n";
            game.editor.setText(originalCode);
            game.running = true; game.toggleLevelMenu();
            check(game.levelMenu && !game.running, "Otevření výběru pozastaví program");
            check(game.activateLevel(level) && game.world.level.equals(level), "Úroveň se opravdu aktivuje ve hře");
            check(game.world.x == 1 && game.robotX == 1 && game.activeLine == 0 && !game.running && !game.levelMenu, "Aktivace vyresetuje přehrávání i robota");
            check(game.editor.text.contains("class Main") && game.tab == Game.EditorTab.ASSIGNMENT, "Nová úroveň má vlastní šablonu a zadání");
            String customCode = CodeEditor.wrapLegacyBody("move(Direction.RIGHT); // vlastní řešení");
            game.editor.setText(customCode);
            var pending = Game.class.getDeclaredField("pending"); pending.setAccessible(true);
            pending.set(game, new CompletableFuture<List<RobotProgram.Command>>());
            check(!game.activateLevel(LevelCatalog.defaultLevel()) && game.world.level.equals(level), "Probíhající překlad nemůže aktivovat jinou mapu");
            pending.set(game, null);
            game.activateLevel(LevelCatalog.defaultLevel());
            check(game.editor.text.equals(originalCode), "Návrat obnoví rozepsané řešení první úrovně");
            check(Files.readString(temp.resolve("programs/custom-test.java.txt")).equals(customCode), "Vlastní program je uložen pod id úrovně");
            game.activateLevel(level);
            check(game.editor.text.equals(customCode), "Návrat obnoví vlastní program konkrétní úrovně");
            game.editor.undo();
            check(game.editor.text.equals(customCode), "Historie undo se nepřenese z jiné úrovně");
            var key = Game.class.getDeclaredMethod("key", int.class, int.class); key.setAccessible(true);
            key.invoke(game, org.lwjgl.glfw.GLFW.GLFW_KEY_F3, 0);
            check(game.levelMenu, "F3 otevře herní výběr úrovní");
            for (int i = 0; i < game.levels.levels().size(); i++) {
                if (game.levels.levels().get(i).id().equals("02-repeated-delivery")) game.levelSelection = i;
            }
            key.invoke(game, org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN, 0);
            key.invoke(game, org.lwjgl.glfw.GLFW.GLFW_KEY_UP, 0);
            key.invoke(game, org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0);
            check(!game.levelMenu && game.world.level.id().equals("02-repeated-delivery"), "Šipka a Enter aktivují vybranou úroveň přes ovládání hry");
            Game reopened = new Game(definitions, temp.resolve("programs"), temp.resolve("first.java.txt"));
            var load = Game.class.getDeclaredMethod("load"); load.setAccessible(true); load.invoke(reopened);
            reopened.activateLevel(level);
            check(reopened.editor.text.equals(customCode), "Program úrovně se obnoví i po novém spuštění");
            var secondLevel = catalog.levels().stream().filter(l -> l.id().equals("02-repeated-delivery")).findFirst().orElseThrow();
            check(secondLevel.start().equals(secondLevel.dock()) && secondLevel.orangeGoal()==3
                    && secondLevel.allowedKeywords().equals(java.util.Set.of("move","pick","drop","for")),
                    "Druhý level začíná na docku, požaduje 3 balíky a povoluje for");
            check(!CodeEditor.starter(secondLevel).contains("move(") && !CodeEditor.starter(secondLevel).contains("for ("),
                    "Druhý level nemá předvyplněné řešení ani cyklus");
            check(CodeEditor.starter(parse(CUSTOM+"initialCode=\n")).isEmpty(), "Výslovně prázdný initialCode vloží prázdný editor");
            world.setLevel(secondLevel);
            String secondSolution=CodeEditor.wrapLegacyBody("""
                    for (int balik=0;balik<3;balik++) {
                        for(int krok=0;krok<4;krok++) move(Direction.RIGHT);
                        pick();
                        for(int krok=0;krok<4;krok++) move(Direction.LEFT);
                        drop();
                    }
                    """);
            for (var command : new RobotProgram(secondLevel.allowedKeywords()).compile(secondSolution)) {
                int guard = 0;
                while (!world.execute(command)) if (++guard > 100) throw new AssertionError("Program druhé úrovně nedokončil příkaz");
            }
            check(world.complete() && world.steps==24 && world.delivered()==3 && world.x==0 && world.y==3,
                    "Druhý level lze splnit třemi stejnými cestami v cyklu for");
            check(game.activateLevel(configured) && game.editor.text.equals(configuredCode),
                    "Aktivace úrovně načte její nakonfigurovanou celou Java třídu");
            game.editor.insert("// rozepsáno\n");
            String edited = game.editor.text;
            game.activateLevel(level);
            game.activateLevel(configured);
            check(game.editor.text.equals(edited), "Uložené řešení má přednost před initialCode");
            game.editor.at(2, 3, false);
            int oldCaret = game.editor.caret;
            move(game.world, 3, 2);
            game.running = true;
            game.selectTab(Game.EditorTab.DOCUMENTATION);
            game.resetCode();
            check(game.editor.text.equals(configuredCode) && !game.running && !game.saved
                            && game.tab == Game.EditorTab.JAVA && game.editor.focused && game.editorView,
                    "Reset obnoví kód aktivní úrovně, zastaví program a zaměří editor");
            check(game.world.x == 3 && game.world.y == 2, "Reset kódu zachová aktuální sklad");
            key.invoke(game, org.lwjgl.glfw.GLFW.GLFW_KEY_Z, org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL);
            check(game.editor.text.equals(edited) && game.editor.caret == oldCaret, "Ctrl+Z vrátí rozepsaný program i polohu kurzoru před resetem");
            key.invoke(game, org.lwjgl.glfw.GLFW.GLFW_KEY_Y, org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL);
            check(game.editor.text.equals(configuredCode), "Ctrl+Y zopakuje obnovení původního kódu");
            game.editor.insert("// ponechat při překladu\n");
            String duringCompile = game.editor.text;
            pending.set(game, new CompletableFuture<RobotProgram.Session>());
            game.resetCode();
            check(game.editor.text.equals(duringCompile), "Probíhající překlad blokuje obnovení kódu");
            pending.set(game, null);
            Game progress = new Game(definitions, temp.resolve("progress-programs"), temp.resolve("progress-first.txt"));
            progress.editor.setText(FIRST_SOLUTION);
            check(!progress.canAdvanceLevel() && !progress.advanceLevel(), "Další level nelze zapnout před splněním úrovně");
            String completedProgram = progress.editor.text;
            for (var command : new RobotProgram().compile(completedProgram)) {
                int guard = 0;
                while (!progress.world.execute(command)) if (++guard > 100) throw new AssertionError("Program nedokončil příkaz");
            }
            check(progress.canAdvanceLevel() && progress.nextLevel().id().equals("02-repeated-delivery"), "Po skutečném splnění je dostupná následující úroveň");
            pending.set(progress, new CompletableFuture<RobotProgram.Session>());
            check(!progress.advanceLevel(), "Během překladu není povolen přechod do další úrovně");
            pending.set(progress, null);
            key.invoke(progress, org.lwjgl.glfw.GLFW.GLFW_KEY_F5, 0);
            check(progress.world.level.id().equals("02-repeated-delivery") && progress.world.steps == 0
                            && progress.world.x == progress.world.level.start().x() && progress.world.y == progress.world.level.start().y()
                            && !progress.running && progress.tab == Game.EditorTab.ASSIGNMENT,
                    "F5 po splnění načte další mapu v počátečním stavu a otevře její zadání");
            check(Files.readString(temp.resolve("progress-first.txt")).equals(completedProgram), "Přechod uloží dokončené řešení předchozí úrovně");
            progress.activateLevel(progress.levels.levels().getLast());
            progress.world.orange = progress.world.goal("orange"); progress.world.purple = progress.world.goal("purple");
            if (progress.world.level.arrayLevel()) {
                progress.sorting.complete = true;
                if (progress.world.level.expectedOutput()!=null) progress.world.captureOutput(progress.world.level.expectedOutput());
                progress.world.finishProgram();
            }
            check((progress.world.level.arraySandbox() ? !progress.levelComplete() : progress.levelComplete()) && !progress.canAdvanceLevel() && !progress.advanceLevel(), "Poslední level nenabízí neexistující pokračování ani návrat na začátek");
        } finally {
            try (var files = Files.walk(temp)) { for (Path path : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
        }
        System.out.println("PASS: " + checks + " level checks (definitions, validation, stock, goals, catalog, activation, persistence).");
    }

    private static Level parse(String source) throws Exception { return Level.read(new StringReader(source), "test"); }
    private static void move(Warehouse world, int x, int y) {
        while (world.x < x) world.move(1,0);
        while (world.x > x) world.move(-1,0);
        while (world.y < y) world.move(0,1);
        while (world.y > y) world.move(0,-1);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
    private static void expect(Action action, String message) throws Exception {
        try { action.run(); } catch (Exception e) { check(e.getMessage().contains(message), "Očekáváno: " + message + "; obdrženo: " + e); return; }
        throw new AssertionError("Očekávána chyba: " + message);
    }
    @FunctionalInterface interface Action { void run() throws Exception; }
}
