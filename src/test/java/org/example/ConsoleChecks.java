package org.example;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.lwjgl.opengl.GL;
import static org.lwjgl.glfw.GLFW.*;

/** Skutečný stdout hráčova procesu, cíle úrovně a konzole ve hře. */
public final class ConsoleChecks {
    private static int checks;
    private static final String PICK = "move(Direction.RIGHT); move(Direction.RIGHT); pick();\n";
    private static final String DEFINITION = """
            id=console-test
            name=Console test
            width=10
            height=10
            start=2,3
            dock=0,3
            goal.orange=0
            goal.purple=0
            shelves=orange,4,3,2
            """;

    public static void main(String[] args) throws Exception {
        var directory=Files.createTempDirectory("hrassmg-console-check-");
        Game game=new Game(directory.resolve("levels"),directory.resolve("programs"),directory.resolve("first.txt"));
        try {
            method("init",boolean.class).invoke(game,false);
            Level sample=game.levels.levels().stream().filter(l->l.id().equals("03-output")).findFirst().orElseThrow();
            check(sample.checksOutput() && sample.expectedOutput().equals("PARTS") && sample.cargoGoal().equals("orange"),"Ukázková konfigurace");
            var editor=new CodeEditor(); editor.setText("sout"); editor.caret=editor.anchor=4;
            var completion=new CodeCompletion(); completion.refresh(editor,true,sample.allowedKeywords());
            check(completion.accept(editor),"Doplnění sout"); editor.insert("cargoType()");
            check(editor.text.equals("System.out.println(cargoType());"),"Sout vloží standardní Java výpis");
            run(game,sample,PICK+editor.text);
            check(game.world.complete() && game.canAdvanceLevel() && game.world.cargo.equals("orange") && game.world.x==4 && game.world.y==3,"Pohyb, vyzvednutí a výpis skutečně splní level");
            check(game.world.consoleOutput.equals("PARTS\n") && game.world.programFinished,"Konzole uchová celý výstup a úspěšný konec programu");
            game.click(new java.awt.geom.Point2D.Double(game.layout.console.getCenterX(),game.layout.console.getCenterY()),0);
            check(game.tab==Game.EditorTab.CONSOLE,"Záložka konzole je dostupná kliknutím");
            preview(game,1366,768,"target/console-preview.png");
            preview(game,800,600,"target/console-compact.png");

            run(game,sample,"System.out.println(\"PARTS\");");
            check(!game.world.complete() && game.world.cargo==null,"Samotný správný text bez balíku nestačí");
            run(game,sample,PICK+"System.out.println(\"parts\");");
            check(!game.world.complete() && game.status.contains("neodpovídá"),"Jiná velikost písmen neprojde");
            run(game,sample,PICK);
            check(!game.world.complete(),"Chybějící výpis neprojde");
            run(game,sample,PICK+"System.out.println(cargoType()); System.out.println(\"navíc\");");
            check(!game.world.complete(),"Nadbytečný výpis neprojde");
            run(game,sample,PICK+"System.out.println(\" PARTS \" );");
            check(!game.world.complete(),"Mezery se porovnávají přesně");
            run(game,sample,PICK+"System.out.print(cargoType());");
            check(game.world.complete() && game.world.consoleOutput.equals("PARTS"),"Print bez koncového newline se zachytí při ukončení");
            run(game,parse("goal.cargo=orange\nexpectedOutput=Náklad: PARTS\n"),PICK+"System.out.print(\"Náklad: \" ); System.out.println(cargoType());");
            check(game.world.complete() && game.world.consoleOutput.equals("Náklad: PARTS\n"),"Print před dotazem nepoškodí protokol a neodstraní mezery");
            run(game,parse("expectedOutput=Řádek 1\\nŘádek 2\n"),"System.out.printf(\"Řádek %d%nŘádek %d\",1,2);");
            check(game.world.complete(),"UTF-8, printf a více řádků bez robotových příkazů");
            run(game,parse("expectedOutput=\n"),"int hodnota=1;");
            check(game.world.complete() && game.world.consoleOutput.isEmpty(),"Výslovně prázdný očekávaný výstup");
            run(game,parse("goal.orange=1\nexpectedOutput=Odesláno\n"),PICK+"move(Direction.LEFT); move(Direction.LEFT); move(Direction.LEFT); move(Direction.LEFT); drop(); System.out.println(\"Odesláno\");");
            check(game.world.complete() && game.world.delivered()==1 && game.world.consoleOutput.equals("Odesláno\n"),"Výpis za posledním drop není předčasně ukončen");
            run(game,parse("goal.cargo=orange\nexpectedOutput=PARTS\n"),PICK+"System.out.println(cargoType()); throw new IllegalStateException(\"runtime test\");");
            check(!game.world.complete() && !game.world.programFinished,"Správný výpis před pádem programu nestačí");
            method("reset").invoke(game);
            check(game.world.consoleOutput.isEmpty() && !game.world.programFinished && !game.world.complete(),"Reset vymaže konzoli i výsledek kontroly");
            check(sample.outputMatches("PARTS\r\n") && !sample.outputMatches("PARTS\n\n"),"CRLF je přenositelné, prázdný řádek navíc je chyba");
            verifyTiles(game);
            Level first=LevelCatalog.defaultLevel();
            check(first.allowedKeywords().equals(java.util.Set.of("move","pick","drop")) && !first.checksOutput() && first.cargoGoal()==null,"První level používá jen základní příkazy bez cíle konzole");
            String firstSolution=PICK+"move(Direction.LEFT); move(Direction.LEFT); move(Direction.LEFT); move(Direction.LEFT); drop();";
            run(game,first,firstSolution);
            check(game.world.complete() && game.world.steps==6 && game.world.cargo==null,"První level: police, pick, dock, drop");
            System.out.println("PASS: "+checks+" console checks (live Game, sout, stdout/print/printf, Unicode, exact matching, objectives, failures, reset, first level, UI).");
        } finally {
            method("closeSession").invoke(game);
            var compiler=Game.class.getDeclaredField("compiler"); compiler.setAccessible(true);
            ((java.util.concurrent.ExecutorService)compiler.get(game)).shutdownNow();
            game.textRenderer.close();
            long window=glfwGetCurrentContext();
            if(window!=0) { org.lwjgl.glfw.Callbacks.glfwFreeCallbacks(window); glfwDestroyWindow(window); }
            GL.setCapabilities(null); glfwTerminate();
            try(var paths=Files.walk(directory)) { for(var path:paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
        }
    }
    private static Level parse(String extra) throws Exception { return Level.read(new java.io.StringReader(DEFINITION+extra),"console-test"); }
    private static void verifyTiles(Game game) throws Exception {
        Level level=game.levels.levels().get(2);
        check(level.id().equals("03-all-tiles") && level.dock()==null && level.shelves().isEmpty() && level.visitPerimeter()
                && level.start().equals(new Level.Cell(0,0)) && level.tileGoal()==16,"Třetí level má 16 políček obvodu a start [0,0]");
        check(CodeEditor.starter(level).equals("import static robot.api.Robot.*;\n\npublic class Main {\n    public static void main(String[] args) {\n    }\n}"),"Třetí level má přesně požadovanou základní strukturu");
        game.activateLevel(level); game.selectTab(Game.EditorTab.JAVA);
        check(game.world.tileType()==robot.api.Robot.TileType.FLOOR,"Počáteční políčko je podlaha, ne dock");
        preview(game,1366,768,"target/tiles-preview.png");
        String solution="""
                for(Direction smer:new Direction[]{Direction.RIGHT,Direction.DOWN,Direction.LEFT,Direction.TOP}) {
                    for(int krok=0;krok<4;krok++) {
                        System.out.println("tile("+robotCoordinatesX()+","+robotCoordinatesY()+")");
                        move(smer);
                    }
                }
                System.out.println("HOTOVO");
                """;
        run(game,level,solution);
        check(game.levelComplete() && game.world.printedTiles.size()==16 && game.world.steps==16
                && game.world.x==0 && game.world.y==0,"Celý obvod a HOTOVO po návratu splní level");
        game.selectTab(Game.EditorTab.CONSOLE); preview(game,1366,768,"target/tiles-console.png");
        String transposed=solution.replace("robotCoordinatesX()+\",\"+robotCoordinatesY()","robotCoordinatesY()+\",\"+robotCoordinatesX()");
        run(game,level,transposed);
        check(!game.levelComplete(),"Záměna y a x neprojde");
        run(game,level,"System.out.println(\"HOTOVO\");");
        check(!game.levelComplete() && game.world.steps==0,"Výpis všech souřadnic bez pohybu neprojde");
        run(game,level,"System.out.println(\"tile(0,0)\");"+solution);
        check(!game.levelComplete() && game.status.contains("vícekrát"),"Duplicitní výpis neprojde");
        run(game,level,solution.replace("System.out.println(\"HOTOVO\");","System.out.println(\"tile(0,0)\");"));
        check(!game.levelComplete(),"Po návratu je nutné HOTOVO místo duplicitního počátečního políčka");
        run(game,level,solution.replace("System.out.println(\"HOTOVO\");",""));
        check(!game.levelComplete(),"Chybějící HOTOVO neprojde");
        run(game,level,"move(Direction.RIGHT); move(Direction.DOWN);");
        check(!game.levelComplete() && !game.world.programFinished,"Pohyb do vnitřku mapy neprojde");
        run(game,level,solution+"System.out.println(\"navíc\");");
        check(!game.levelComplete(),"Výpis za HOTOVO neprojde");
        String userCode="""
                for(int i=0;i<4;i++) { move(Direction.DOWN); System.out.println("tile("+robotCoordinatesX()+","+robotCoordinatesY()+")"); }
                for(int i=0;i<4;i++) { move(Direction.RIGHT); System.out.println("tile("+robotCoordinatesX()+","+robotCoordinatesY()+")"); }
                for(int i=0;i<4;i++) { move(Direction.TOP); System.out.println("tile("+robotCoordinatesX()+","+robotCoordinatesY()+")"); }
                for(int i=0;i<4;i++) {
                    move(Direction.LEFT);
                    if(robotCoordinatesX()==0) {
                        if(robotCoordinatesY()==0) System.out.println("HOTOVO");
                    } else System.out.println("tile("+robotCoordinatesX()+","+robotCoordinatesY()+")");
                }
                """;
        run(game,level,userCode);
        check(!game.levelComplete() && game.world.printedTiles.size()==15,"Výpis X,Y projde, ale předčasné HOTOVO bez počátečního políčka neprojde");
        String corrected="System.out.println(\"tile(0,0)\");\n"+userCode;
        run(game,level,corrected);
        check(game.levelComplete() && game.world.printedTiles.size()==16,"Uživatelův kód X,Y s počátečním výpisem projde");
    }
    private static void run(Game game,Level level,String body) throws Exception {
        game.activateLevel(level); game.selectTab(Game.EditorTab.JAVA); game.editor.setText(CodeEditor.wrapLegacyBody(body));
        method("play").invoke(game);
        var pending=Game.class.getDeclaredField("pending"); pending.setAccessible(true);
        var session=Game.class.getDeclaredField("session"); session.setAccessible(true);
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
        while(pending.get(game)!=null) { method("update").invoke(game); Thread.sleep(1); if(System.nanoTime()>deadline) throw new AssertionError("Compile: "+game.status); }
        if(session.get(game)==null) throw new AssertionError("Compile: "+game.status+game.world.log);
        while(session.get(game)!=null) { method("execute").invoke(game); Thread.sleep(1); if(System.nanoTime()>deadline) throw new AssertionError("Run: "+game.status); }
    }
    private static void preview(Game game,int width,int height,String file) throws Exception {
        game.layout=new UiLayout(width,height,1); game.editorView=true;
        var image=new java.awt.image.BufferedImage(width,height,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var gpu=TextRenderer.class.getDeclaredField("gpu"); gpu.setAccessible(true);
        boolean previous=gpu.getBoolean(game.textRenderer); gpu.setBoolean(game.textRenderer,false);
        var graphics=image.createGraphics();
        try { game.dashboard.draw(graphics,game); }
        finally { graphics.dispose(); gpu.setBoolean(game.textRenderer,previous); }
        ImageIO.write(image,"png",Path.of(file).toFile());
    }
    private static java.lang.reflect.Method method(String name,Class<?>... args) throws Exception { var method=Game.class.getDeclaredMethod(name,args); method.setAccessible(true); return method; }
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); checks++; }
}
