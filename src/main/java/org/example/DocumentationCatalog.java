package org.example;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

final class DocumentationCatalog {
    private List<DocumentationChapter> chapters = List.of();
    private List<String> errors = List.of();
    DocumentationCatalog() { reload(); }
    List<DocumentationChapter> chapters() { return chapters; }
    List<String> errors() { return errors; }
    void reload() {
        var loaded = new LinkedHashMap<String, DocumentationChapter>();
        var failures = new ArrayList<String>();
        for (var entry : ResourceConfigs.read("documentation", failures)) {
            try {
                var chapter = DocumentationChapter.read(new StringReader(entry.text()), entry.name().substring(0, entry.name().length() - 11));
                if (loaded.containsKey(chapter.id())) throw new IllegalArgumentException("Duplicitní id kapitoly: " + chapter.id());
                loaded.put(chapter.id(), chapter);
            } catch (Exception e) { failures.add(entry.name() + ": " + e.getMessage()); }
        }
        chapters = loaded.values().stream().sorted(Comparator.comparingInt(DocumentationChapter::order).thenComparing(DocumentationChapter::id)).toList();
        errors = List.copyOf(failures);
    }
}
