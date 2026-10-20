package org.example;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.concurrent.ArrayBlockingQueue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import robot.api.Robot;

/** Překlad a spuštění hráčova Java programu mimo vlákno okna. */
final class RobotProgram {
    record Command(String name, int a, int b, int line, String data) {
        Command(String name, int a, int b, int line) { this(name, a, b, line, null); }
        boolean query() { return name.equals("tileType") || name.equals("shelfType") || name.equals("cargoType") || name.equals("orderRemaining")
                || name.equals("robotCoordinatesX") || name.equals("robotCoordinatesY"); }
    }
    static final int MAX_COMMANDS = 20000;
    private static final java.util.Map<String, byte[]> API_CLASSES = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<String> allowedKeywords;
    private final java.util.Set<String> requiredKeywords;
    private final List<Integer> sortingArray;
    private final boolean searching;
    private final boolean sandbox;
    RobotProgram() { this(java.util.Set.of()); }
    RobotProgram(java.util.Set<String> allowedKeywords) { this(allowedKeywords, List.of()); }
    RobotProgram(java.util.Set<String> allowedKeywords, List<Integer> sortingArray) {
        this(allowedKeywords, sortingArray, false);
    }
    RobotProgram(java.util.Set<String> allowedKeywords, List<Integer> sortingArray, boolean searching) {
        this(allowedKeywords, sortingArray, searching, java.util.Set.of());
    }
    RobotProgram(java.util.Set<String> allowedKeywords, List<Integer> sortingArray, boolean searching, java.util.Set<String> requiredKeywords) {
        this(allowedKeywords, sortingArray, searching, requiredKeywords, false);
    }
    RobotProgram(java.util.Set<String> allowedKeywords, List<Integer> sortingArray, boolean searching, java.util.Set<String> requiredKeywords, boolean sandbox) {
        this.allowedKeywords = java.util.Set.copyOf(allowedKeywords); this.sortingArray = List.copyOf(sortingArray); this.searching = searching;
        this.requiredKeywords = java.util.Set.copyOf(requiredKeywords);
        this.sandbox = sandbox;
    }
    private Path prepare(String code) throws Exception {
        ProgramRules.check(code, allowedKeywords, requiredKeywords);
        Path dir = Files.createTempDirectory("hrassmg-program-");
        try {
            Path source = dir.resolve("Main.java");
            Files.writeString(source, code, StandardCharsets.UTF_8);
            for (Class<?> api : List.of(Robot.class, Robot.Direction.class, Robot.TileType.class, Robot.ShelfType.class,
                    sort.runtime.ArrayTrace.class, sort.runtime.ArrayTrace.Slot.class)) {
                String resource = "/" + api.getName().replace('.', '/') + ".class";
                Path destination = dir.resolve(resource.substring(1));
                Files.createDirectories(destination.getParent());
                byte[] cached = API_CLASSES.get(resource);
                if (cached == null) {
                    try (var bytes = api.getResourceAsStream(resource)) {
                        if (bytes == null) throw new IOException("Chybí třída API: " + resource);
                        cached = bytes.readAllBytes(); API_CLASSES.putIfAbsent(resource, cached);
                    }
                }
                Files.write(destination, cached);
            }
            run(List.of(tool("javac"), "-J-Dfile.encoding=UTF-8", "-J-Dstdout.encoding=UTF-8", "-J-Dstderr.encoding=UTF-8", "-encoding", "UTF-8", "-proc:none", "-d",
                    dir.toString(), "-cp", dir.toString(), source.toString()), dir.resolve("compiler.txt"), 12);
            if (sandbox || !sortingArray.isEmpty()) {
                Files.writeString(source, JavaArrayTracing.instrument(code, searching, sandbox), StandardCharsets.UTF_8);
                run(List.of(tool("javac"), "-J-Dfile.encoding=UTF-8", "-J-Dstdout.encoding=UTF-8", "-J-Dstderr.encoding=UTF-8", "-encoding", "UTF-8", "-proc:none", "-d",
                        dir.toString(), "-cp", dir.toString(), source.toString()), dir.resolve("compiler.txt"), 12);
            }
            return dir;
        } catch (Exception e) { cleanup(dir); throw e; }
    }

