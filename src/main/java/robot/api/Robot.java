package robot.api;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/** Veřejné API hráčova programu. Každý příkaz čeká na dokončení v herním okně. */
public final class Robot {
    public enum Direction { LEFT, RIGHT, TOP, DOWN }
    public enum TileType { FLOOR, SHELF, DOCK, INBOUND }
    public enum ShelfType {
        PARTS("orange", "Součástky"), ELECTRONICS("purple", "Elektronika"),
        TOOLS("tools", "Nástroje"), MATERIALS("materials", "Materiál");
        private final String key, label;
        ShelfType(String key, String label) { this.key = key; this.label = label; }
        public String key() { return key; }
        public String label() { return label; }
        public static ShelfType fromKey(String key) {
            for (ShelfType type : values()) if (type.key.equals(key)) return type;
            throw new IllegalArgumentException("Neznámý typ zásilky: " + key);
        }
    }

    private Robot() {}
    private static final AtomicInteger commands = new AtomicInteger();
    private static final boolean LIVE = Boolean.getBoolean("robot.live");
    private static final BufferedReader replies = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    private static long order = 1;

    private static synchronized int[] emit(String name, int a, int b) {
        if (commands.incrementAndGet() > 2000) throw new IllegalStateException("Příliš mnoho příkazů (maximum 2000 na zakázku).");
        int line = 0;
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            if ("Main.java".equals(frame.getFileName())) { line = frame.getLineNumber(); break; }
        }
        System.out.println("ROBOT|" + name + "|" + a + "|" + b + "|" + line);
        System.out.flush();
        if (!LIVE) return new int[]{0, 0};
        try {
            String response = replies.readLine();
            if (response == null) throw new IllegalStateException("Spojení s hrou bylo ukončeno.");
            String[] fields = response.split("[|]");
            if (fields.length != 4 || !fields[0].equals("ACK")) throw new IllegalStateException("Neplatná odpověď hry.");
            long nextOrder = Long.parseLong(fields[3]);
            if (order != nextOrder) { order = nextOrder; commands.set(0); }
            return new int[]{Integer.parseInt(fields[1]), Integer.parseInt(fields[2])};
        } catch (IOException e) { throw new IllegalStateException("Spojení s hrou selhalo.", e); }
    }

    /** Posun o jedno políčko; hranice a překážky kontroluje hra. */
    public static void move(Direction direction) {
        if (direction == null) throw new IllegalArgumentException("move očekává směr Direction.");
        // Jméno směru je interní zpráva pro herní proces.
        emit(direction.name().toLowerCase(Locale.ROOT), 0, 0);
    }

    /** Vezme jednu zásilku z příjmu nebo aktuálního regálu, pokud robot nic nenese. */
    public static void pick() { emit("pick", 0, 0); }

    /** Naskladní zásilku do odpovídající police nebo odešle již naskladněnou zásilku na docku. */
    public static void drop() { emit("drop", 0, 0); }

    /** Aktuální celočíselná souřadnice X robota; nula je levý sloupec. */
    public static int robotCoordinatesX() {
        requireLive("robotCoordinatesX");
        return emit("robotCoordinatesX", 0, 0)[0];
    }

    /** Aktuální celočíselná souřadnice Y robota; nula je horní řádek. */
    public static int robotCoordinatesY() {
        requireLive("robotCoordinatesY");
        return emit("robotCoordinatesY", 0, 0)[0];
    }

    /** Typ políčka pod robotem. Prázdný regál zůstává políčkem SHELF. */
    public static TileType tileType() {
        requireLive("tileType");
        return TileType.values()[emit("tileType", 0, 0)[0]];
    }

    /** Sortiment aktuálního regálu i po vyčerpání zásoby; mimo regál vrací null. */
    public static ShelfType shelfType() {
        requireLive("shelfType");
        int type = emit("shelfType", 0, 0)[0];
        return type < 0 ? null : ShelfType.values()[type];
    }

    /** Typ právě neseného balíku; bez nákladu vrací null. */
    public static ShelfType cargoType() {
        requireLive("cargoType");
        int type = emit("cargoType", 0, 0)[0];
        return type < 0 ? null : ShelfType.values()[type];
    }

    /** Zbývající počet daného typu v aktuální zakázce. Po dokončení čte novou zakázku. */
    public static int orderRemaining(ShelfType type) {
        requireLive("orderRemaining");
        if (type == null) throw new IllegalArgumentException("orderRemaining očekává typ zboží ShelfType.");
        return emit("orderRemaining", type.ordinal(), 0)[0];
    }

    private static void requireLive(String method) {
        if (!LIVE) throw new IllegalStateException(method + " vyžaduje živé spuštění ve hře.");
    }
}
