package org.example;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Deset skutečných Java řešení ověřuje data konfigurací, canvas, konzoli a postup. */
public final class ArrayCurriculumChecks {
    private static int checks;
    private static final List<String> NAMES=List.of("Naplň pole","Součet a průměr","Největší číslo","Nejmenší číslo","Sudá a lichá čísla",
            "Hledání čísla","Otoč pole","Posuň hodnoty","Druhé největší číslo");
    private static final List<String> BODIES=List.of(
            """
            int[] pole = new int[6];
            for(int i=0;i<pole.length;i++) pole[i]=(i+1)*3;
            for(int hodnota:pole) System.out.println(hodnota);
            """,
            """
            int soucet=0;
            for(int hodnota:pole) soucet+=hodnota;
            double prumer=(double) soucet / pole.length;
            System.out.println("Součet: "+soucet);
            System.out.println("Průměr: "+prumer);
            """,
            """
            int maximum=pole[0];
            for(int i=1;i<pole.length;i++) if(pole[i]>maximum) maximum=pole[i];
            System.out.println("Největší: "+maximum);
            """,
            """
            int minimum=pole[0];
            for(int i=1;i<pole.length;i++) minimum=Math.min(minimum,pole[i]);
            System.out.println("Nejmenší: "+minimum);
            """,
            """
            int suda=0, licha=0;
            for(int hodnota:pole) { if(hodnota%2==0) suda++; else licha++; }
            System.out.println("Sudá: "+suda);
            System.out.println("Lichá: "+licha);
            """,
            """
            int index=-1;
            for(int i=0;i<pole.length;i++) if(pole[i]==12) { index=i; break; }
            System.out.println("Index: "+index);
            """,
            """
            for(int i=0;i<pole.length/2;i++) {
                int posledni=pole.length-1-i;
                int docasna=pole[i]; pole[i]=pole[posledni]; pole[posledni]=docasna;
            }
            """,
            """
            int posledni=pole[pole.length-1];
            for(int i=pole.length-1;i>0;i--) pole[i]=pole[i-1];
            pole[0]=posledni;
            """,
            """
            int maximum=Integer.MIN_VALUE;
            for(int hodnota:pole) if(hodnota>maximum) maximum=hodnota;
            int druhe=Integer.MIN_VALUE;
            for(int hodnota:pole) if(hodnota<maximum && hodnota>druhe) druhe=hodnota;
            System.out.println("Druhé největší: "+druhe);
            """
    );
    public static void main(String[] args) throws Exception {
        var directory=Files.createTempDirectory("hrassmg-array-curriculum-");
        Game game=new Game(directory.resolve("levels"),directory.resolve("programs"),directory.resolve("first.txt"));
        try {
            var lessons=game.levels.levels().stream().filter(l->l.arrayPractice() && !l.arraySandbox()).toList();
            check(game.levels.errors().isEmpty(),"Konfigurace všech úrovní jsou platné: "+game.levels.errors());
            check(lessons.size()==9 && lessons.stream().map(Level::name).toList().equals(NAMES),"Devět úkolů s polem ve správném pořadí");
            check(game.levels.levels().stream().noneMatch(l->l.name().equals("Vlastní statistika"))
                    && game.levels.levels().stream().anyMatch(Level::arraySandbox),"Statistiku nahradil sandbox");
            check(game.levels.levels().stream().noneMatch(l->l.id().equals("06-array")),"Původní jediná úroveň byla nahrazena");
            for(int i=0;i<lessons.size();i++) {
                Level level=lessons.get(i);
                check(level.isVisible() && level.assignment().contains("# "+(i+1)+". "+NAMES.get(i)),"Název a viditelnost úkolu "+(i+1));
                check(!CodeEditor.starter(level).contains("System.out") && !CodeEditor.starter(level).contains("for("),"Kostra neobsahuje hotové řešení "+(i+1));
                run(game,level,source(level,BODIES.get(i)));
                check(game.levelComplete(),"Řešení úkolu "+(i+1)+" splní pole i konzoli: "+game.sorting.operation+" / "+game.world.consoleOutput);
                check(game.sorting.reads>0,"Canvas sleduje skutečné čtení úkolu "+(i+1));
                if(i==0 || i==6 || i==7) check(game.sorting.writes>0,"Canvas sleduje zápisy úkolu "+(i+1));
                else check(game.sorting.writes==0,"Výpočet zachoval původní pole "+(i+1));
                if(i<lessons.size()-1) check(game.canAdvanceLevel() && game.nextLevel().equals(lessons.get(i+1)),"Postup na další úkol "+(i+2));
            }
            run(game,lessons.get(1),source(lessons.get(1),""));
            check(!game.levelComplete() && !game.world.outputComplete(),"Pouhá deklarace pole nesplní početní úkol");
            run(game,lessons.get(1),source(lessons.get(1),BODIES.get(1).replace("(double) soucet /", "soucet /")));
            check(!game.levelComplete(),"Celočíselný průměr je odmítnut");
            run(game,lessons.get(6),source(lessons.get(6),"int[] jine={18,15,12,9,6,3};"));
            check(!game.levelComplete(),"Otočení musí změnit původní pole, vytvoření jiného pole nestačí");
            run(game,lessons.get(8),source(lessons.get(8),"System.out.println(\"Druhé největší: 18\");"));
            check(!game.levelComplete(),"Maximum není druhá největší různá hodnota");

            game.activateLevel(lessons.getFirst()); game.toggleLevelMenu();
            check(game.levelChapter==Game.LevelChapter.MATERIALS && game.levelChoices().stream().filter(Level::arrayPractice).count()==10,"Všech deset úkolů patří do Podkladů");
            preview(game,"target/array-curriculum-menu-start.png");
            game.moveLevelSelection(999); preview(game,"target/array-curriculum-menu-end.png");
            game.activateLevel(lessons.getLast()); game.selectTab(Game.EditorTab.ASSIGNMENT); preview(game,"target/array-curriculum-last-task.png");
            System.out.println("PASS: "+checks+" array curriculum checks (all 9 Java solutions, outputs, canvas events, progression, mistakes, menu, previews).");
        } finally {
            var compiler=Game.class.getDeclaredField("compiler"); compiler.setAccessible(true); ((java.util.concurrent.ExecutorService)compiler.get(game)).shutdownNow();
            game.textRenderer.close();
            try(var files=Files.walk(directory)) { for(var file:files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(file); }
        }
    }
    private static String source(Level level,String body) {
        String starter=CodeEditor.starter(level);
        if (starter.contains("int[] pole = new int[6];")) body=body.replace("int[] pole = new int[6];\n", "");
        return starter.replace("// Doplň řešení úkolu.",body).replace("// Vytvoř a naplň pole, potom vypiš jeho prvky.",body);
    }
    private static void run(Game game,Level level,String source) throws Exception {
        game.activateLevel(level);
        try(var session=new RobotProgram(level.allowedKeywords(),level.array(),false,level.requiredKeywords()).start(source)) {
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);
            while(!session.finished()) {
                var command=session.poll();
                if(command!=null) { game.sorting.execute(command); game.sorting.advance(1,1); session.acknowledge(0,0,1); }
                else Thread.sleep(1);
                if(System.nanoTime()>deadline) throw new AssertionError("Timeout: "+level.name());
            }
            game.world.captureOutput(session.output()); game.world.finishProgram(); game.sorting.finish();
        }
    }
    private static void preview(Game game,String file) throws Exception {
        game.layout=new UiLayout(1366,768,1); var image=new java.awt.image.BufferedImage(1366,768,java.awt.image.BufferedImage.TYPE_INT_RGB); var g=image.createGraphics();
        try { game.dashboard.draw(g,game); } finally { g.dispose(); }
        javax.imageio.ImageIO.write(image,"png",Path.of(file).toFile());
    }
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); checks++; }
}
