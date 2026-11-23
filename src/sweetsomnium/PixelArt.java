package sweetsomnium;

import java.awt.Color;
import java.awt.Graphics2D;

/** Draws tiny pixel patterns written as strings, e.g. the HUD hearts and the cloud faces. */
final class PixelArt {

    /** One slot per ASCII character, so any pattern character can be used directly as an index. */
    private static final int PALETTE_SIZE = 128;

    private PixelArt() { }

    /**
     * Builds a palette: an array where {@code palette[ch]} is the color for pattern character {@code ch}.
     * Characters that are not given a color stay {@code null}, which means transparent.
     *
     * @param chars  the pattern characters, e.g. "kpw"
     * @param colors one color per character, in the same order
     */
    static Color[] palette(String chars, Color... colors) {
        if (chars.length() != colors.length) {
            throw new IllegalArgumentException("need exactly one color per character");
        }
        Color[] palette = new Color[PALETTE_SIZE];
        for (int i = 0; i < chars.length(); i++) {
            palette[chars.charAt(i)] = colors[i];
        }
        return palette;
    }

    /**
     * @param rows    one string per pixel row; '.' (or any char missing from the palette) is transparent
     * @param palette which color each character stands for, built with {@link #palette}
     * @param scale   size of one pattern pixel on screen
     */
    static void draw(Graphics2D g, String[] rows, Color[] palette, int x, int y, int scale) {
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            for (int c = 0; c < row.length(); c++) {
                char ch = row.charAt(c);
                Color color = ch < palette.length ? palette[ch] : null;
                if (color != null) {
                    g.setColor(color);
                    g.fillRect(x + c * scale, y + r * scale, scale, scale);
                }
            }
        }
    }
}