    Session start(String code) throws Exception {
        Path dir = prepare(code);
        try {
            Process process = new ProcessBuilder(tool("java"), "-Drobot.live=true", "-Dsort.input=" + sortingArray.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")),
                    "-Dsort.sandbox=" + sandbox,
                    "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                    "-Xmx64m", "-XX:ActiveProcessorCount=1", "-cp", dir.toString(), mainClass(dir)).start();
            return new Session(dir, process, sandbox);
        } catch (Exception e) { cleanup(dir); throw e; }
    }

    List<Command> compile(String code) throws Exception {
        Path dir = prepare(code);
        try {
            Path output = dir.resolve("program.txt");
            run(List.of(tool("java"), "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-Xmx64m", "-XX:ActiveProcessorCount=1", "-cp",
                    dir.toString(), mainClass(dir)), output, 3);
            List<Command> commands = new ArrayList<>();
            try (var lines = Files.lines(output, StandardCharsets.UTF_8)) {
                var iterator = lines.iterator();
                while (iterator.hasNext()) {
                    String line = iterator.next();
                    if (!line.startsWith("ROBOT|")) continue;
                    String[] parts = line.split("\\|");
                    if (parts.length != 5) throw new IllegalArgumentException("Neplatný výstup programu.");
                    Command command = new Command(parts[1], Integer.parseInt(parts[2]),
                            Integer.parseInt(parts[3]), Integer.parseInt(parts[4]));
                    if (!List.of("left", "right", "top", "down", "pick", "drop").contains(command.name())) {
                        throw new IllegalArgumentException("Neznámý příkaz: " + command.name());
                    }
                    commands.add(command);
                    if (commands.size() > MAX_COMMANDS) throw new IllegalArgumentException("Maximum je 2000 příkazů.");
                }
            }
            if (commands.isEmpty()) throw new IllegalArgumentException("Program neobsahuje žádné příkazy robota.");
            return List.copyOf(commands);
        } finally {
            cleanup(dir);
        }
    }

    /** Jediný nepotvrzený příkaz drží Java program v synchronizaci s animací skladu. */
    static final class Session implements AutoCloseable {
        private final Path directory;
        private final Process process;
        private final PrintWriter replies;
        private final ArrayBlockingQueue<Command> queue = new ArrayBlockingQueue<>(1);
        private final StringBuilder errors = new StringBuilder();
        private final StringBuilder console = new StringBuilder();
        private volatile String failure;
        private volatile boolean waiting, stdoutDone, stderrDone, closed;
        private volatile long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        private long order = 1;
        private int requests;
        private final boolean sandbox;

        Session(Path directory, Process process) {
            this(directory, process, false);
        }
        Session(Path directory, Process process, boolean sandbox) {
            this.sandbox = sandbox;
            this.directory = directory; this.process = process;
            replies = new PrintWriter(process.getOutputStream(), false, StandardCharsets.UTF_8);
            Thread output = new Thread(() -> read(false), "robot-output"); output.setDaemon(true); output.start();
            Thread error = new Thread(() -> read(true), "robot-errors"); error.setDaemon(true); error.start();
        }

        private void read(boolean error) {
            try (var reader = new InputStreamReader(error ? process.getErrorStream() : process.getInputStream(), StandardCharsets.UTF_8)) {
                StringBuilder line = new StringBuilder();
                int count = 0, ch;
                while ((ch = reader.read()) != -1 && !closed) {
                    if (error) {
                        if (++count > 256_000) throw new IOException("Program vytváří příliš mnoho chybového výstupu.");
                        synchronized (errors) { errors.append((char) ch); }
                    } else if (ch == '\n') {
                        count += receiveLine(line.toString(), true); line.setLength(0);
                        if (count > 256_000) throw new IOException("Program vytváří příliš mnoho výstupu.");
                    } else {
                        if (line.length() >= 8192) throw new IOException("Řádek výstupu programu je příliš dlouhý.");
                        line.append((char) ch);
                    }
                }
                if (!error && line.length() > 0 && !closed && count + receiveLine(line.toString(), false) > 256_000)
                    throw new IOException("Program vytváří příliš mnoho výstupu.");
            } catch (Exception e) { if (!closed) failure = e.getMessage(); }
            finally { if (error) stderrDone = true; else stdoutDone = true; }
        }

        private int receiveLine(String value, boolean newline) throws IOException {
            if (newline && value.endsWith("\r")) value = value.substring(0,value.length()-1);
            int protocol = value.indexOf("ROBOT|");
            String text = protocol >= 0 ? value.substring(0,protocol) : value + (newline ? "\n" : "");
            synchronized (console) { console.append(text); }
            // System.out.print může před příkazem zanechat část rozpracovaného řádku.
            if (protocol >= 0) receive(value.substring(protocol));
            return text.length();
        }

        String output() { synchronized (console) { return console.toString(); } }

        private void receive(String value) throws IOException {
            String[] parts = value.split("[|]");
            if (parts.length != 5 || !List.of("left", "right", "top", "down", "pick", "drop", "tileType", "shelfType", "cargoType", "orderRemaining", "robotCoordinatesX", "robotCoordinatesY", "sortAttach", "sortValue", "sortSetValue", "sortSnapshot", "sortMissing", "sortResult", "sandboxArray", "sandboxBlock").contains(parts[1]))
                throw new IOException("Neplatný příkaz programu.");
            Command command = parts[1].equals("sandboxBlock")
                    ? new Command(parts[1], Integer.parseInt(parts[2]), 0, Integer.parseInt(parts[3]), parts[4])
                    : new Command(parts[1], Integer.parseInt(parts[2]), Integer.parseInt(parts[3]), Integer.parseInt(parts[4]));
            if (waiting) throw new IOException("Program odeslal příkaz bez čekání na hru.");
            waiting = true;
            if (!queue.offer(command)) throw new IOException("Fronta příkazů je plná.");
        }

        void check() {
            if (closed) return;
            if (failure != null) throw new IllegalStateException(failure);
            if (stdoutDone && stderrDone && !process.isAlive() && process.exitValue() != 0) {
                String detail;
                synchronized (errors) { detail = errors.toString(); }
                if (detail.isBlank()) detail = "Java program skončil s chybou " + process.exitValue();
                detail = detail.replace(directory.toString(), "").replaceAll("Main\\.java:(\\d+)", "editor:$1");
                throw new IllegalStateException(detail.substring(0, Math.min(2400, detail.length())));
            }
            if (!waiting && process.isAlive() && System.nanoTime() > deadline)
                throw new IllegalStateException("Program překročil časový limit 3 sekund mezi příkazy. Zkontroluj cykly.");
        }

        Command poll() {
            check();
            Command command = queue.poll();
            if (command != null && !sandbox && ++requests > MAX_COMMANDS) throw new IllegalStateException("Maximum je 2000 příkazů na zakázku.");
            return command;
        }

        void acknowledge(int x, int y, long nextOrder) {
            if (closed) return;
            if (nextOrder != order) { order = nextOrder; requests = 0; }
            deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            waiting = false;
            replies.println("ACK|" + x + "|" + y + "|" + nextOrder);
            replies.flush();
            if (replies.checkError()) throw new IllegalStateException("Spojení s Java programem bylo ukončeno.");
        }

        boolean finished() { check(); return stdoutDone && stderrDone && !process.isAlive() && queue.isEmpty() && !waiting; }

        @Override public synchronized void close() {
            if (closed) return;
            closed = true;
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            Thread cleanupThread = new Thread(() -> {
                try { process.waitFor(2, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                replies.close(); cleanup(directory);
            }, "robot-cleanup");
            cleanupThread.setDaemon(true); cleanupThread.start();
        }
    }

    private static void cleanup(Path dir) {
        // Windows může krátce držet soubor i po ukončení procesu. Úklid nesmí přepsat původní chybu.
        for (int attempt = 0; attempt < 10; attempt++) {
            try (var paths = Files.walk(dir)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
                return;
            } catch (IOException e) {
                try { Thread.sleep(50); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
            }
        }
    }

    /** Přeložená cesta respektuje i package, komentáře a Unicode zápis v hráčově zdroji. */
    private static String mainClass(Path directory) throws IOException {
        try (var files = Files.walk(directory)) {
            Path entry = files.filter(path -> path.getFileName().toString().equals("Main.class"))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Program musí obsahovat třídu Main."));
            String relative = directory.relativize(entry).toString();
            return relative.substring(0, relative.length() - ".class".length())
                    .replace('\\', '.').replace('/', '.');
        }
    }

    private String tool(String name) throws IOException {
        Path path = Path.of(System.getProperty("java.home"), "bin", name + ".exe");
        if (!Files.isRegularFile(path)) path = Path.of(System.getProperty("java.home"), "bin", name);
        if (!Files.isRegularFile(path)) throw new IOException("Chybí " + name + ". Spusť hru pomocí plného JDK 21 nebo novějšího.");
        return path.toString();
    }

    private void run(List<String> args, Path output, int seconds) throws Exception {
        Process process = new ProcessBuilder(args).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        try {
            while (!process.waitFor(40, TimeUnit.MILLISECONDS)) {
                if (System.nanoTime() > deadline) throw new IllegalArgumentException("Program překročil časový limit. Zkontroluj cykly.");
                if (Files.exists(output) && Files.size(output) > 256_000) throw new IllegalArgumentException("Program vytváří příliš mnoho výstupu.");
            }
            if (Files.size(output) > 256_000) throw new IllegalArgumentException("Program vytváří příliš mnoho výstupu.");
            if (process.exitValue() != 0) {
                String error = Files.readString(output, StandardCharsets.UTF_8).replace(output.getParent().toString(), "");
                error = error.lines().filter(line -> !line.startsWith("ROBOT|")).collect(java.util.stream.Collectors.joining("\n"));
                // Hráčův zdroj se překládá beze změn, čísla řádků jsou totožná s editorem.
                var diagnostic = java.util.regex.Pattern.compile("Main\\.java:(\\d+)").matcher(error);
                StringBuilder mapped = new StringBuilder();
                while (diagnostic.find()) diagnostic.appendReplacement(mapped,
                        "editor:" + diagnostic.group(1));
                diagnostic.appendTail(mapped);
                error = mapped.toString();
                throw new IllegalArgumentException(error.substring(0, Math.min(error.length(), 2400)));
            }
        } finally {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            process.waitFor(2, TimeUnit.SECONDS);
        }
    }
}
