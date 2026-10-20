package org.example;

import java.io.Reader;
import java.util.Properties;
import java.util.Set;

record DocumentationChapter(String id, String title, String description, int order, String content) {
    static DocumentationChapter read(Reader reader, String defaultId) throws Exception {
        var properties = new Properties(); properties.load(reader);
        var keys = Set.of("id", "title", "description", "order", "content");
        for (String key : properties.stringPropertyNames()) if (!keys.contains(key)) throw new IllegalArgumentException("Neznámá vlastnost: " + key);
        return new DocumentationChapter(properties.getProperty("id", defaultId).strip(), properties.getProperty("title", "").strip(),
                properties.getProperty("description", "").strip(), Integer.parseInt(properties.getProperty("order", "0").strip()),
                properties.getProperty("content", "").replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    "));
    }
    DocumentationChapter {
        if (!id.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}")) throw new IllegalArgumentException("Neplatné id kapitoly");
        if (title.isBlank() || title.length() > 120 || title.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("title: název na jednom řádku, nejvýše 120 znaků");
        if (description.length() > 500 || description.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("description: nejvýše 500 znaků na jednom řádku");
        if (content.length() > 64_000) throw new IllegalArgumentException("content: nejvýše 64000 znaků");
    }
}
