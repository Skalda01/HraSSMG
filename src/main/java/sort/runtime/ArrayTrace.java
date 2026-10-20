package sort.runtime;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Interní propojení obyčejného Java pole s canvasem, v hráčově zdroji se nevyskytuje. */
public final class ArrayTrace {
    private ArrayTrace() {}
    private static final BufferedReader replies = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    private static final int[] input = Arrays.stream(System.getProperty("sort.input", "").split(","))
            .filter(value -> !value.isEmpty()).mapToInt(Integer::parseInt).toArray();
    private static int[] tracked;
    private static int commands;
    private static final boolean sandbox = Boolean.getBoolean("sort.sandbox");
    private static final java.util.Map<int[], Integer> arrays = new java.util.WeakHashMap<>();
    private static int arrayId;

    private static synchronized void emit(String name, int a, int b, int line) {
        if (!sandbox && ++commands > 20_000) throw new IllegalStateException("Maximum je 20000 operací nad polem.");
        exchange("ROBOT|" + name + "|" + a + "|" + b + "|" + line);
    }
    private static synchronized void exchange(String message) {
        System.out.println(message);
        System.out.flush();
        try {
            String response = replies.readLine();
            if (response == null || !response.startsWith("ACK|")) throw new IllegalStateException("Spojení s hrou bylo ukončeno.");
        } catch (IOException e) { throw new IllegalStateException("Spojení s hrou selhalo.", e); }
    }
    public static int[] register(int[] array, int line) {
        if (sandbox) {
            if (array != null && array != tracked) {
                tracked = array;
                int id = arrays.computeIfAbsent(array, ignored -> ++arrayId);
                emit("sandboxArray", array.length, id, line);
                sync(line);
            }
            return array;
        }
        if (tracked == null && array != null && input.length > 0 && Arrays.equals(array, input)) {
            tracked = array;
            emit("sortAttach", array.length, 0, line);
        }
        return array;
    }
    public static void sync(int line) {
        if (!sandbox || tracked == null) return;
        for (int start = 0; start < tracked.length; start += 512) {
            StringBuilder block = new StringBuilder("ROBOT|sandboxBlock|").append(start).append('|').append(line).append('|');
            int end = Math.min(tracked.length, start + 512);
            for (int i = start; i < end; i++) { if (i > start) block.append(','); block.append(tracked[i]); }
            exchange(block.toString());
        }
    }
    public static int read(int[] array, int index, int line) {
        int value = array[index];
        register(array, line);
        if (array == tracked) emit("sortValue", index, value, line);
        return value;
    }
    public static int write(int[] array, int index, int value, int line) {
        register(array, line);
        array[index] = value;
        if (array == tracked) emit("sortSetValue", index, value, line);
        return value;
    }
    public static int increment(int[] array, int index, int delta, boolean post, int line) {
        int old = read(array, index, line);
        int value = write(array, index, old + delta, line);
        return post ? old : value;
    }
    public static Slot slot(int[] array, int index, int line) { return new Slot(array, index, line); }
    public static int result(int value, int line) { emit("sortResult", value, 0, line); return value; }
    public static int resultChange(int expressionValue, int currentValue, int line) {
        result(currentValue, line); return expressionValue;
    }
    public static final class Slot {
        private final int[] array;
        private final int index, line, old;
        private Slot(int[] array, int index, int line) {
            this.array = array; this.index = index; this.line = line; old = read(array, index, line);
        }
        public int apply(int op, int rhs) {
            return write(array, index, switch (op) {
                case 0 -> old + rhs; case 1 -> old - rhs; case 2 -> old * rhs; case 3 -> old / rhs; case 4 -> old % rhs;
                case 5 -> old & rhs; case 6 -> old | rhs; case 7 -> old ^ rhs; case 8 -> old << rhs; case 9 -> old >> rhs;
                case 10 -> old >>> rhs; default -> throw new IllegalArgumentException();
            }, line);
        }
        public int apply(int op, long rhs) {
            return write(array, index, (int) switch (op) {
                case 0 -> old + rhs; case 1 -> old - rhs; case 2 -> old * rhs; case 3 -> old / rhs; case 4 -> old % rhs;
                case 5 -> old & rhs; case 6 -> old | rhs; case 7 -> old ^ rhs; case 8 -> old << rhs; case 9 -> old >> rhs;
                case 10 -> old >>> rhs; default -> throw new IllegalArgumentException();
            }, line);
        }
        public int apply(int op, float rhs) {
            return write(array, index, (int) switch (op) {
                case 0 -> old + rhs; case 1 -> old - rhs; case 2 -> old * rhs; case 3 -> old / rhs; case 4 -> old % rhs;
                default -> throw new IllegalArgumentException();
            }, line);
        }
        public int apply(int op, double rhs) {
            return write(array, index, (int) switch (op) {
                case 0 -> old + rhs; case 1 -> old - rhs; case 2 -> old * rhs; case 3 -> old / rhs; case 4 -> old % rhs;
                default -> throw new IllegalArgumentException();
            }, line);
        }
    }
    public static void finish(int line) {
        if (sandbox) { sync(line); return; }
        if (tracked == null) { emit("sortMissing", 0, 0, line); return; }
        for (int i = 0; i < tracked.length; i++) emit("sortSnapshot", i, tracked[i], line);
    }
}
