package org.example;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Standardní knihovny JDK v programu hráče, v trasování pole i v editoru. */
public final class RandomMathChecks {
    private static int checks;
    private static final String SOURCE = """
            import java.util.Random;
            import static java.lang.Math.*;
            public class Main {
                public static void main(String[] args) {
                    Random nahoda = new Random(42L);
                    System.out.println(nahoda.nextInt(10));
                    nahoda.setSeed(42L);
                    System.out.println(nahoda.nextInt(1, 7));
                    System.out.println(abs(-7));
                    System.out.println(Math.min(3, 8));
                    System.out.println(Math.max(3, 8));
                    System.out.println(Math.clamp(15, 0, 10));
                    System.out.println(Math.sqrt(9));
                    System.out.println(Math.pow(2, 3));
                    System.out.println(Math.round(2.6));
                    System.out.println(Math.floor(-2.1));
                    System.out.println(Math.ceil(2.1));
                    System.out.println(Math.hypot(3, 4));
                    System.out.println(Math.sin(Math.toRadians(90)));
                    System.out.println(Math.toDegrees(Math.PI));
                    System.out.println(Math.random() >= 0 && Math.random() < 1);
                    System.out.println(nahoda.nextDouble() >= 0 && nahoda.nextDouble() < 1);
                }
            }
            """;

    public static void main(String[] args) throws Exception {
        check(complete("Ma","Math").text.equals("Math"), "Návrh třídy Math");
        var automatic = new CodeEditor(); automatic.setText("Ma"); automatic.move(2,false);
        var suggestions = new CodeCompletion(); suggestions.refresh(automatic,false); suggestions.accept(automatic);
        check(automatic.text.equals("Math"), "Velké Ma přednostně doplní Math a malé ma ponechá main");
        check(complete("Rand","Random").text.equals("java.util.Random"), "Random se vloží bez nutnosti importu");
        check(complete("newrand","newrandom").text.equals("java.util.Random nahoda = new java.util.Random();"), "Šablona generátoru");
        var editor=complete("Math.sq","sqrt");
        check(editor.text.equals("Math.sqrt(4.0)") && editor.selection().equals("4.0"), "Matematická metoda jako výraz s parametrem");
        check(complete("java.lang.Math.P","PI").text.equals("java.lang.Math.PI"), "Konstanta nemá závorky");
        check(complete("Math.max(3, 9)","max",8).text.equals("Math.max(3, 9)"), "Doplnění názvu zachová existující argumenty");
        for(String declaration:List.of("Random nahoda = new Random();", "java.util.Random nahoda = new java.util.Random(42L);", "var nahoda = new Random();", "var nahoda = new java.util.Random();")) {
            var e=complete(declaration+"\nint x = nahoda.nextI","nextInt");
            check(e.text.endsWith("nahoda.nextInt(10)") && e.selection().equals("10"), "Metody generátoru pro "+declaration);
        }
        check(complete("Random r = new Random(); r.setS","setSeed").text.endsWith("r.setSeed(42L);"), "Nastavení seedu");
        for(String prefix:List.of("Math.","Random r = new Random(); r.")) {
            var e=new CodeEditor(); e.setText(prefix); e.move(prefix.length(),false);
            var completion=new CodeCompletion(); completion.refresh(e,false,Set.of("move"),true);
            check(completion.isOpen() && completion.items().stream().noneMatch(i -> Level.API_KEYWORDS.contains(i.name())), "Knihovní metody fungují v omezené i algoritmické lekci");
        }
        try(var paths=Files.list(Path.of("src/main/resources/levels"))) {
            for(var path:paths.filter(p -> p.toString().endsWith(".properties")).toList()) {
                try(var reader=Files.newBufferedReader(path)) {
                    var level=Level.read(reader,"test"); ProgramRules.check(SOURCE,level.allowedKeywords()); checks++;
                }
            }
        }
        String output=run(SOURCE, new RobotProgram(Set.of("move")),null);
        check(output.equals("0\n3\n7\n3\n8\n10\n3.0\n8.0\n3\n-3.0\n3.0\n5.0\n1.0\n180.0\ntrue\ntrue\n"), "Skutečný JDK Random a Math v omezeném hráčově programu");

        var random=new java.util.Random(42L);
        var values=new java.util.ArrayList<Integer>(); for(int i=0;i<6;i++) values.add(Math.max(2,random.nextInt(1,13)));
        Level level=Level.read(new java.io.StringReader("id=random-array\nname=Random array\nmode=array\narray=0,0,0,0,0,0\ngoal.array="+values.toString().replaceAll("[\\[\\] ]", "")),"test");
        String arraySource="""
                import java.util.Random;
                public class Main {
                    public static void main(String[] args) {
                        int[] pole = new int[6];
                        Random nahoda = new Random(42L);
                        for(int i=0;i<pole.length;i++) {
                            pole[i]=Math.max(2,nahoda.nextInt(1,13));
                            System.out.println(pole[i]);
                        }
                    }
                }
                """;
        var state=new SortState(); state.reset(level);
        String tracedOutput=run(arraySource,new RobotProgram(Set.of(),level.array()),state); state.finish();
        check(state.complete && state.reads==6 && state.writes==6, "Náhodné hodnoty a Math se průběžně přenesou do canvasu");
        check(tracedOutput.equals(values.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining("\n"))+"\n"), "Canvas a konzole obsahují stejné skutečné náhodné hodnoty");
        try(var reader=Files.newBufferedReader(Path.of("src/main/resources/documentation/11-random-math.properties"))) {
            var chapter=DocumentationChapter.read(reader,"random-math");
            var examples=java.util.regex.Pattern.compile("```java\\n(.*?)\\n```",java.util.regex.Pattern.DOTALL).matcher(chapter.content());
            int count=0;
            while(examples.find()) { run(examples.group(1),new RobotProgram(),null); count++; }
            check(count==2,"Oba úplné příklady dokumentace se přeloží a spustí");
        }
        System.out.println("PASS: "+checks+" Random/Math checks (completion, restrictions, real JDK runtime, seed, math, live array tracing, documentation examples).");
    }
    private static CodeEditor complete(String text,String name) { return complete(text,name,text.length()); }
    private static CodeEditor complete(String text,String name,int caret) {
        var e=new CodeEditor(); e.setText(text); e.move(caret,false);
        var c=new CodeCompletion(); c.refresh(e,true);
        int index=java.util.stream.IntStream.range(0,c.items().size()).filter(i->c.items().get(i).name().equals(name)).findFirst().orElseThrow(()->new AssertionError(text+" -> "+c.items()));
        c.move(index); if(!c.accept(e)) throw new AssertionError(text); return e;
    }
    private static String run(String source,RobotProgram program,SortState state) throws Exception {
        try(var session=program.start(source)) {
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);
            while(!session.finished()) {
                var command=session.poll();
                if(command!=null) {
                    if(state==null) throw new AssertionError("Neočekávaný příkaz: "+command);
                    state.execute(command); state.advance(1,1); session.acknowledge(0,0,1);
                } else Thread.sleep(1);
                if(System.nanoTime()>deadline) throw new AssertionError("Timeout");
            }
            return session.output();
        }
    }
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); checks++; }
}
