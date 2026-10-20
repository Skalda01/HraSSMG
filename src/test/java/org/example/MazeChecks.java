package org.example;

import java.nio.file.Files;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.lwjgl.opengl.GL;
import static org.lwjgl.glfw.GLFW.*;

/** Průchod novým bludištěm přes skutečný hráčův proces a obsluhu hry. */
public final class MazeChecks {
    private static int checks;
    private static final String SOLUTION = """
            for (int usek = 0; usek < 7; usek++) {
                Direction smer = Direction.RIGHT;
                int kroku = 0;
                switch (usek) {
                    case 0 -> { smer = Direction.RIGHT; kroku = 6; }
                    case 1 -> { smer = Direction.DOWN; kroku = 6; }
                    case 2 -> { smer = Direction.LEFT; kroku = 6; }
                    case 3 -> { smer = Direction.TOP; kroku = 4; }
                    case 4 -> { smer = Direction.RIGHT; kroku = 4; }
                    case 5 -> { smer = Direction.DOWN; kroku = 2; }
                    case 6 -> { smer = Direction.LEFT; kroku = 2; }
                }
                for (int krok = 0; krok < kroku; krok++) move(smer);
            }
            """;

    public static void main(String[] args) throws Exception {
        var directory = Files.createTempDirectory("hrassmg-maze-check-");
        Game game = new Game(directory.resolve("levels"), directory.resolve("programs"), directory.resolve("first.txt"));
        try {
            method("init", boolean.class).invoke(game, false);
            Level level = game.levels.levels().stream().filter(l -> l.positionGoal() != null && l.requiredKeywords().contains("switch")).findFirst().orElseThrow();
            check(level.isVisible(), "Bludiště je dostupné ve výběru");
            check(level.dock() == null && level.shelves().isEmpty() && level.totalGoal() == 0, "Pouze průchod, bez doručování");
            check(level.positionGoal().equals(new Level.Cell(2,4)) && level.requiredKeywords().equals(Set.of("switch")), "Cíl a povinný switch");
            verifyCorridor(level);
            var world = new Warehouse(level);
            check(!world.complete(), "Prázdná objednávka neznamená splnění úrovně");
            world.x=2; world.y=4;
            check(!world.complete(), "Před úspěšným koncem programu není splněno");
            world.finishProgram(); check(world.complete(), "Konec programu v cíli");
            world.reset(); check(!world.complete() && !world.programFinished && world.steps == 0, "Reset vrací stav");

            accepts(level, SOLUTION);
            accepts(level, "switch(0) { case 0: break; }");
            rejects(level, "// switch\nString s = \"switch\";", "použij: switch");
            rejects(level, "if(true) move(Direction.RIGHT);", "není povoleno: if");
            rejects(level, "while(true) {}", "není povoleno: while");
            rejects(level, "pick();", "není povoleno: pick");
            rejects(level, "Runnable odeber = robot.api.Robot::pick;", "není povoleno: pick");

            game.activateLevel(level); game.selectTab(Game.EditorTab.ASSIGNMENT);
            preview(game, "target/maze-preview.png");
            run(game, level, SOLUTION);
            check(game.world.complete() && game.world.programFinished && game.world.steps == 30 && game.world.atGoal(), "Skutečný switch projde 30 kroků a dokončí úroveň");
            check(game.status.contains("Robot dorazil do cíle"), "Stav hlásí dosažení cíle místo zásilek");
            run(game, level, "switch(0) { default -> {} }");
            check(!game.world.complete() && game.world.programFinished && game.status.contains("Dojeď do cíle"), "Switch bez pohybu úkol nesplní");
            run(game, level, "switch(0) { default -> move(Direction.DOWN); }");
            check(!game.world.complete() && !game.world.programFinished && game.world.steps == 0, "Průchod zdí končí chybou");
            run(game, level, SOLUTION.replace("kroku = 2; }\n    }", "kroku = 1; }\n    }"));
            check(!game.world.complete() && game.world.programFinished && game.world.steps == 29, "Zastavení před cílem nestačí");

            invalid("goal.position=7,4", "mimo sklad");
            invalid("goal.position=1,1", "není dosažitelný");
            invalid("requiredKeywords=while", "není povolen");
            invalid("requiredKeywords=unknown", "neznámý");
            System.out.println("PASS: " + checks + " maze checks (single corridor, fifth level, Java switch, live Game, target, walls, rules, reset, preview).");
            for (String error : game.levels.errors()) System.out.println("Existing catalog error: " + error);
        } finally {
            method("closeSession").invoke(game);
            var compiler = Game.class.getDeclaredField("compiler"); compiler.setAccessible(true);
            ((java.util.concurrent.ExecutorService) compiler.get(game)).shutdownNow();
            game.textRenderer.close();
            long window=glfwGetCurrentContext();
            if (window != 0) { org.lwjgl.glfw.Callbacks.glfwFreeCallbacks(window); glfwDestroyWindow(window); }
            GL.setCapabilities(null); glfwTerminate();
            try (var files = Files.walk(directory)) {
                for (var path : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static void verifyCorridor(Level level) {
        Set<Level.Cell> visited = new java.util.HashSet<>();
        var queue = new java.util.ArrayDeque<Level.Cell>(); queue.add(level.start());
        while (!queue.isEmpty()) {
            var cell = queue.removeFirst();
            if (!visited.add(cell)) continue;
            var neighbours = new java.util.ArrayList<Level.Cell>();
            for (int[] delta : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                var next = new Level.Cell(cell.x()+delta[0], cell.y()+delta[1]);
                if (next.x()>=0 && next.y()>=0 && next.x()<level.width() && next.y()<level.height() && !level.obstacles().contains(next)) neighbours.add(next);
            }
            check(neighbours.size() == (cell.equals(level.start()) || cell.equals(level.positionGoal()) ? 1 : 2), "Chodba bez odboček na " + cell);
            queue.addAll(neighbours.stream().filter(c -> !visited.contains(c)).toList());
        }
        check(visited.size()==31 && visited.size()==level.width()*level.height()-level.obstacles().size(), "Jediná souvislá cesta dlouhá 30 kroků");
    }
    private static void accepts(Level level, String body) throws Exception {
        ProgramRules.check(CodeEditor.wrapLegacyBody(body), level.allowedKeywords(), level.requiredKeywords()); checks++;
    }
    private static void rejects(Level level, String body, String message) throws Exception {
        try { accepts(level, body); throw new AssertionError("Přijat zakázaný program: " + body); }
        catch (IllegalArgumentException e) { check(e.getMessage().contains(message), e.getMessage()); }
    }
    private static void invalid(String replacement, String message) throws Exception {
        String definition=Files.readString(java.nio.file.Path.of("src/main/resources/levels/05-shift.properties"));
        String key=replacement.substring(0,replacement.indexOf('='));
        definition=definition.replaceAll("(?m)^"+java.util.regex.Pattern.quote(key)+"=.*$", replacement);
        try { Level.read(new java.io.StringReader(definition),"maze"); throw new AssertionError("Přijata vadná konfigurace: "+replacement); }
        catch (IllegalArgumentException e) { check(e.getMessage().contains(message), e.getMessage()); }
    }
    private static void run(Game game, Level level, String body) throws Exception {
        game.activateLevel(level); game.selectTab(Game.EditorTab.JAVA); game.editor.setText(CodeEditor.wrapLegacyBody(body));
        method("play").invoke(game);
        var pending=Game.class.getDeclaredField("pending"); pending.setAccessible(true);
        var session=Game.class.getDeclaredField("session"); session.setAccessible(true);
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
        while (pending.get(game)!=null) { method("update").invoke(game); Thread.sleep(1); if(System.nanoTime()>deadline) throw new AssertionError(game.status); }
        if (session.get(game)==null) throw new AssertionError("Compile: "+game.status);
        while (session.get(game)!=null) { method("execute").invoke(game); Thread.sleep(1); if(System.nanoTime()>deadline) throw new AssertionError(game.status); }
    }
    private static void preview(Game game, String file) throws Exception {
        game.layout=new UiLayout(1366,768,1); game.editorView=true;
        var image=new java.awt.image.BufferedImage(1366,768,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var gpu=TextRenderer.class.getDeclaredField("gpu"); gpu.setAccessible(true);
        boolean previous=gpu.getBoolean(game.textRenderer); gpu.setBoolean(game.textRenderer,false);
        var graphics=image.createGraphics();
        try { game.dashboard.draw(graphics,game); }
        finally { graphics.dispose(); gpu.setBoolean(game.textRenderer,previous); }
        javax.imageio.ImageIO.write(image,"png",java.nio.file.Path.of(file).toFile());
    }
    private static java.lang.reflect.Method method(String name, Class<?>... args) throws Exception { var m=Game.class.getDeclaredMethod(name,args); m.setAccessible(true); return m; }
    private static void check(boolean condition, String message) { if(!condition) throw new AssertionError(message); checks++; }
}
