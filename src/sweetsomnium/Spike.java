package sweetsomnium;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/** A candy-cone spike. Touching one costs a life. Can stand on the floor or hang from a ceiling. */
public class Spike {

    private final int x;
    private final int y;
    private final boolean hanging;
    private final BufferedImage sprite;
    private final Rectangle hitbox;

    /** Fills tile (col, row). Standing spikes sit on the tile below, hanging ones on the tile above. */
    public Spike(int col, int row, boolean hanging, BufferedImage sprite) {
        this.x = col * Level.TILE;
        this.y = row * Level.TILE;
        this.hanging = hanging;
        this.sprite = sprite;
        // The cone is the middle-bottom of its 48x48 sprite; only its wide lower part hurts,
        // so brushing the very tip is forgiven.
        this.hitbox = hanging
                ? new Rectangle(x + 18, y, 13, 22)
                : new Rectangle(x + 18, y + Level.TILE - 22, 13, 22);
    }

    public Rectangle bounds() {
        return hitbox;
    }

    public void draw(Graphics2D g) {
        if (hanging) {
            g.drawImage(sprite, x, y + Level.TILE, Level.TILE, -Level.TILE, null); // upside down
        } else {
            g.drawImage(sprite, x, y, null);
        }
    }
}
