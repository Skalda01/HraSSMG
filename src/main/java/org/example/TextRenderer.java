package org.example;

import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTTPackContext;
import org.lwjgl.stb.STBTTPackedchar;
import org.lwjgl.system.MemoryStack;

import java.awt.*;
import java.awt.geom.Point2D;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.stb.STBTruetype.*;
import static org.lwjgl.system.MemoryUtil.*;

/** Stejný STB coverage postup jako v GameEngineOPENGL, doplněný o ořezávání GUI a pevné buňky editoru. */
final class TextRenderer implements AutoCloseable {
    private static final int[][] RANGES = {{32, 560}, {0x2000, 64}, {0x2190, 16}, {0x2212, 1}, {0x221e, 1}};
    private static final int GLYPHS = 642;
    private static final float SUPERSAMPLE = 2;
    private static final int ATLAS_DIMENSION = 2048;
    private static final int[] ATLAS_PIXEL_HEIGHTS = {8, 10, 12, 14, 16, 18, 20, 24, 28, 32, 40, 48};
    private static final int FLOATS_PER_VERTEX = 8;
    private final FontBook fonts;
    private final Map<FontBook.Source, NativeFont> nativeFonts = new IdentityHashMap<>();
    private final LinkedHashMap<Key, Atlas> atlases = new LinkedHashMap<>(32, 0.75f, true);
    private final List<Run> runs = new ArrayList<>();
    private final List<Batch> batches = new ArrayList<>();
    private int width, height, program, vao, vbo, screenUniform;
    private boolean gpu;
    private FloatBuffer upload;
    private float[] vertices = new float[16_384];
    private int floats;
    private Color vertexColor;

    TextRenderer() { this(new FontBook()); }
    TextRenderer(FontBook fonts) { this.fonts = fonts; }
    Font font(FontBook.Face face, int size) { return fonts.font(face, size); }
    boolean isGpu() { return gpu; }

