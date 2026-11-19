package sweetsomnium;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.Map;

/** Draws tiny pixel patterns written as strings, e.g. the HUD hearts and the cloud faces. */
final class PixelArt {

    private PixelArt() { }

    /**
     * @param rows    one string per pixel row; '.' (or any char missing from the palette) is transparent
     * @param palette which color each character stands for
     * @param scale   size of one pattern pixel on screen
     */
    static void draw(Graphics2D g, String[] rows, Map<Character, Color> palette, int x, int y, int scale) {
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            for (int c = 0; c < row.length(); c++) {
                Color color = palette.get(row.charAt(c));
                if (color != null) {
                    g.setColor(color);
                    g.fillRect(x + c * scale, y + r * scale, scale, scale);
                }
            }
        }
    }
}
