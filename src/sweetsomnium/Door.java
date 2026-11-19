package sweetsomnium;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/** The exit. Locked (with a candy padlock) until every candy in the level is collected. */
public class Door {

    public static final int WIDTH = 52;
    public static final int HEIGHT = 84;

    private static final Color OUTLINE = Color.BLACK;
    private static final Color FRAME = new Color(0xF2D2F6);
    private static final Color STRIPE = new Color(0xD77BBA);
    private static final Color WOOD = new Color(0x97467D);
    private static final Color WOOD_DARK = new Color(0x7A3565);
    private static final Color LOCK = new Color(0xFFD86B);
    private static final Color LOCK_DARK = new Color(0xC99A3A);
    private static final Color GLOW_CENTER = new Color(0xFFFBE6);
    private static final Color GLOW_EDGE = new Color(0xFFC6E6);

    private final int x;
    private final int y;
    private final Shape outer;
    private final Shape inner;
    private boolean locked = true;
    private int unlockTimer;
    private int tick;

    /** Stands on top of the tile below (col, row). */
    public Door(int col, int row) {
        x = col * Level.TILE + (Level.TILE - WIDTH) / 2;
        y = (row + 1) * Level.TILE - HEIGHT;
        outer = arch(x, y, WIDTH, HEIGHT);
        inner = arch(x + 6, y + 6, WIDTH - 12, HEIGHT - 6);
    }

    public boolean isLocked() {
        return locked;
    }

    public void unlock() {
        if (locked) {
            locked = false;
            unlockTimer = 40;
        }
    }

    /** The doorway area the player has to touch. */
    public Rectangle bounds() {
        return new Rectangle(x + 10, y + 20, WIDTH - 20, HEIGHT - 20);
    }

    public void update() {
        tick++;
        if (unlockTimer > 0) {
            unlockTimer--;
        }
    }

    public void draw(Graphics2D g) {
        // Candy-striped frame
        g.setColor(FRAME);
        g.fill(outer);
        Shape oldClip = g.getClip();
        g.clip(outer);
        g.setColor(STRIPE);
        g.setStroke(new BasicStroke(4));
        for (int i = -HEIGHT; i < WIDTH + HEIGHT; i += 11) {
            g.drawLine(x + i, y + HEIGHT, x + i + HEIGHT, y);
        }
        g.setClip(oldClip);

        int cx = x + WIDTH / 2;
        if (locked) {
            drawClosedDoor(g, cx);
        } else {
            drawOpenDoor(g, cx);
        }

        g.setStroke(new BasicStroke(1));
        g.setColor(OUTLINE);
        g.draw(outer);
        g.draw(inner);

        if (unlockTimer > 0) {
            float p = 1f - unlockTimer / 40f;
            int r = (int) (20 + p * 50);
            g.setColor(new Color(255, 250, 230, (int) (220 * (1f - p))));
            g.setStroke(new BasicStroke(3));
            g.drawOval(cx - r, y + HEIGHT / 2 - r, r * 2, r * 2);
            g.setStroke(new BasicStroke(1));
        }
    }

    private void drawClosedDoor(Graphics2D g, int cx) {
        g.setColor(WOOD);
        g.fill(inner);
        Shape oldClip = g.getClip();
        g.clip(inner);
        g.setColor(WOOD_DARK);
        for (int px = x + 16; px < x + WIDTH - 8; px += 10) {
            g.fillRect(px, y, 1, HEIGHT);
        }
        g.setClip(oldClip);

        // Padlock
        int ly = y + HEIGHT / 2 + 6;
        g.setColor(OUTLINE);
        g.setStroke(new BasicStroke(3));
        g.drawArc(cx - 6, ly - 13, 12, 14, 0, 180);
        g.setStroke(new BasicStroke(1));
        g.fillRect(cx - 9, ly - 6, 19, 15);
        g.setColor(LOCK);
        g.fillRect(cx - 8, ly - 5, 17, 13);
        g.setColor(LOCK_DARK);
        g.fillRect(cx - 8, ly + 5, 17, 3);
        g.setColor(OUTLINE);
        g.fillRect(cx - 1, ly - 2, 3, 3);
        g.fillRect(cx, ly + 1, 1, 4);
    }

    private void drawOpenDoor(Graphics2D g, int cx) {
        float pulse = (float) (0.5 + 0.5 * Math.sin(tick * 0.08));
        Rectangle2D box = inner.getBounds2D();
        g.setPaint(new RadialGradientPaint(
                new Point2D.Double(cx, box.getCenterY() + 6),
                (float) (box.getWidth() * (0.85 + 0.15 * pulse)),
                new float[] {0f, 1f},
                new Color[] {GLOW_CENTER, GLOW_EDGE},
                MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g.fill(inner);

        // The door itself, swung open (seen edge-on)
        g.setColor(OUTLINE);
        g.fillRect(x + 6, y + 22, 7, HEIGHT - 22);
        g.setColor(WOOD);
        g.fillRect(x + 7, y + 23, 5, HEIGHT - 24);

        // Sparkles drifting up out of the doorway
        for (int i = 0; i < 3; i++) {
            int t = (tick + i * 23) % 70;
            int sx = cx - 8 + i * 9;
            int sy = y + HEIGHT - 12 - t;
            int alpha = (int) (255 * (1f - t / 70f));
            Collectible.drawSparkle(g, sx, sy, 2, new Color(255, 255, 255, alpha));
        }
    }

    /** A rectangle with a round top. */
    private static Shape arch(int x, int y, int w, int h) {
        Area a = new Area(new Ellipse2D.Double(x, y, w, w));
        a.add(new Area(new Rectangle2D.Double(x, y + w / 2.0, w, h - w / 2.0)));
        return a;
    }
}