    void create() {
        program = shaderProgram();
        screenUniform = glGetUniformLocation(program, "screenSize");
        glUseProgram(program); glUniform1i(glGetUniformLocation(program, "fontTexture"), 0);
        vao = glGenVertexArrays(); vbo = glGenBuffers();
        glBindVertexArray(vao); glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, FLOATS_PER_VERTEX * Float.BYTES, 0L);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, FLOATS_PER_VERTEX * Float.BYTES, 2L * Float.BYTES);
        glVertexAttribPointer(2, 4, GL_FLOAT, false, FLOATS_PER_VERTEX * Float.BYTES, 4L * Float.BYTES);
        glEnableVertexAttribArray(0); glEnableVertexAttribArray(1);
        glEnableVertexAttribArray(2);
        gpu = true;
    }

    void beginFrame(int width, int height) { this.width = width; this.height = height; runs.clear(); }

    double measure(Graphics2D g, FontBook.Face face, int size, String value) {
        if (!gpu) {
            Font font = fonts.font(face, size);
            return face == FontBook.Face.CODE ? value.codePointCount(0, value.length()) * font.getStringBounds("M", g.getFontRenderContext()).getWidth()
                    : font.getStringBounds(value, g.getFontRenderContext()).getWidth();
        }
        double scale = Math.abs(g.getTransform().getScaleY());
        int pixels = Math.max(8, (int) Math.round(size * scale));
        NativeFont base = nativeFont(fonts.source(face));
        double cell = base.advance('M') * base.scale(pixels), result = 0;
        for (int offset = 0; offset < value.length();) {
            int cp = value.codePointAt(offset); offset += Character.charCount(cp);
            NativeFont font = supported(base, cp);
            result += face == FontBook.Face.CODE ? cell : font.advance(safeCodepoint(font, cp)) * font.scale(pixels);
        }
        return result / scale;
    }

    void draw(Graphics2D g, FontBook.Face face, int size, String text, double x, double baseline, Color color) {
        if (text.isEmpty()) return;
        if (!gpu) {
            g.setFont(fonts.font(face, size)); g.setColor(color);
            if (face != FontBook.Face.CODE) g.drawString(text, (float) x, (float) baseline);
            else {
                double cell = measure(g, face, size, "M");
                for (int offset = 0; offset < text.length();) {
                    int cp = text.codePointAt(offset); offset += Character.charCount(cp);
                    String glyph = new String(Character.toChars(cp));
                    double glyphWidth = g.getFont().getStringBounds(glyph, g.getFontRenderContext()).getWidth();
                    g.drawString(glyph, (float) (x + (cell - glyphWidth) / 2), (float) baseline); x += cell;
                }
            }
            return;
        }
        var transform = g.getTransform();
        Point2D point = transform.transform(new Point2D.Double(x, baseline), null);
        int pixels = Math.max(8, (int) Math.round(size * Math.abs(transform.getScaleY())));
        Rectangle clip = g.getClip() == null ? new Rectangle(0, 0, width, height)
                : transform.createTransformedShape(g.getClip()).getBounds().intersection(new Rectangle(0, 0, width, height));
        if (clip.isEmpty()) return;
        double physicalWidth = measure(g, face, size, text) * Math.abs(transform.getScaleX());
        Rectangle bounds = new Rectangle((int) Math.floor(point.getX()) - 2, (int) Math.floor(point.getY()) - pixels * 2,
                (int) Math.ceil(physicalWidth) + 4, pixels * 3);
        runs.add(new Run(text, face, pixels, (float) Math.round(point.getX()), (float) Math.round(point.getY()), color,
                bounds, new ArrayList<>(List.of(clip))));
    }

    /** Později kreslené panely (zejména našeptávač) zakryjí dříve zaznamenaný text. */
    void occlude(Graphics2D g, Rectangle rectangle) {
        if (!gpu || runs.isEmpty()) return;
        Rectangle cover = g.getTransform().createTransformedShape(rectangle).getBounds();
        for (Run run : runs) {
            if (!run.bounds.intersects(cover)) continue;
            List<Rectangle> pieces = new ArrayList<>();
            for (Rectangle clip : run.clips) {
                Rectangle overlap = clip.intersection(cover);
                if (overlap.isEmpty()) { pieces.add(clip); continue; }
                add(pieces, clip.x, clip.y, clip.width, overlap.y - clip.y);
                add(pieces, clip.x, overlap.y + overlap.height, clip.width, clip.y + clip.height - overlap.y - overlap.height);
                add(pieces, clip.x, overlap.y, overlap.x - clip.x, overlap.height);
                add(pieces, overlap.x + overlap.width, overlap.y, clip.x + clip.width - overlap.x - overlap.width, overlap.height);
            }
            run.clips.clear(); run.clips.addAll(pieces);
        }
    }

    private static void add(List<Rectangle> target, int x, int y, int w, int h) { if (w > 0 && h > 0) target.add(new Rectangle(x, y, w, h)); }

    void prepare() {
        if (!gpu) return;
        batches.clear(); floats = 0;
        HashSet<Key> active = new HashSet<>();
        for (Run run : runs) {
            if (run.clips.isEmpty()) continue;
            NativeFont base = nativeFont(fonts.source(run.face));
            vertexColor = run.color;
            float pen = run.x;
            float cell = base.advance('M') * base.scale(run.size);
            for (int offset = 0; offset < run.text.length();) {
                int cp = run.text.codePointAt(offset); offset += Character.charCount(cp);
                NativeFont font = supported(base, cp); cp = safeCodepoint(font, cp);
                Key key = new Key(font, atlasSize(run.size)); active.add(key);
                Atlas atlas = atlases.computeIfAbsent(key, this::atlas);
                STBTTPackedchar glyph = atlas.characters.get(index(cp));
                float scale = run.size / (key.size * SUPERSAMPLE);
                float advance = glyph.xadvance() * scale;
                float origin = pen + (run.face == FontBook.Face.CODE ? (cell - advance) / 2 : 0);
                if (glyph.x1() > glyph.x0() && glyph.y1() > glyph.y0()) {
                    int first = floats / FLOATS_PER_VERTEX;
                    quad(origin + glyph.xoff() * scale, run.y + glyph.yoff() * scale,
                            origin + glyph.xoff2() * scale, run.y + glyph.yoff2() * scale,
                            glyph.x0() / (float) atlas.dimension, glyph.y0() / (float) atlas.dimension,
                            glyph.x1() / (float) atlas.dimension, glyph.y1() / (float) atlas.dimension);
                    // Sousední glyphy stejného runu sdílejí jediný draw call.
                    Batch last = batches.isEmpty() ? null : batches.getLast();
                    if (last != null && last.texture == atlas.texture && last.clips == run.clips && last.color.equals(run.color)) last.count += 6;
                    else batches.add(new Batch(atlas.texture, first, 6, run.color, run.clips));
                }
                pen += run.face == FontBook.Face.CODE ? cell : advance;
            }
        }
        if (upload == null || upload.capacity() < floats) { if (upload != null) memFree(upload); upload = memAllocFloat(Math.max(4096, floats)); }
        upload.clear(); upload.put(vertices, 0, floats).flip();
        glBindBuffer(GL_ARRAY_BUFFER, vbo); glBufferData(GL_ARRAY_BUFFER, upload, GL_DYNAMIC_DRAW);
        var iterator = atlases.entrySet().iterator();
        while (atlases.size() > 64 && iterator.hasNext()) {
            var entry = iterator.next();
            if (!active.contains(entry.getKey())) { entry.getValue().close(); iterator.remove(); }
        }
    }

    void render(int width, int height) {
        if (!gpu || batches.isEmpty()) return;
        glUseProgram(program); glUniform2f(screenUniform, width, height);
        glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_SCISSOR_TEST); glActiveTexture(GL_TEXTURE0); glBindVertexArray(vao);
        for (Batch batch : batches) {
            glBindTexture(GL_TEXTURE_2D, batch.texture);
            for (Rectangle clip : batch.clips) {
                glScissor(clip.x, height - clip.y - clip.height, clip.width, clip.height);
                glDrawArrays(GL_TRIANGLES, batch.first, batch.count);
            }
        }
        glDisable(GL_SCISSOR_TEST); glDisable(GL_BLEND);
    }

    private Atlas atlas(Key key) {
        for (int dimension = ATLAS_DIMENSION; dimension <= ATLAS_DIMENSION; dimension *= 2) {
            ByteBuffer bitmap = memAlloc(dimension * dimension);
            STBTTPackedchar.Buffer characters = STBTTPackedchar.malloc(GLYPHS);
            boolean success = false;
            try (STBTTPackContext pack = STBTTPackContext.malloc()) {
                if (stbtt_PackBegin(pack, bitmap, dimension, dimension, 0, 1)) {
                    try {
                        success = true; int offset = 0;
                        for (int[] range : RANGES) {
                            characters.position(offset).limit(offset + range[1]);
                            // Zachovej původní em velikost rozhraní; 2× atlas se při kreslení zmenší.
                            success &= stbtt_PackFontRange(pack, key.font.data, 0, -key.size * SUPERSAMPLE, range[0], characters);
                            offset += range[1];
                        }
                    } finally { stbtt_PackEnd(pack); }
                }
                characters.clear();
                if (success) {
                    int texture = glGenTextures(); glBindTexture(GL_TEXTURE_2D, texture);
                    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                    glTexImage2D(GL_TEXTURE_2D, 0, GL_R8, dimension, dimension, 0, GL_RED, GL_UNSIGNED_BYTE, bitmap);
                    glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
                    return new Atlas(texture, dimension, characters);
                }
            } finally { memFree(bitmap); if (!success) characters.free(); }
        }
        throw new IllegalStateException("Font se nevejde do atlasu pro velikost " + key.size);
    }

    private NativeFont nativeFont(FontBook.Source source) { return nativeFonts.computeIfAbsent(source, NativeFont::new); }
    private static int atlasSize(int pixels) {
        int selected = ATLAS_PIXEL_HEIGHTS[0];
        for (int size : ATLAS_PIXEL_HEIGHTS) if (Math.abs(pixels - size) < Math.abs(pixels - selected)) selected = size;
        return selected;
    }
    private NativeFont supported(NativeFont font, int cp) {
        return index(cp) >= 0 && stbtt_FindGlyphIndex(font.info, cp) != 0 ? font : nativeFont(fonts.fallback);
    }
    private int safeCodepoint(NativeFont font, int cp) { return index(cp) >= 0 && stbtt_FindGlyphIndex(font.info, cp) != 0 ? cp : '?'; }
    private static int index(int cp) {
        int offset = 0;
        for (int[] range : RANGES) { if (cp >= range[0] && cp < range[0] + range[1]) return offset + cp - range[0]; offset += range[1]; }
        return -1;
    }

    private void quad(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1) {
        if (floats + 6 * FLOATS_PER_VERTEX > vertices.length) vertices = java.util.Arrays.copyOf(vertices, vertices.length * 2);
        vertex(x0,y0,u0,v0); vertex(x1,y0,u1,v0); vertex(x1,y1,u1,v1);
        vertex(x1,y1,u1,v1); vertex(x0,y1,u0,v1); vertex(x0,y0,u0,v0);
    }
    private void vertex(float x, float y, float u, float v) {
        vertices[floats++] = x; vertices[floats++] = y; vertices[floats++] = u; vertices[floats++] = v;
        vertices[floats++] = vertexColor.getRed() / 255f; vertices[floats++] = vertexColor.getGreen() / 255f;
        vertices[floats++] = vertexColor.getBlue() / 255f; vertices[floats++] = vertexColor.getAlpha() / 255f;
    }

    private int shaderProgram() {
        int vertex = shader(GL_VERTEX_SHADER, """
                #version 330 core
                layout(location=0) in vec2 position;
                layout(location=1) in vec2 uv;
                layout(location=2) in vec4 vertexColor;
                uniform vec2 screenSize;
                out vec2 texCoord;
                out vec4 textColor;
                void main() {
                    vec2 p = position / screenSize * 2.0 - 1.0;
                    gl_Position = vec4(p.x, -p.y, 0.0, 1.0);
                    texCoord = uv;
                    textColor = vertexColor;
                }
                """);
        int fragment = shader(GL_FRAGMENT_SHADER, """
                #version 330 core
                in vec2 texCoord;
                uniform sampler2D fontTexture;
                in vec4 textColor;
                out vec4 color;
                void main() { color = vec4(textColor.rgb, textColor.a * texture(fontTexture, texCoord).r); }
                """);
        int program = glCreateProgram(); glAttachShader(program, vertex); glAttachShader(program, fragment); glLinkProgram(program);
        glDeleteShader(vertex); glDeleteShader(fragment);
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) { String error = glGetProgramInfoLog(program); glDeleteProgram(program); throw new IllegalStateException(error); }
        return program;
    }
    private int shader(int type, String source) {
        int shader = glCreateShader(type); glShaderSource(shader, source); glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) { String error = glGetShaderInfoLog(shader); glDeleteShader(shader); throw new IllegalStateException(error); }
        return shader;
    }

    @Override public void close() {
        for (Atlas atlas : atlases.values()) atlas.close(); atlases.clear();
        for (NativeFont font : nativeFonts.values()) font.close(); nativeFonts.clear();
        if (upload != null) { memFree(upload); upload = null; }
        if (vbo != 0) glDeleteBuffers(vbo); if (vao != 0) glDeleteVertexArrays(vao); if (program != 0) glDeleteProgram(program);
        vbo = vao = program = 0; gpu = false;
    }

    private record Key(NativeFont font, int size) {}
    private record Run(String text, FontBook.Face face, int size, float x, float y, Color color, Rectangle bounds, List<Rectangle> clips) {}
    private record Atlas(int texture, int dimension, STBTTPackedchar.Buffer characters) {
        void close() { glDeleteTextures(texture); characters.free(); }
    }
    private static final class Batch {
        final int texture, first; int count; final Color color; final List<Rectangle> clips;
        Batch(int texture, int first, int count, Color color, List<Rectangle> clips) { this.texture=texture; this.first=first; this.count=count; this.color=color; this.clips=clips; }
    }
    private static final class NativeFont implements AutoCloseable {
        final ByteBuffer data; final STBTTFontinfo info; final Map<Integer, Integer> advances = new java.util.HashMap<>();
        NativeFont(FontBook.Source source) {
            data = memAlloc(source.bytes().length); data.put(source.bytes()).flip(); info = STBTTFontinfo.malloc();
            if (!stbtt_InitFont(info, data, stbtt_GetFontOffsetForIndex(data, 0))) { info.free(); memFree(data); throw new IllegalArgumentException("STB nepodporuje font " + source.name()); }
        }
        float scale(int size) { return stbtt_ScaleForMappingEmToPixels(info, size); }
        int advance(int cp) {
            return advances.computeIfAbsent(cp, point -> {
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    var width = stack.ints(0); stbtt_GetCodepointHMetrics(info, point, width, null); return width.get(0);
                }
            });
        }
        @Override public void close() { info.free(); memFree(data); }
    }
}
