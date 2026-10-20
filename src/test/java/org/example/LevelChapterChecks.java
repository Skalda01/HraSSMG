package org.example;

import java.awt.Rectangle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.lwjgl.glfw.GLFW.*;

/** Kategorie nabídky, skutečné události myši/kláves a výběr po filtrování. */
public final class LevelChapterChecks {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var directory=Files.createTempDirectory("hrassmg-level-chapters-");
        var definitions=Files.createDirectory(directory.resolve("levels"));
        for(int i=0;i<10;i++) Files.writeString(definitions.resolve("robot-"+i+".properties"),
                "id=zz-robot-"+i+"\nname=Robot "+i+"\nwidth=2\nheight=2\nstart=0,0\ngoal.position=1,1\n");
        Files.writeString(definitions.resolve("array.properties"),"id=zz-array\nname=Array\nmode=array\narray=0,0\ngoal.array=1,2\n");
        Files.writeString(definitions.resolve("sort.properties"),"id=zz-sort\nname=Sort\nmode=bubblesort\narray=3,1,2\n");
        Files.writeString(definitions.resolve("search.properties"),"id=zz-search\nname=Search\nmode=binarysearch\narray=1,2,3\ntarget=2\n");
        Files.writeString(definitions.resolve("hidden.properties"),"id=zz-hidden\nname=Hidden\nmode=bubblesort\narray=3,1,2\nisVisible=false\n");
        Game game=new Game(definitions,directory.resolve("programs"),directory.resolve("first.txt"));
        try {
            game.layout=new UiLayout(1366,768,1); game.toggleLevelMenu();
            check(game.levelChapter==Game.LevelChapter.ROBOT && game.levelChoices().stream().noneMatch(Level::arrayLevel),"Robot obsahuje pouze robotí úrovně");
            check(game.levelChoices().size()==game.levels.levels().stream().filter(l->!l.arrayLevel()).count(),"Žádná robotí úroveň nechybí");
            click(game,game.layout.levelMaterials);
            check(game.materialsOverview() && game.levelMenuSize()==2 && game.levelChoices().isEmpty(),"Kliknutí na Podklady zobrazí dvě kapitoly místo úrovní");
            key(game,GLFW_KEY_DOWN); key(game,GLFW_KEY_ENTER);
            check(game.materialsChapter==Game.MaterialsChapter.ALGORITHMS && game.levelMenu,"Enter otevře kapitolu Algoritmy bez spuštění úrovně");
            check(game.levelChoices().stream().anyMatch(Level::sorting) && game.levelChoices().stream().anyMatch(Level::searching)
                    && game.levelChoices().stream().noneMatch(Level::arrayPractice),"Algoritmy obsahují bubble sort a binary search bez úkolů s polem");
            check(game.levelChoices().stream().noneMatch(l->l.id().equals("zz-hidden")),"Skrytá vizualizace se nezobrazuje");
            key(game,GLFW_KEY_DOWN); check(game.levelSelection==1,"Šipka vybírá uvnitř Podkladů");
            Level chosen=game.levelChoices().get(1); key(game,GLFW_KEY_ENTER);
            check(!game.levelMenu && game.world.level.equals(chosen),"Enter aktivuje filtrovanou úroveň místo globálního indexu");
            game.toggleLevelMenu(); check(game.levelChapter==Game.LevelChapter.MATERIALS && game.levelChoices().get(game.levelSelection).equals(chosen),"Otevření vybere kapitolu a řádek aktivní vizualizace");
            key(game,GLFW_KEY_ESCAPE); check(game.materialsOverview() && game.levelMenu && game.levelSelection==1,"Escape z algoritmu vrátí výběr kapitol");
            key(game,GLFW_KEY_ENTER); click(game,game.layout.levelBack);
            game.click(new java.awt.geom.Point2D.Double(game.layout.levelList.x+20,game.layout.levelList.y+20),0);
            click(game,game.layout.levelActivate);
            check(game.materialsChapter==Game.MaterialsChapter.ARRAY && !game.levelChoices().isEmpty()
                    && game.levelChoices().stream().allMatch(Level::arrayPractice),"Myš otevře Pole pouze s úkoly práce s polem");
            key(game,GLFW_KEY_LEFT); check(game.levelChapter==Game.LevelChapter.ROBOT,"Vlevo přepne na Robot");
            key(game,GLFW_KEY_RIGHT); check(game.levelChapter==Game.LevelChapter.MATERIALS,"Vpravo přepne na Podklady");
            key(game,GLFW_KEY_TAB); check(game.levelChapter==Game.LevelChapter.ROBOT,"Tab přepíná kapitolu");
            game.moveLevelSelection(999);
            check(game.firstLevelVisible()>0 && game.levelSelection==game.levelChoices().size()-1,"Posun zohledňuje délku aktuální kapitoly");
            int first=game.firstLevelVisible(); Level clicked=game.levelChoices().get(first);
            game.click(new java.awt.geom.Point2D.Double(game.layout.levelList.x+20,game.layout.levelList.y+20),0);
            click(game,game.layout.levelActivate);
            check(game.world.level.equals(clicked),"Kliknutí po posunu aktivuje správný řádek");
            game.toggleLevelMenu(); click(game,game.layout.levelMaterials);
            key(game,GLFW_KEY_ENTER);
            game.levelSelection=game.levelChoices().stream().map(Level::id).toList().indexOf("zz-array");
            String array=Files.readString(definitions.resolve("array.properties"));
            Files.writeString(definitions.resolve("array.properties"),array+"isVisible=false\n");
            key(game,GLFW_KEY_F4);
            check(game.levelChapter==Game.LevelChapter.MATERIALS && game.levelChoices().stream().noneMatch(l->l.id().equals("zz-array"))
                    && game.levelSelection<game.levelChoices().size(),"Obnovení zachová kapitolu a opraví výběr po skrytí");

            var field=LevelCatalog.class.getDeclaredField("levels"); field.setAccessible(true);
            var available=game.levels.levels(); field.set(game.levels,available.stream().filter(Level::arrayLevel).toList());
            game.selectLevelChapter(Game.LevelChapter.ROBOT); Level before=game.world.level;
            game.moveLevelSelection(1); key(game,GLFW_KEY_ENTER); click(game,game.layout.levelActivate);
            check(game.levelChoices().isEmpty() && game.levelSelection==0 && game.world.level.equals(before),"Prázdná kapitola neaktivuje úroveň z jiné kapitoly");
            field.set(game.levels,available.stream().filter(l->!l.arrayLevel()).toList());
            game.selectMaterialsChapter(Game.MaterialsChapter.ALGORITHMS); key(game,GLFW_KEY_ENTER);
            check(game.levelChoices().isEmpty() && game.world.level.equals(before),"Prázdné Algoritmy nespustí cizí úroveň");
            key(game,GLFW_KEY_BACKSPACE); check(game.materialsOverview() && game.levelSelection==1,"Backspace vrátí kapitoly i z prázdného seznamu");
            field.set(game.levels,available);
            try(var files=Files.list(definitions)) { for(var file:files.toList()) Files.delete(file); }
            game.reloadLevels(); game.activateLevel(LevelCatalog.defaultLevel()); game.toggleLevelMenu();
            preview(game,1366,768,"target/level-chapter-robot.png");
            click(game,game.layout.levelMaterials); preview(game,1366,768,"target/level-chapter-materials.png");
            preview(game,640,440,"target/level-chapter-compact.png");
            key(game,GLFW_KEY_ENTER); preview(game,1366,768,"target/level-chapter-array.png");
            click(game,game.layout.levelBack); key(game,GLFW_KEY_DOWN); key(game,GLFW_KEY_ENTER);
            preview(game,1366,768,"target/level-chapter-algorithms.png");
            for(int[] size:new int[][]{{640,440},{800,600},{1366,768},{3840,2160}}) for(double dpi:new double[]{1,1.25,1.5,2}) {
                UiLayout l=new UiLayout(size[0],size[1],dpi);
                check(l.levelDialog.contains(l.levelRobot) && l.levelDialog.contains(l.levelMaterials)
                        && l.levelDialog.contains(l.levelBack) && !l.levelBack.intersects(l.levelList)
                        && !l.levelRobot.intersects(l.levelMaterials) && !l.levelMaterials.intersects(l.levelList),"Kapitoly se vejdou a nepřekrývají seznam při "+size[0]+" / "+dpi);
            }
            System.out.println("PASS: "+checks+" level chapter checks (classification, visibility, mouse, keyboard, scrolling, reload, empty chapter, DPI, previews).");
        } finally {
            var compiler=Game.class.getDeclaredField("compiler"); compiler.setAccessible(true); ((java.util.concurrent.ExecutorService)compiler.get(game)).shutdownNow();
            game.textRenderer.close();
            try(var files=Files.walk(directory)) { for(var file:files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(file); }
        }
    }
    private static void key(Game game,int key) throws Exception { var method=Game.class.getDeclaredMethod("key",int.class,int.class); method.setAccessible(true); method.invoke(game,key,0); }
    private static void click(Game game,Rectangle rect) { game.click(new java.awt.geom.Point2D.Double(rect.getCenterX(),rect.getCenterY()),0); }
    private static void preview(Game game,int width,int height,String file) throws Exception {
        game.layout=new UiLayout(width,height,1); var image=new java.awt.image.BufferedImage(width,height,java.awt.image.BufferedImage.TYPE_INT_RGB); var g=image.createGraphics();
        try { game.dashboard.draw(g,game); } finally { g.dispose(); }
        javax.imageio.ImageIO.write(image,"png",Path.of(file).toFile());
    }
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); checks++; }
}
