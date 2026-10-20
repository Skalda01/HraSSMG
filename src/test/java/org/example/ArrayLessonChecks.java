package org.example;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.lwjgl.opengl.GL;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/** Běžné Java pole, živé čtení a zápis, animace a kontrola výsledku úlohy. */
public final class ArrayLessonChecks {
    private static int checks;
    private static final String FILL = "for(int i=0;i<pole.length;i++) pole[i]=(i+1)*2;\n";
    private static final String SUM = "int soucet=0; for(int hodnota:pole) soucet+=hodnota; System.out.println(\"Součet: \"+soucet);\n";
    private static final String SOLUTION = program(FILL+SUM);

    public static void main(String[] args) throws Exception {
        var directory=Files.createTempDirectory("hrassmg-array-lesson-");
        Game game=new Game(directory.resolve("levels"),directory.resolve("programs"),directory.resolve("first.txt"));
        try {
            Level level=Level.read(new java.io.StringReader("id=array-check\nname=Array check\nmode=array\narray=0,0,0,0,0,0\ngoal.array=2,4,6,8,10,12\nexpectedOutput=Součet: 42\ninitialCode=public class Main {\\n    public static void main(String[] args) {\\n        int[] pole = new int[6];\\n\\n    }\\n}\n"),"array-check");
            check(level.isVisible() && level.arrayLevel() && !level.sorting() && !level.searching(), "Samostatná viditelná lekce pole");
            check(level.array().equals(java.util.List.of(0,0,0,0,0,0)) && level.expectedArray().equals(java.util.List.of(2,4,6,8,10,12)), "Výchozí nuly a cílové hodnoty");
            check(CodeEditor.starter(level).contains("new int[6]") && !CodeEditor.starter(level).contains("for("), "Editor má deklaraci bez hotového řešení");
            try(var session=new RobotProgram().start(SOLUTION)) {
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
                while(!session.finished()) { Thread.sleep(1); if(System.nanoTime()>deadline) throw new AssertionError("Standalone timeout"); }
                check(session.output().equals("Součet: 42\n"), "Zdroj běží i jako obyčejná Java bez herních klíčových slov");
            }
            LiveSortingChecks.invoke(game,"init",new Class<?>[]{boolean.class},false);
            game.activateLevel(level); game.selectTab(Game.EditorTab.JAVA); game.editor.setText(SOLUTION);
            preview(game,"target/array-initial.png",1366,768);
            LiveSortingChecks.key(game,GLFW_KEY_F5,0);
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(25);
            while(!game.sorting.animating()) LiveSortingChecks.tick(game,deadline);
            check(game.sorting.writes==1 && game.sorting.writing && game.sorting.value(0)==2, "První skutečný zápis se animuje");
            LiveSortingChecks.key(game,GLFW_KEY_ESCAPE,0);
            double progress=game.sorting.progress;
            for(int i=0;i<5;i++) { Thread.sleep(5); LiveSortingChecks.invoke(game,"update"); }
            check(game.sorting.progress==progress && game.sorting.writes==1, "Pauza zastaví animaci i program");
            preview(game,"target/array-writing.png",1366,768);
            LiveSortingChecks.key(game,GLFW_KEY_F6,0);
            while(game.sorting.animating()) LiveSortingChecks.tick(game,deadline);
            check(!game.running && game.sorting.writes==1, "F6 dokončí právě jednu operaci");
            game.setSpeedMultiplier(10); LiveSortingChecks.key(game,GLFW_KEY_F5,0);
            while(game.sorting.reads==0) LiveSortingChecks.tick(game,deadline);
            LiveSortingChecks.key(game,GLFW_KEY_ESCAPE,0);
            check(!game.sorting.writing && game.sorting.reads==1 && game.sorting.visited[0], "For-each vyvolává samostatná čtení");
            preview(game,"target/array-reading.png",1366,768);
            preview(game,"target/array-compact.png",640,440);
            LiveSortingChecks.invoke(game,"render"); glFinish(); check(glGetError()==GL_NO_ERROR,"Canvas se vykreslí na GPU");
            LiveSortingChecks.key(game,GLFW_KEY_F5,0); waitRun(game);
            check(game.levelComplete() && game.sorting.reads==6 && game.sorting.writes==6 && game.world.programFinished, "Výsledné pole a součet splní lekci až po skončení programu");
            preview(game,"target/array-complete.png",1366,768);
            String source=game.editor.text; LiveSortingChecks.key(game,GLFW_KEY_R,GLFW_MOD_CONTROL);
            check(!game.levelComplete() && game.sorting.value(0)==0 && game.sorting.writes==0 && game.editor.text.equals(source), "Reset obnoví nuly a zachová kód");
            run(game,level,CodeEditor.starter(level)); check(!game.levelComplete(),"Pouhá deklarace nesplní cíl");
            run(game,level,program(SUM)); check(!game.levelComplete(),"Čtení nul a jiný součet nestačí");
            run(game,level,program(FILL)); check(!game.levelComplete() && game.sorting.value(5)==12,"Správné pole bez výpisu nestačí");
            run(game,level,program(FILL+SUM+"System.out.println(\"navíc\");")); check(!game.levelComplete(),"Nadbytečný výpis je chyba");
            run(game,level,program("pole[6]=1;")); check(!game.levelComplete() && !game.world.programFinished && game.sorting.writes==0,"Přístup mimo pole úlohu nesplní");
            run(game,level,program(FILL+SUM+"Integer.parseInt(\"chyba\");")); check(!game.levelComplete() && !game.world.programFinished,"Pád po správném výsledku není úspěch");
            run(game,level,program("int[] odkaz=pole; napln(odkaz);\n"+SUM).replace("\n}","\nstatic void napln(int[] p) { for(int i=0;i<p.length;i++) { p[i]=(i+1)*2-1; p[i]++; } }\n}"));
            check(game.levelComplete() && game.sorting.writes==12 && game.sorting.reads==12,"Aliasy, parametry pomocné metody a inkrementace se sledují");
            Level unsorted=read("array=0,0,0\ngoal.array=9,1,-4\n");
            run(game,unsorted,"public class Main { public static void main(String[] args) { int[] p=new int[3]; p[0]=9; p[1]=1; p[2]=-4; } }");
            check(game.levelComplete() && !game.sorting.sorted(),"Lekce kontroluje cíl včetně záporných hodnot, ne seřazení");
            try { read("array=0,0\ngoal.array=1\n"); throw new AssertionError("Přijata jiná délka cíle"); }
            catch(IllegalArgumentException e) { check(e.getMessage().contains("goal.array"),"Validace délky cíle"); }
            System.out.println("PASS: "+checks+" array lesson checks (plain Java, live canvas, read/write, pause/step, output, errors, aliases, goals, GPU, previews).");
        } finally {
            LiveSortingChecks.invoke(game,"closeSession");
            var compiler=Game.class.getDeclaredField("compiler"); compiler.setAccessible(true); ((java.util.concurrent.ExecutorService)compiler.get(game)).shutdownNow();
            game.textRenderer.close(); long window=glfwGetCurrentContext();
            if(window!=0) { org.lwjgl.glfw.Callbacks.glfwFreeCallbacks(window); glfwDestroyWindow(window); }
            GL.setCapabilities(null); glfwTerminate();
            try(var files=Files.walk(directory)) { for(var file:files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(file); }
        }
    }
    private static String program(String body) { return "public class Main {\n    public static void main(String[] args) {\n        int[] pole=new int[6];\n"+body+"    }\n}"; }
    private static Level read(String properties) throws Exception { return Level.read(new java.io.StringReader("id=array-test\nname=Array test\nmode=array\n"+properties),"array-test"); }
    private static void run(Game game,Level level,String source) throws Exception {
        game.activateLevel(level); game.selectTab(Game.EditorTab.JAVA); game.editor.setText(source); game.setSpeedMultiplier(10);
        LiveSortingChecks.key(game,GLFW_KEY_F5,0); waitRun(game);
    }
    private static void waitRun(Game game) throws Exception {
        var session=Game.class.getDeclaredField("session"); session.setAccessible(true);
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
        do { LiveSortingChecks.tick(game,deadline); } while(game.compiling() || session.get(game)!=null);
    }
    private static void preview(Game game,String file,int width,int height) throws Exception {
        game.layout=new UiLayout(width,height,1); game.editorView=false;
        var gpu=TextRenderer.class.getDeclaredField("gpu"); gpu.setAccessible(true); boolean previous=gpu.getBoolean(game.textRenderer); gpu.setBoolean(game.textRenderer,false);
        var image=new java.awt.image.BufferedImage(width,height,java.awt.image.BufferedImage.TYPE_INT_RGB); var graphics=image.createGraphics();
        try { game.dashboard.draw(graphics,game); } finally { graphics.dispose(); gpu.setBoolean(game.textRenderer,previous); }
        javax.imageio.ImageIO.write(image,"png",Path.of(file).toFile());
    }
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); checks++; }
}
