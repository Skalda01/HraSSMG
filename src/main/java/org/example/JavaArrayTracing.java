package org.example;

import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

/** Přidá sledování int[] pouze do kopie pro běh; editor i původní čísla řádků zachová. */
final class JavaArrayTracing {
    private static final String TRACE = "sort.runtime.ArrayTrace.";
    private final String source;
    private final CompilationUnitTree unit;
    private final Trees trees;
    private final boolean searching;
    private final boolean sandbox;
    private int temporaryIndex;
    private record Edit(int start, int end, String value) {}

    static String instrument(String source) throws Exception {
        return instrument(source, false);
    }
    static String instrument(String source, boolean searching) throws Exception {
        return instrument(source, searching, false);
    }
    static String instrument(String source, boolean searching, boolean sandbox) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("Je potřeba plné JDK.");
        try (var files = compiler.getStandardFileManager(null, null, java.nio.charset.StandardCharsets.UTF_8)) {
            var input = new SimpleJavaFileObject(URI.create("string:///Main.java"), JavaFileObject.Kind.SOURCE) {
                @Override public CharSequence getCharContent(boolean ignoreErrors) { return source; }
            };
            var task = (JavacTask) compiler.getTask(null, files, diagnostic -> {}, List.of("-proc:none"), null, List.of(input));
            var unit = task.parse().iterator().next();
            task.analyze();
            return new JavaArrayTracing(source, unit, Trees.instance(task), searching, sandbox).render(unit);
        }
    }

    private JavaArrayTracing(String source, CompilationUnitTree unit, Trees trees, boolean searching, boolean sandbox) {
        this.source = source; this.unit = unit; this.trees = trees; this.searching = searching;
        this.sandbox = sandbox;
    }
    private int start(Tree tree) { return (int) trees.getSourcePositions().getStartPosition(unit, tree); }
    private int end(Tree tree) { return (int) trees.getSourcePositions().getEndPosition(unit, tree); }
    private int line(Tree tree) { return (int) unit.getLineMap().getLineNumber(start(tree)); }
    private boolean intArray(Tree tree) {
        var type = trees.getTypeMirror(TreePath.getPath(unit, tree));
        return type instanceof ArrayType array && array.getComponentType().getKind() == TypeKind.INT;
    }
    private boolean intAccess(ExpressionTree tree) { return tree instanceof ArrayAccessTree access && intArray(access.getExpression()); }
    private int observedArrayArgument(MethodInvocationTree call) {
        var element = trees.getElement(TreePath.getPath(unit, call.getMethodSelect()));
        if (!(element instanceof javax.lang.model.element.ExecutableElement method)
                || !(method.getEnclosingElement() instanceof javax.lang.model.element.TypeElement owner)) return -1;
        int index = owner.getQualifiedName().contentEquals("java.util.Arrays") ? 0
                : owner.getQualifiedName().contentEquals("java.lang.System") && method.getSimpleName().contentEquals("arraycopy") ? 2 : -1;
        return index >= 0 && index < call.getArguments().size() && intArray(call.getArguments().get(index)) ? index : -1;
    }
    private String temporary(String kind) {
        String name;
        do { name = "$hrassmg" + kind + temporaryIndex++; } while (source.contains(name));
        return name;
    }
    private String enhancedLoop(EnhancedForLoopTree loop, String label) {
        String array = temporary("Array"), index = temporary("Index");
        String expression = render(loop.getExpression());
        String variable = source.substring(start(loop.getVariable()), end(loop.getVariable()));
        String prefix = "{int[] " + array + "=" + expression + ";" + label
                + "for(int " + index + "=0;" + index + "<" + array + ".length;" + index + "++){"
                + variable + "=" + TRACE + "read(" + array + "," + index + "," + line(loop) + ");";
        long headerLines = source.substring(start(loop), start(loop.getStatement())).chars().filter(c -> c == '\n').count();
        long prefixLines = prefix.chars().filter(c -> c == '\n').count();
        return prefix + "\n".repeat((int) Math.max(0, headerLines - prefixLines)) + render(loop.getStatement()) + "}}";
    }
    private boolean resultVariable(Tree tree) {
        if (!searching) return false;
        var element = trees.getElement(TreePath.getPath(unit, tree));
        if (element == null || element.getKind() != javax.lang.model.element.ElementKind.LOCAL_VARIABLE
                || !element.getSimpleName().contentEquals("vysledek") || element.asType().getKind() != TypeKind.INT) return false;
        var owner = element.getEnclosingElement();
        return owner.getSimpleName().contentEquals("main") && owner.getEnclosingElement().getSimpleName().contentEquals("Main");
    }

    private String render(Tree root) { return render(root, false); }
    private String render(Tree root, boolean skipRoot) {
        int from = root == unit ? 0 : start(root), to = root == unit ? source.length() : end(root);
        List<Edit> edits = new ArrayList<>();
        new TreePathScanner<Void, Void>() {
            private void replace(Tree tree, String replacement) {
                long missingLines = source.substring(start(tree), end(tree)).chars().filter(c -> c == '\n').count()
                        - replacement.chars().filter(c -> c == '\n').count();
                if (missingLines > 0) replacement += "\n".repeat((int) missingLines);
                edits.add(new Edit(start(tree), end(tree), replacement));
            }
            @Override public Void visitVariable(VariableTree tree, Void unused) {
                if (tree.getInitializer() != null && intArray(tree)) {
                    ExpressionTree value = tree.getInitializer();
                    String expression = render(value);
                    if (value instanceof NewArrayTree array && array.getType() == null) expression = "new int[]" + expression;
                    replace(value, TRACE + "register(" + expression + "," + line(tree) + ")");
                    return null;
                }
                if (tree.getInitializer() != null && resultVariable(tree)) {
                    replace(tree.getInitializer(), TRACE + "result(" + render(tree.getInitializer()) + "," + line(tree) + ")");
                    return null;
                }
                return super.visitVariable(tree, unused);
            }
            @Override public Void visitEnhancedForLoop(EnhancedForLoopTree tree, Void unused) {
                if (intArray(tree.getExpression())) {
                    replace(tree, enhancedLoop(tree, ""));
                    return null;
                }
                return super.visitEnhancedForLoop(tree, unused);
            }
            @Override public Void visitLabeledStatement(LabeledStatementTree tree, Void unused) {
                if (tree.getStatement() instanceof EnhancedForLoopTree loop && intArray(loop.getExpression())) {
                    replace(tree, enhancedLoop(loop, tree.getLabel() + ":"));
                    return null;
                }
                return super.visitLabeledStatement(tree, unused);
            }
            @Override public Void visitMethod(MethodTree tree, Void unused) {
                if (tree.getBody() != null && tree.getName().contentEquals("main")
                        && tree.getModifiers().getFlags().contains(Modifier.STATIC) && tree.getParameters().size() == 1
                        && java.util.Set.of("String[]", "java.lang.String[]").contains(tree.getParameters().getFirst().getType().toString())
                        && getCurrentPath().getParentPath().getLeaf() instanceof ClassTree owner && owner.getSimpleName().contentEquals("Main")) {
                    String body = render(tree.getBody());
                    replace(tree.getBody(), "{try{" + body.substring(1, body.length() - 1)
                            + "}finally{" + TRACE + "finish(" + line(tree) + ");}}");
                    return null;
                }
                return super.visitMethod(tree, unused);
            }
            @Override public Void visitAssignment(AssignmentTree tree, Void unused) {
                if (sandbox && intArray(tree.getVariable())) {
                    replace(tree.getExpression(), TRACE + "register(" + render(tree.getExpression()) + "," + line(tree) + ")");
                    return null;
                }
                if (resultVariable(tree.getVariable())) {
                    replace(tree.getExpression(), TRACE + "result(" + render(tree.getExpression()) + "," + line(tree) + ")");
                    return null;
                }
                if (intAccess(tree.getVariable())) {
                    ArrayAccessTree array = (ArrayAccessTree) tree.getVariable();
                    replace(tree, TRACE + "write(" + render(array.getExpression()) + "," + render(array.getIndex())
                            + "," + render(tree.getExpression()) + "," + line(tree) + ")");
                    return null;
                }
                return super.visitAssignment(tree, unused);
            }
            @Override public Void visitExpressionStatement(ExpressionStatementTree tree, Void unused) {
                if (sandbox && !(skipRoot && tree == root) && tree.getExpression() instanceof MethodInvocationTree call
                        && call.getArguments().stream().anyMatch(JavaArrayTracing.this::intArray)) {
                    replace(tree, "{" + render(tree, true) + TRACE + "sync(" + line(tree) + ");}");
                    return null;
                }
                return super.visitExpressionStatement(tree, unused);
            }
            @Override public Void visitMethodInvocation(MethodInvocationTree tree, Void unused) {
                int observed = sandbox ? observedArrayArgument(tree) : -1;
                if (observed >= 0) {
                    var args = new ArrayList<String>();
                    for (int i = 0; i < tree.getArguments().size(); i++) {
                        String value = render(tree.getArguments().get(i));
                        args.add(i == observed ? TRACE + "register(" + value + "," + line(tree) + ")" : value);
                    }
                    replace(tree, render(tree.getMethodSelect()) + "(" + String.join(",", args) + ")");
                    return null;
                }
                return super.visitMethodInvocation(tree, unused);
            }
            @Override public Void visitCompoundAssignment(CompoundAssignmentTree tree, Void unused) {
                if (!(skipRoot && tree == root) && resultVariable(tree.getVariable())) {
                    replace(tree, TRACE + "result(" + render(tree, true) + "," + line(tree) + ")");
                    return null;
                }
                if (intAccess(tree.getVariable())) {
                    ArrayAccessTree array = (ArrayAccessTree) tree.getVariable();
                    int operation = switch (tree.getKind()) {
                        case PLUS_ASSIGNMENT -> 0; case MINUS_ASSIGNMENT -> 1; case MULTIPLY_ASSIGNMENT -> 2;
                        case DIVIDE_ASSIGNMENT -> 3; case REMAINDER_ASSIGNMENT -> 4; case AND_ASSIGNMENT -> 5;
                        case OR_ASSIGNMENT -> 6; case XOR_ASSIGNMENT -> 7; case LEFT_SHIFT_ASSIGNMENT -> 8;
                        case RIGHT_SHIFT_ASSIGNMENT -> 9; case UNSIGNED_RIGHT_SHIFT_ASSIGNMENT -> 10;
                        default -> throw new IllegalArgumentException("Neznámé přiřazení");
                    };
                    replace(tree, TRACE + "slot(" + render(array.getExpression()) + "," + render(array.getIndex())
                            + "," + line(tree) + ").apply(" + operation + "," + render(tree.getExpression()) + ")");
                    return null;
                }
                return super.visitCompoundAssignment(tree, unused);
            }
            @Override public Void visitUnary(UnaryTree tree, Void unused) {
                if (!(skipRoot && tree == root) && resultVariable(tree.getExpression()) && switch (tree.getKind()) {
                    case PREFIX_INCREMENT, POSTFIX_INCREMENT, PREFIX_DECREMENT, POSTFIX_DECREMENT -> true;
                    default -> false;
                }) {
                    replace(tree, TRACE + "resultChange(" + render(tree, true) + "," + render(tree.getExpression()) + "," + line(tree) + ")");
                    return null;
                }
                if (intAccess(tree.getExpression()) && switch (tree.getKind()) {
                    case PREFIX_INCREMENT, POSTFIX_INCREMENT, PREFIX_DECREMENT, POSTFIX_DECREMENT -> true;
                    default -> false;
                }) {
                    ArrayAccessTree array = (ArrayAccessTree) tree.getExpression();
                    boolean increment = tree.getKind() == Tree.Kind.PREFIX_INCREMENT || tree.getKind() == Tree.Kind.POSTFIX_INCREMENT;
                    boolean post = tree.getKind() == Tree.Kind.POSTFIX_INCREMENT || tree.getKind() == Tree.Kind.POSTFIX_DECREMENT;
                    replace(tree, TRACE + "increment(" + render(array.getExpression()) + "," + render(array.getIndex())
                            + "," + (increment ? 1 : -1) + "," + post + "," + line(tree) + ")");
                    return null;
                }
                return super.visitUnary(tree, unused);
            }
            @Override public Void visitArrayAccess(ArrayAccessTree tree, Void unused) {
                if (intAccess(tree)) {
                    replace(tree, TRACE + "read(" + render(tree.getExpression()) + "," + render(tree.getIndex()) + "," + line(tree) + ")");
                    return null;
                }
                return super.visitArrayAccess(tree, unused);
            }
        }.scan(TreePath.getPath(unit, root), null);
        StringBuilder result = new StringBuilder(source.substring(from, to));
        edits.sort(Comparator.comparingInt(Edit::start).reversed());
        for (Edit edit : edits) result.replace(edit.start() - from, edit.end() - from, edit.value());
        return result.toString();
    }
}
