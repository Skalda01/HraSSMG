package org.example;

import java.nio.file.Files;
import org.lwjgl.opengl.GL;
import static org.lwjgl.glfw.GLFW.*;

/** Vrácení úprav přes skutečnou obsluhu hry a klávesy QWERTY/QWERTZ. */
public final class EditorHistoryChecks {
    private static int checks;
    private static final int CTRL=GLFW_MOD_CONTROL;

    public static void main(String[] args) throws Exception {
        var directory=Files.createTempDirectory("hrassmg-editor-history-");
        Game game=new Game(directory.resolve("levels"),directory.resolve("programs"),directory.resolve("first.txt"));
        var key=Game.class.getDeclaredMethod("key",int.class,int.class); key.setAccessible(true);
        try {
            game.tab=Game.EditorTab.JAVA;
            game.editor.setText("move(Direction.RIGHT);");
            game.editor.move(game.editor.text.length(),false);
            int caret=game.editor.caret;
            game.editor.insert("\npick();\n");
            String edited=game.editor.text;
            key.invoke(game,Game.shortcutKey(GLFW_KEY_Z,CTRL,"z"),CTRL);
            check(game.editor.text.equals("move(Direction.RIGHT);") && game.editor.caret==caret,"QWERTY Ctrl+Z vrátí vložený blok i kurzor");
            key.invoke(game,Game.shortcutKey(GLFW_KEY_Y,CTRL,"y"),CTRL);
            check(game.editor.text.equals(edited),"QWERTY Ctrl+Y obnoví blok");
            key.invoke(game,Game.shortcutKey(GLFW_KEY_Y,CTRL,"z"),CTRL);
            check(game.editor.text.equals("move(Direction.RIGHT);"),"České Ctrl+Z na fyzické klávese Y provede undo");
            key.invoke(game,Game.shortcutKey(GLFW_KEY_Z,CTRL,"y"),CTRL);
            check(game.editor.text.equals(edited),"České Ctrl+Y na fyzické klávese Z provede redo");
            key.invoke(game,GLFW_KEY_Z,CTRL);
            key.invoke(game,Game.shortcutKey(GLFW_KEY_Y,CTRL|GLFW_MOD_SHIFT,"Z"),CTRL|GLFW_MOD_SHIFT);
            check(game.editor.text.equals(edited),"Ctrl+Shift+Z obnoví změnu i při českém rozložení");

            game.editor.setText("abc"); game.editor.anchor=1; game.editor.caret=3;
            key.invoke(game,GLFW_KEY_DELETE,0);
            check(game.editor.text.equals("a"),"Smazání označeného textu");
            key.invoke(game,GLFW_KEY_Z,CTRL);
            check(game.editor.text.equals("abc") && game.editor.selection().equals("bc"),"Undo obnoví smazaný text i výběr");
            key.invoke(game,GLFW_KEY_Y,CTRL);
            key.invoke(game,GLFW_KEY_Z,CTRL);
            game.editor.insert("X");
            key.invoke(game,GLFW_KEY_Y,CTRL);
            check(game.editor.text.equals("aX"),"Nová úprava zruší starou větev redo");

            game.editor.setText("mo"); game.editor.move(2,false);
            game.completion.refresh(game.editor,true,java.util.Set.of());
            check(game.completion.accept(game.editor),"Vložení návrhu pohybu");
            key.invoke(game,GLFW_KEY_Z,CTRL);
            check(game.editor.text.equals("mo"),"Ctrl+Z vrátí celé doplnění najednou");

            game.editor.setText("text"); game.saved=true; game.activeLine=7;
            key.invoke(game,GLFW_KEY_Z,CTRL); key.invoke(game,GLFW_KEY_Y,CTRL);
            check(game.saved && game.activeLine==7 && game.editor.text.equals("text"),"Prázdná historie nezmění stav programu");
            game.editor.move(4,false); game.editor.insert("!");
            game.running=true; key.invoke(game,GLFW_KEY_Z,CTRL);
            check(game.running && game.editor.text.equals("text!"),"Běžící program se zkratkou nemění");
            game.running=false;
            key.invoke(game,GLFW_KEY_Z,CTRL|GLFW_MOD_ALT);
            check(game.editor.text.equals("text!"),"AltGr nevyvolá undo");
            key.invoke(game,GLFW_KEY_Z,CTRL);
            check(game.editor.text.equals("text"),"Po pozastavení funguje Ctrl+Z");
            check(Game.shortcutKey(GLFW_KEY_Y,0,"z")==GLFW_KEY_Y && Game.shortcutKey(GLFW_KEY_Z,CTRL,null)==GLFW_KEY_Z,
                    "Běžné psaní a chybějící název klávesy mají původní chování");

            var init=Game.class.getDeclaredMethod("init",boolean.class); init.setAccessible(true); init.invoke(game,false);
            long window=glfwGetCurrentContext();
            var callback=glfwSetKeyCallback(window,null);
            if(callback==null) throw new AssertionError("Chybí callback klávesnice");
            try {
                game.editor.setText("before"); game.editor.move(6,false); game.editor.insert(" after");
                int undoKey="z".equalsIgnoreCase(glfwGetKeyName(GLFW_KEY_Y,0)) ? GLFW_KEY_Y : GLFW_KEY_Z;
                callback.invoke(window,undoKey,glfwGetKeyScancode(undoKey),GLFW_PRESS,CTRL);
                check(game.editor.text.equals("before"),"Skutečný GLFW callback zpracuje Ctrl+Z v aktivním rozložení");
                callback.invoke(window,undoKey,glfwGetKeyScancode(undoKey),GLFW_PRESS,CTRL|GLFW_MOD_SHIFT);
                check(game.editor.text.equals("before after"),"Skutečný GLFW callback zpracuje Ctrl+Shift+Z");
            } finally { glfwSetKeyCallback(window,callback); }
            System.out.println("PASS: "+checks+" editor history checks (Ctrl+Z, redo, Czech/English layout, selection, completion, no-op, native callback).");
        } finally {
            var close=Game.class.getDeclaredMethod("closeSession"); close.setAccessible(true); close.invoke(game);
            var compiler=Game.class.getDeclaredField("compiler"); compiler.setAccessible(true);
            ((java.util.concurrent.ExecutorService)compiler.get(game)).shutdownNow();
            game.textRenderer.close();
            long window=glfwGetCurrentContext();
            if(window!=0) { org.lwjgl.glfw.Callbacks.glfwFreeCallbacks(window); glfwDestroyWindow(window); }
            GL.setCapabilities(null); glfwTerminate();
            try(var paths=Files.walk(directory)) { for(var path:paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
        }
    }
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); checks++; }
}
