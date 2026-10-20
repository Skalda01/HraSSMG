package org.example;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.lwjgl.glfw.GLFW;

/** Změny viditelnosti v konfiguraci i nabídce hry, bez otevření okna. */
public final class VisibilityChecks {
    private static int checks;
    private static final String DEFINITION = """
            id=zz-visibility-test
            name=Visibility test
            width=2
            height=2
            start=0,0
            goal.position=1,1
            """;

    public static void main(String[] args) throws Exception {
        var directory=Files.createTempDirectory("hrassmg-visibility-");
        Game game=new Game(Files.createDirectory(directory.resolve("levels")),directory.resolve("programs"),directory.resolve("first.txt"));
        try {
            check(read("").isVisible(), "Chybějící isVisible znamená true");
            check(read("isVisible=true\n").isVisible() && !read("isVisible=false\n").isVisible(), "Obě hodnoty se načtou");
            try { read("isVisible=falce\n"); throw new AssertionError("Přijat překlep"); }
            catch (IllegalArgumentException e) { check(e.getMessage().contains("isVisible"), "Neplatná hodnota má srozumitelnou chybu"); }
            var file=directory.resolve("levels/visible.properties");
            Files.writeString(file,DEFINITION+"isVisible=false\n");
            game.toggleLevelMenu(); check(!hasTest(game), "Skrytá vlastní úroveň se při otevření nenačte");
            game.toggleLevelMenu(); Files.writeString(file,DEFINITION+"isVisible=true\n");
            game.toggleLevelMenu(); check(hasTest(game), "Změna na true se projeví při otevření nabídky bez F4");
            Level selected=game.levels.levels().getLast(); game.activateLevel(selected);
            Files.writeString(file,DEFINITION+"isVisible=false\n"); game.toggleLevelMenu();
            check(!hasTest(game) && game.world.level.equals(selected), "Skrytí odstraní nabídku a zachová rozehraný stav");
            check(game.nextLevel()==null, "Skrytá úroveň se nenabízí v postupu");
            game.toggleLevelMenu(); Files.writeString(file,DEFINITION+"isVisible=true\n"); game.toggleLevelMenu();
            check(hasTest(game), "Skrytou úroveň lze znovu zobrazit");
            game.toggleLevelMenu(); Files.delete(file); game.toggleLevelMenu();
            check(!hasTest(game), "Odstranění definice se projeví při otevření nabídky");

            var list=LevelCatalog.class.getDeclaredField("levels"); list.setAccessible(true); list.set(game.levels,List.of());
            game.levelSelection=0; game.moveLevelSelection(1); game.moveLevelSelection(-1);
            var key=Game.class.getDeclaredMethod("key",int.class,int.class); key.setAccessible(true);
            key.invoke(game,GLFW.GLFW_KEY_ENTER,0);
            game.click(new java.awt.geom.Point2D.Double(game.layout.levelActivate.getCenterX(),game.layout.levelActivate.getCenterY()),0);
            check(game.levelMenu && game.levelSelection==0 && game.nextLevel()==null, "Prázdný seznam bezpečně zvládne šipky, Enter i kliknutí");
            var image=new java.awt.image.BufferedImage(1366,768,java.awt.image.BufferedImage.TYPE_INT_RGB);
            game.layout=new UiLayout(1366,768,1);
            var graphics=image.createGraphics(); try { game.dashboard.draw(graphics,game); } finally { graphics.dispose(); }
            javax.imageio.ImageIO.write(image,"png",Path.of("target/empty-level-menu.png").toFile());
            game.reloadLevels(); check(!game.levels.levels().isEmpty(), "Obnovení po prázdném seznamu nespadne");
            var production=new LevelCatalog(Path.of("levels"));
            check(production.levels().stream().noneMatch(l -> l.id().equals("03-workshop")), "Malá dílna se nenačítá z neaktivního příkladu");
            check(production.errors().isEmpty(), "Všechny aktuální konfigurace jsou platné: "+production.errors());
            System.out.println("PASS: "+checks+" visibility checks (configuration, menu refresh, hide/show/delete, empty menu, preview).");
        } finally {
            var compiler=Game.class.getDeclaredField("compiler"); compiler.setAccessible(true);
            ((java.util.concurrent.ExecutorService)compiler.get(game)).shutdownNow();
            game.textRenderer.close();
            try(var files=Files.walk(directory)) { for(var file:files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(file); }
        }
    }
    private static boolean hasTest(Game game) { return game.levels.levels().stream().anyMatch(l -> l.id().equals("zz-visibility-test")); }
    private static Level read(String properties) throws Exception { return Level.read(new java.io.StringReader(DEFINITION+properties),"test"); }
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); checks++; }
}
