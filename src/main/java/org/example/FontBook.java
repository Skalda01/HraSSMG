package org.example;

import java.awt.Font;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/** Jediný přibalený font JetBrains Mono pro celé rozhraní i editor. */
final class FontBook {
    static final String FONT_RESOURCE = "fonts/JetBrainsMono-Regular.ttf";
    enum Face { UI, BOLD, CODE }
    record Source(String name, byte[] bytes, Font font) {}
    private final Map<Float, Font> sizes = new HashMap<>();
    final Source fallback;

    FontBook() {
        try (InputStream input = FontBook.class.getClassLoader().getResourceAsStream(FONT_RESOURCE)) {
            if (input == null) throw new IllegalStateException("Chybí přibalený font " + FONT_RESOURCE);
            byte[] bytes = input.readAllBytes();
            Font font = Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(bytes));
            fallback = new Source(FONT_RESOURCE, bytes, font);
        } catch (Exception e) {
            throw new IllegalStateException("Nelze načíst přibalený JetBrains Mono: " + FONT_RESOURCE, e);
        }

    }

    Source source(Face face) { return fallback; }

    Font font(Face face, float size) {
        return sizes.computeIfAbsent(size, value -> fallback.font().deriveFont(value));
    }
}
