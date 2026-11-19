package sweetsomnium;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * A thin striped candy platform. It's one-way: you can jump up through it
 * from below and land on top of it.
 */
public class Platform {

    /** The striped bar sits this many pixels down inside its 48px-tall sprite. */
    private static final int BAR_OFFSET_Y = 33;

    private final int x;
    private final int y;
    private final BufferedImage sprite;

    /**
     * @param tileX      left edge, in tiles (can be fractional, e.g. 25.5)
     * @param topRow     the tile row line the platform's top surface sits on
     * @param widthTiles 1 (small sprite) or 2 (long sprite)
     */
    public Platform(double tileX, int topRow, int widthTiles) {
        sprite = switch (widthTiles) {
            case 1 -> Assets.PLATFORM_SMALL;
            case 2 -> Assets.PLATFORM_LONG;
            default -> throw new IllegalArgumentException("Platforms are 1 or 2 tiles wide, not " + widthTiles);
        };
        x = (int) Math.round(tileX * Level.TILE);
        y = topRow * Level.TILE - BAR_OFFSET_Y;
    }

    /** Y of the surface you stand on. */
    public double top() {
        return y + BAR_OFFSET_Y;
    }

    public double left() {
        return x + 1;
    }

    public double right() {
        return x + sprite.getWidth() - 1;
    }

    public void draw(Graphics2D g) {
        g.drawImage(sprite, x, y, null);
    }
}
