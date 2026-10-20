package org.example;

import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/** Pravidla lekce kontrolují Java syntaxi, nikoli slova v komentářích nebo řetězcích. */
final class ProgramRules {
    static void check(String code, Set<String> allowed) throws Exception {
        check(code, allowed, Set.of());
    }
    static void check(String code, Set<String> allowed, Set<String> required) throws Exception {
        if (allowed.isEmpty() && required.isEmpty()) return;
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("Pro kontrolu programu je potřeba plné JDK.");
        try (var files = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            var diagnostics = new DiagnosticCollector<JavaFileObject>();
            var task = (JavacTask) compiler.getTask(null, files, diagnostics, List.of("-proc:none"), null, List.of(new Source(code)));
            var trees = Trees.instance(task);
            var units = task.parse();
            task.analyze();
            Set<String> used = new java.util.HashSet<>();
            for (var unit : units) new Visitor(allowed, trees, unit, used).scan(unit, null);
            var missing = required.stream().filter(keyword -> !used.contains(keyword)).sorted().toList();
            if (!missing.isEmpty()) throw new IllegalArgumentException("editor:1: V této úrovni použij: " + String.join(", ", missing));
            // Běžné chyby syntaxe následně vypíše překladač se stejnými čísly řádků.
        }
    }

    private static final class Source extends SimpleJavaFileObject {
        private final String code;
        Source(String code) { super(URI.create("string:///Main.java"), Kind.SOURCE); this.code = code; }
        @Override public CharSequence getCharContent(boolean ignoreEncodingErrors) { return code; }
    }

    private static final class Visitor extends TreePathScanner<Void, Void> {
        private final Set<String> allowed;
        private final Trees trees;
        private final CompilationUnitTree unit;
        private final Set<String> used;
        Visitor(Set<String> allowed, Trees trees, CompilationUnitTree unit, Set<String> used) {
            this.allowed = allowed; this.trees = trees; this.unit = unit; this.used = used;
        }
        private void require(String name, Tree tree) {
            used.add(name);
            if (Level.permits(allowed, name)) return;
            long position = trees.getSourcePositions().getStartPosition(unit, tree);
            long line = position < 0 ? 1 : unit.getLineMap().getLineNumber(position);
            throw new IllegalArgumentException("editor:" + line + ": V této úrovni není povoleno: " + name);
        }
        @Override public Void scan(Tree tree, Void unused) {
            if (tree == null) return null;
            String keyword = switch (tree.getKind()) {
                case FOR_LOOP, ENHANCED_FOR_LOOP -> "for";
                case WHILE_LOOP -> "while";
                case DO_WHILE_LOOP -> "do";
                case IF -> "if";
                case SWITCH, SWITCH_EXPRESSION -> "switch";
                case BREAK -> "break";
                case CONTINUE -> "continue";
                case RETURN -> "return";
                case TRY -> "try";
                case THROW -> "throw";
                case ASSERT -> "assert";
                case SYNCHRONIZED -> "synchronized";
                default -> null;
            };
            if (keyword != null) require(keyword, tree);
            if (tree.getKind() == Tree.Kind.DO_WHILE_LOOP) require("while", tree);
            return super.scan(tree, unused);
        }
        @Override public Void visitMethodInvocation(MethodInvocationTree tree, Void unused) {
            requireApi(tree.getMethodSelect(), tree);
            return super.visitMethodInvocation(tree, unused);
        }
        private void requireApi(Tree reference, Tree tree) {
            // Stejný název může mít vlastní metoda, přetížení i metoda jiné knihovny.
            // Omezujeme pouze volání, které překladač skutečně přiřadil k Robot API.
            var element = trees.getElement(TreePath.getPath(unit, reference));
            if (!(element instanceof ExecutableElement method)
                    || !(method.getEnclosingElement() instanceof TypeElement owner)
                    || !owner.getQualifiedName().contentEquals("robot.api.Robot")) return;
            String name = Level.canonicalKeyword(method.getSimpleName().toString());
            if (Level.API_KEYWORDS.contains(name)) require(name, tree);
        }
        @Override public Void visitIf(IfTree tree, Void unused) {
            if (tree.getElseStatement() != null) {
                Tree branch = tree.getElseStatement();
                require(branch instanceof IfTree ? "elseif" : "else", branch);
            }
            return super.visitIf(tree, unused);
        }
        @Override public Void visitMemberReference(MemberReferenceTree tree, Void unused) {
            requireApi(tree, tree);
            return super.visitMemberReference(tree, unused);
        }
    }
}
