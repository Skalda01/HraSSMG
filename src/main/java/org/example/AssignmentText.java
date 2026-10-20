package org.example;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Malý formát zadání: nadpisy, Java bloky a kód uvnitř vět. */
final class AssignmentText {
    enum Kind { HEADING, TEXT, CODE, SPACE }
    record Block(Kind kind, String text) {}
    record Span(String text, boolean code) {}
    private static final Pattern CODE_LINE = Pattern.compile(
            "\\s*(?:(?:for|while|if|switch)\\s*\\(.*\\)\\s*(?:\\{.*\\}?|;)?"
                    + "|do\\s*\\{.*|}\\s*while\\s*\\(.*\\)\\s*;"
                    + "|[\\w$.]+\\s*\\(.*\\)\\s*;?"
                    + "|(?:byte|short|int|long|float|double|char|boolean|String|var|(?:java\\.util\\.)?(?:ArrayList|List)(?:\\s*<[^;={}]+>)?)(?:\\s*\\[\\s*\\])*\\s+.*;|[{};]+)\\s*");
    private static final Pattern INLINE = Pattern.compile(
            "`([^`\\n]+)`|\\b[\\p{L}_$][\\p{L}\\p{N}_$.]*\\([^()\\n]*(?:\\([^()\\n]*\\)[^()\\n]*)*\\);?");

    static List<Block> blocks(String content) {
        var result = new ArrayList<Block>();
        String[] lines = content.split("\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.stripLeading().startsWith("```")) {
                var code = new ArrayList<String>();
                while (++i < lines.length && !lines[i].strip().equals("```")) code.add(lines[i]);
                result.add(new Block(Kind.CODE, String.join("\n", code)));
            } else if (line.isBlank()) result.add(new Block(Kind.SPACE, ""));
            else if (line.startsWith("# ")) result.add(new Block(Kind.HEADING, line.substring(2)));
            else if (CODE_LINE.matcher(line).matches()) {
                var code = new ArrayList<String>(); code.add(line);
                while (i + 1 < lines.length && CODE_LINE.matcher(lines[i + 1]).matches()) code.add(lines[++i]);
                result.add(new Block(Kind.CODE, String.join("\n", code)));
            } else result.add(new Block(Kind.TEXT, line));
        }
        return List.copyOf(result);
    }

    static List<Span> spans(String text) {
        var result = new ArrayList<Span>();
        var matcher = INLINE.matcher(text);
        int end = 0;
        while (matcher.find()) {
            if (matcher.start() > end) result.add(new Span(text.substring(end, matcher.start()), false));
            result.add(new Span(matcher.group(1) == null ? matcher.group() : matcher.group(1), true));
            end = matcher.end();
        }
        if (end < text.length()) result.add(new Span(text.substring(end), false));
        return List.copyOf(result);
    }
}
