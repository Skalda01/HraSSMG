package org.example;

import java.util.Arrays;

/** Zrcadlo skutečného Java pole, napojené na události běžícího programu. */
final class SortState {
    private int[] values = new int[0];
    private int[] originalSorted = new int[0];
    private int previousValue;
    private boolean attached;
    private boolean[] snapshot;
    private int[] original;
    private boolean searching, resultSeen, arrayPractice;
    private boolean sandbox;
    int arrayId;
    long viewVersion;
    private int[] expectedArray = new int[0];
    boolean writing;
    boolean[] visited;
    int target, searchLow, searchHigh, probe = -1, foundProbe = -1, reportedResult = -1;
    int fromLow, fromHigh;
    int left = -1, right = -1, reads, writes;
    double progress = 1;
    boolean complete;
    String operation = "Spusťte vlastní implementaci pomocí F5.";

    void reset(Level level) {
        values = level.array().stream().mapToInt(Integer::intValue).toArray();
        original = values.clone(); searching = level.searching(); target = level.target();
        arrayPractice = level.arrayPractice(); expectedArray = level.expectedArray().stream().mapToInt(Integer::intValue).toArray(); writing = false;
        sandbox = level.arraySandbox(); arrayId = 0; viewVersion++;
        visited = new boolean[values.length];
        searchLow = fromLow = 0; searchHigh = fromHigh = values.length - 1;
        probe = foundProbe = reportedResult = -1; resultSeen = false;
        originalSorted = values.clone(); Arrays.sort(originalSorted);
        attached = false; snapshot = new boolean[values.length];
        left = right = -1; reads = writes = previousValue = 0; progress = 1; complete = false;
        operation = "Spusťte vlastní implementaci pomocí F5.";
    }
    int length() { return values.length; }
    int value(int index) { checkIndex(index); return values[index]; }
    int maximum() { return Math.max(Math.max(previousValue, Arrays.stream(originalSorted).max().orElse(1)), Arrays.stream(values).max().orElse(1)); }
    boolean sorted() {
        for (int i = 1; i < values.length; i++) if (values[i - 1] > values[i]) return false;
        return values.length > 0;
    }
    boolean animating() { return progress < 1; }
    void advance(double dt, double duration) { progress = Math.min(1, progress + Math.max(0, dt) / duration); }
    double displayValue(int index) {
        double t = progress * progress * (3 - 2 * progress);
        return index == left && animating() ? previousValue + (values[index] - previousValue) * t : value(index);
    }
    int execute(RobotProgram.Command command) {
        if (sandbox && command.name().equals("sandboxArray")) {
            if (command.a() < 0) throw new IllegalArgumentException("Záporná délka pole.");
            values = new int[command.a()]; visited = new boolean[values.length];
            snapshot = new boolean[0]; arrayId = command.b(); attached = true;
            left = right = -1; progress = 1; writing = false; viewVersion++;
            operation = "Zobrazeno pole #" + arrayId + " · délka " + values.length + ".";
            return 0;
        }
        if (sandbox && command.name().equals("sandboxBlock")) {
            String[] block = command.data().split(",");
            if (command.a() < 0 || (long) command.a() + block.length > values.length) throw new IllegalArgumentException("Neplatný blok pole.");
            for (int i = 0; i < block.length; i++) values[command.a() + i] = Integer.parseInt(block[i]);
            return 0;
        }
        if (command.name().equals("sortResult")) {
            resultSeen = true; reportedResult = command.a();
            operation = "Výsledek programu: " + reportedResult; return 0;
        }
        if (command.name().equals("sortAttach")) {
            if (command.a() != length()) throw new IllegalArgumentException("Pole neodpovídá vstupu úrovně.");
            attached = true; return 0;
        }
        if (command.name().equals("sortMissing")) throw new IllegalArgumentException("V programu chybí původní vstupní int[] pole ze zadání.");
        if (!java.util.Set.of("sortValue", "sortSetValue", "sortSnapshot").contains(command.name()))
            throw new IllegalArgumentException("V této úrovni používejte běžné Java pole int[].");
        checkIndex(command.a());
        if (command.name().equals("sortSnapshot")) {
            snapshot[command.a()] = true;
            if (values[command.a()] == command.b()) return 0;
        }
        left = right = command.a();
        viewVersion++;
        writing = !command.name().equals("sortValue");
        progress = 1;
        switch (command.name()) {
            case "sortValue" -> {
                reads++; values[left] = command.b(); operation = "Čtení pole[" + left + "] = " + values[left];
                if (arrayPractice) { previousValue = values[left]; progress = 0; visited[left] = true; }
                if (searching) visited[left] = true;
                if (searching && foundProbe < 0) {
                    fromLow = searchLow; fromHigh = searchHigh; probe = left;
                    if (values[left] == target) { foundProbe = left; searchLow = searchHigh = left; }
                    else if (values[left] < target) searchLow = Math.max(searchLow, left + 1);
                    else searchHigh = Math.min(searchHigh, left - 1);
                    progress = 0;
                    operation = "Kontrola [" + left + "]: " + values[left]
                            + (values[left] == target ? " = " : values[left] < target ? " < " : " > ") + target;
                }
                return values[left];
            }
            case "sortSetValue", "sortSnapshot" -> {
                previousValue = values[left];
                values[left] = command.b();
                operation = "Zápis pole[" + left + "]: " + previousValue + " → " + values[left];
                if (command.name().equals("sortSetValue")) writes++;
                progress = arrayPractice || previousValue != values[left] ? 0 : 1;
                return 0;
            }
            default -> throw new IllegalArgumentException("Neznámá operace pole");
        }
    }
    void finish() {
        if (sandbox) {
            complete = false; progress = 1;
            operation = attached ? "Program skončil. Zobrazeno pole #" + arrayId + " · délka " + length() + "."
                    : "Program skončil. Vytvoř int[] pro zobrazení v canvasu.";
            return;
        }
        boolean verified = attached && snapshot.length > 0;
        for (boolean seen : snapshot) verified &= seen;
        complete = verified && Arrays.equals(values, originalSorted); progress = 1;
        if (arrayPractice) {
            complete = verified && Arrays.equals(values, expectedArray);
            operation = complete ? "Pole odpovídá zadání. Úroveň splněna!"
                    : "Program skončil. Uprav hodnoty pole podle cíle a spusť jej znovu.";
            return;
        }
        if (searching) {
            boolean present = Arrays.stream(original).anyMatch(value -> value == target);
            boolean correct = reportedResult >= 0 && reportedResult < values.length && original[reportedResult] == target && visited[reportedResult];
            if (!present) correct = reportedResult == -1 && reads > 0 && searchLow > searchHigh;
            complete = verified && resultSeen && correct && Arrays.equals(values, original);
            operation = complete ? present ? "Nalezeno na indexu " + reportedResult + ". Úroveň splněna!"
                    : "Hodnota v poli není. Výsledek -1 je správně. Úroveň splněna!"
                    : !Arrays.equals(values, original) ? "Při hledání vstupní pole neměňte."
                    : "Výsledek hledání není správný. Uložte nalezený index do proměnné vysledek.";
            return;
        }
        operation = complete ? "Pole je seřazeno vzestupně. Úroveň splněna!"
                : sorted() ? "Pole musí obsahovat stejné hodnoty jako na začátku. Při výměně zachovejte oba prvky."
                : "Program skončil, ale pole není seřazeno. Upravte algoritmus a spusťte jej znovu.";
    }
    private void checkIndex(int index) {
        if (index < 0 || index >= values.length) throw new IllegalArgumentException("Index " + index
                + " je mimo pole. Platné indexy: 0 až " + (values.length - 1) + ".");
    }
}
