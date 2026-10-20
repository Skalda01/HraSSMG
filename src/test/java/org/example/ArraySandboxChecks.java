package org.example;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.lwjgl.opengl.GL;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/** Skutečná dynamická pole bez cíle: tvorba, nové délky, knihovny a stránkování. */
public final class ArraySandboxChecks {
    private static int checks;
    private static Level level;
    private record Result(SortState state,String output,int commands) {}
    public static void main(String[] args) throws Exception {
        level=SortingChecks.bundled(Level::arraySandbox);
        check(level.arrayPractice() && level.array().isEmpty() && level.expectedArray().isEmpty() && !level.checksOutput(),"Sandbox nemá předepsaný vstup ani výsledek");
        Result r=run("int[] p={-5,0,17}; p[1]=Math.abs(p[0]); p[2]+=4; System.out.println(java.util.Arrays.toString(p));");
        check(r.state.length()==3 && r.state.value(1)==5 && r.state.value(2)==21 && r.output.equals("[-5, 5, 21]\n"),"Libovolná inicializace, čtení i zápis a konzole");
        check(!r.state.complete,"Sandbox se neoznačuje za splněný úkol");
        r=run("int n=75; int[] p=new int[n]; for(int i=0;i<p.length;i++) p[i]=i*3; System.out.println(p.length);");
        check(r.state.length()==75 && r.state.value(74)==222 && r.state.writes==75,"Délku vypočítává hráčův program a není omezena na 32");
        r=run("int[] p={4,2,9}; p=java.util.Arrays.copyOf(p,130); p[129]=33; java.util.Arrays.fill(p,3,8,-7); java.util.Arrays.sort(p);");
        check(r.state.length()==130 && r.state.value(0)==-7 && r.state.value(129)==33 && r.state.sorted(),"Změna délky, copyOf, fill a sort se přenesou do canvasu");
        r=run("int[] a={1,2}; int[] b={7,9,11}; a[0]++; a[1]+=(b[0]=4); int v=b[2]; System.arraycopy(b,0,a,0,2); System.out.println(v);");
        check(r.state.length()==2 && r.state.arrayId==1 && r.state.value(0)==4 && r.state.value(1)==9 && r.output.equals("11\n"),"Přepínání více polí a arraycopy do právě neaktivního pole");
        r=run("int[][] p={{1,2,3},{4,5}}; int i=0; java.util.Arrays.fill(p[i++],8); if(i!=1) throw new AssertionError();");
        check(r.state.length()==3 && r.state.value(0)==8 && r.state.value(2)==8,"Argument knihovní operace se vyhodnotí jednou");
        r=run("int[] p; p=new int[2]; int[] stejny=p; stejny[1]=42;");
        check(r.state.length()==2 && r.state.arrayId==1 && r.state.value(1)==42,"Přiřazení po deklaraci a alias stejného pole");
        r=run("int[] p=new int[0];"); check(r.state.length()==0 && r.state.arrayId==1,"Prázdné pole je platné");
        r=run("System.out.println(Math.sqrt(16));"); check(r.state.length()==0 && r.output.equals("4.0\n"),"Program může běžet i bez pole");
        r=run("System.out.print(\"stav: \" ); int[] p=new int[100003]; p[0]=Integer.MAX_VALUE; p[100002]=Integer.MIN_VALUE; System.out.println(\"hotovo\");");
        check(r.state.length()==100003 && r.state.value(0)==Integer.MAX_VALUE && r.state.value(100002)==Integer.MIN_VALUE,"Velké pole a celý rozsah int");
        check(r.output.equals("stav: hotovo\n") && r.commands<1000,"Blokový přenos se nemíchá do stdout a neposílá příkaz pro každý prvek");
        live();
        System.out.println("PASS: "+checks+" sandbox checks (dynamic sizes, arrays, aliases, bulk operations, large transfers, plain Java, live Game, paging, pause/step, GPU).");
    }
    private static Result run(String body) throws Exception {
        var state=new SortState(); state.reset(level); int count=0;
        try(var session=new RobotProgram(Set.of(),List.of(),false,Set.of(),true).start(program(body))) {
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
            while(!session.finished()) {
                var command=session.poll();
                if(command!=null) { state.execute(command); state.advance(1,1); count++; session.acknowledge(0,0,1); }
                else Thread.sleep(1);
                if(System.nanoTime()>deadline) throw new AssertionError("Sandbox timeout");
            }
            state.finish(); return new Result(state,session.output(),count);
        }
    }
    private static void live() throws Exception {
        var directory=Files.createTempDirectory("hrassmg-sandbox-");
        Game game=new Game(directory.resolve("levels"),directory.resolve("programs"),directory.resolve("first.txt"));
        try {
            LiveSortingChecks.invoke(game,"init",new Class<?>[]{boolean.class},false);
            game.activateLevel(level); game.selectTab(Game.EditorTab.JAVA);
            game.editor.setText(program("int[] p=new int[250]; p[249]=12;"));
            LiveSortingChecks.key(game,GLFW_KEY_F5,0);
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(25);
            while(!game.sorting.animating()) LiveSortingChecks.tick(game,deadline);
            check(game.sorting.length()==250 && game.sorting.value(249)==12,"Game předává režim sandbox i při prázdném vstupu konfigurace");
            LiveSortingChecks.key(game,GLFW_KEY_ESCAPE,0);
            double progress=game.sorting.progress;
            for(int i=0;i<3;i++) LiveSortingChecks.tick(game,deadline);
            check(game.sorting.progress==progress,"Pauza zastaví operaci sandboxu");
            preview(game,1366,768,"target/sandbox-large.png");
            int first=first(game), capacity=game.dashboard.arrayCapacity(game.sorting,game.layout);
            check(first<=249 && first+capacity>249,"Aktivní index se automaticky zobrazí");
            game.click(new java.awt.geom.Point2D.Double(game.layout.arrayPrevious.getCenterX(),game.layout.arrayPrevious.getCenterY()),0);
            check(first(game)<first,"Šipka stránkuje velké pole");
            game.dashboard.scrollArray(9999,game.sorting,game.layout);
            check(first(game)<250 && first(game)+capacity>=250,"Posun se zastaví na poslední stránce");
            preview(game,640,440,"target/sandbox-compact.png");
            check(first(game)<=249 && first(game)+game.dashboard.arrayCapacity(game.sorting,game.layout)>249,"Změna rozlišení zachová zobrazení aktivního indexu");
            LiveSortingChecks.invoke(game,"render"); glFinish(); check(glGetError()==GL_NO_ERROR,"Dynamický canvas se vykreslí na GPU");
            LiveSortingChecks.key(game,GLFW_KEY_F6,0);
            while(game.sorting.animating()) LiveSortingChecks.tick(game,deadline);
            check(!game.running && game.sorting.writes==1,"F6 dokončí právě jednu operaci");
            game.setSpeedMultiplier(10); LiveSortingChecks.key(game,GLFW_KEY_F5,0);
            var session=Game.class.getDeclaredField("session"); session.setAccessible(true);
            while(session.get(game)!=null) LiveSortingChecks.tick(game,deadline);
            check(game.world.programFinished && !game.levelComplete() && !game.canAdvanceLevel(),"Konec sandboxu ponechá data bez splnění úkolu");
            String code=game.editor.text; LiveSortingChecks.key(game,GLFW_KEY_R,GLFW_MOD_CONTROL);
            check(game.sorting.length()==0 && game.editor.text.equals(code),"Reset vyprázdní canvas a ponechá kód");
        } finally {
            LiveSortingChecks.invoke(game,"closeSession");
            var compiler=Game.class.getDeclaredField("compiler"); compiler.setAccessible(true); ((java.util.concurrent.ExecutorService)compiler.get(game)).shutdownNow();
            game.textRenderer.close(); long window=glfwGetCurrentContext();
            if(window!=0) { org.lwjgl.glfw.Callbacks.glfwFreeCallbacks(window); glfwDestroyWindow(window); }
            GL.setCapabilities(null); glfwTerminate();
            try(var files=Files.walk(directory)) { for(var file:files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(file); }
        }
    }
    private static int first(Game game) throws Exception { var f=Dashboard.class.getDeclaredField("firstArrayIndex"); f.setAccessible(true); return f.getInt(game.dashboard); }
    private static String program(String body) { return "public class Main { public static void main(String[] args) {\n"+body+"\n} }"; }
    private static void preview(Game game,int width,int height,String file) throws Exception {
        game.layout=new UiLayout(width,height,1); game.editorView=false;
        var gpu=TextRenderer.class.getDeclaredField("gpu"); gpu.setAccessible(true); boolean previous=gpu.getBoolean(game.textRenderer); gpu.setBoolean(game.textRenderer,false);
        var image=new java.awt.image.BufferedImage(width,height,java.awt.image.BufferedImage.TYPE_INT_RGB); var graphics=image.createGraphics();
        try { game.dashboard.draw(graphics,game); } finally { graphics.dispose(); gpu.setBoolean(game.textRenderer,previous); }
        javax.imageio.ImageIO.write(image,"png",Path.of(file).toFile());
    }
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); checks++; }
}
