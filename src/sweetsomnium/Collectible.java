package sweetsomnium;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/** A candy (or pill) to pick up. Bobs gently, twinkles now and then, and pops when collected. */
public class Collectible {

    private static final int POP_FRAMES = 30;

    private final int x;
    private final int y;
    private final BufferedImage sprite;
    private final Rectangle hitbox;
    private final int phase; // so candies don't all bob in sync
    private boolean collected;
    private int popTimer;
    private int tick;

    /** x, y = top-left of the 48x48 tile the candy sits in. The sprite is centred in that tile. */
    public Collectible(int x, int y, BufferedImage sprite) {
        this.x = x;
        this.y = y;
        this.sprite = sprite;
        this.hitbox = new Rectangle(x + 12, y + 12, 24, 24); // the candy art is the middle of the tile
        this.phase = (x / 7 + y / 3) % 90;
    }

    public void update() {
        tick++;
        if (popTimer > 0) {
            popTimer--;
        }
    }

    /** Collects the candy if the player touches it. Returns true only on the frame it's picked up. */
    public boolean tryCollect(Rectangle player) {
        if (!collected && player.intersects(hitbox)) {
            collected = true;
            popTimer = POP_FRAMES;
            return true;
        }
        return false;
    }

    public void draw(Graphics2D g) {
        int cx = x + 24;
        int cy = y + 24;
        if (!collected) {
            int bob = (int) Math.round(Math.sin((tick + phase) * 0.08) * 3);
            g.drawImage(sprite, x + (Level.TILE - sprite.getWidth()) / 2, y + (Level.TILE - sprite.getHeight()) / 2 + bob, null);
            int t = (tick + phase) % 100;
            if (t < 14) {
                int size = t < 7 ? t / 2 + 1 : (14 - t) / 2 + 1;
                drawSparkle(g, cx + 9, cy - 9 + bob, size, Color.WHITE);
            }
        } else if (popTimer > 0) {
            float p = 1f - popTimer / (float) POP_FRAMES; // 0 -> 1
            int alpha = (int) (255 * (1f - p));
            int r = (int) (8 + p * 22);
            g.setColor(new Color(255, 236, 248, alpha));
            g.drawOval(cx - r, cy - r, r * 2, r * 2);
            Color spark = new Color(255, 255, 255, alpha);
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3 + 0.3;
                double d = 6 + p * 26;
                drawSparkle(g, (int) (cx + Math.cos(a) * d), (int) (cy + Math.sin(a) * d - p * 8), 2, spark);
            }
        }
    }

    /** A little 4-pointed pixel star. */
    static void drawSparkle(Graphics2D g, int cx, int cy, int arm, Color color) {
        g.setColor(color);
        g.fillRect(cx - arm, cy, arm * 2 + 1, 1);
        g.fillRect(cx, cy - arm, 1, arm * 2 + 1);
    }
}
