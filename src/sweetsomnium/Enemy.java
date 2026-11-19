package sweetsomnium;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/**
 * A candy creature. Touching one costs a life.
 * Level 1's enemies are slow and simple: they pace back and forth on whatever
 * they're standing on and turn around at walls and edges.
 */
public class Enemy {

    public enum Type {
        /** Red gummy, paces back and forth. */
        GUMMY(Assets.GUMMY, 0.5, 12, 14, 14, false),
        /** Red gummy that bounces on the spot (for spots too small to walk on). */
        GUMMY_IDLE(Assets.GUMMY, 0.0, 20, 12, 12, false),
        /** Green gummy worm, crawls slowly. */
        WORM(Assets.GUMMY_WORM, 0.35, 16, 24, 14, false);

        final BufferedImage sheet;
        final double speed;
        final int frameTicks;
        final int hitW;
        final int hitH;
        final boolean facesLeft; // which way the art looks on the sheet

        Type(BufferedImage sheet, double speed, int frameTicks, int hitW, int hitH, boolean facesLeft) {
            this.sheet = sheet;
            this.speed = speed;
            this.frameTicks = frameTicks;
            this.hitW = hitW;
            this.hitH = hitH;
            this.facesLeft = facesLeft;
        }
    }

    private static final int FRAME = 32;   // each sheet is two 32x32 frames side by side
    private static final double EPS = 0.001;

    private final Type type;
    private final BufferedImage[] frames;
    private double x;       // hitbox left
    private final double y; // hitbox top
    private int dir = -1;
    private int tick;

    /** Creates the enemy standing on the first surface at or below tile (col, row). */
    public Enemy(Type type, int col, int row, Level level) {
        this.type = type;
        this.frames = new BufferedImage[] {
            type.sheet.getSubimage(0, 0, FRAME, FRAME),
            type.sheet.getSubimage(FRAME, 0, FRAME, FRAME),
        };
        double[] spot = level.standingSpot(col, row);
        this.x = spot[0] - type.hitW / 2.0;
        this.y = spot[1] - type.hitH;
        clampToSurface(level);
        if (type.speed == 0) { // stand at the far right of its floor, leaving room to land on the left
            for (int i = 0; i < Level.TILE && level.hasFloorAt(x + type.hitW + 1, y + type.hitH); i++) {
                x++;
            }
        }
        this.tick = (col * 13) % type.frameTicks;
    }

    public void update(Level level) {
        tick++;
        if (type.speed == 0) {
            return;
        }
        double nextX = x + dir * type.speed;
        double front = dir > 0 ? nextX + type.hitW : nextX;
        double feet = y + type.hitH;
        boolean wall = level.isSolid(tile(front), tile(feet - 1)) || level.isSolid(tile(front), tile(y));
        boolean edge = !level.hasFloorAt(front, feet);
        if (wall || edge) {
            dir = -dir;
        } else {
            x = nextX;
        }
    }

    /** Slides the enemy back onto its floor if its spawn point hangs over an edge. */
    private void clampToSurface(Level level) {
        double feet = y + type.hitH;
        for (int i = 0; i < Level.TILE && !level.hasFloorAt(x, feet); i++) {
            x++;
        }
        for (int i = 0; i < Level.TILE && !level.hasFloorAt(x + type.hitW - EPS, feet); i++) {
            x--;
        }
    }

    public Rectangle bounds() {
        return new Rectangle((int) x, (int) y, type.hitW, type.hitH);
    }

    public void draw(Graphics2D g) {
        BufferedImage frame = frames[(tick / type.frameTicks) % 2];
        // Art sits on the bottom of its 32x32 frame; line that up with the hitbox's feet.
        int dx = (int) Math.round(x + type.hitW / 2.0 - FRAME / 2.0);
        int dy = (int) Math.round(y + type.hitH - FRAME);
        boolean movingRight = dir > 0;
        boolean flip = type.speed > 0 && (movingRight == type.facesLeft);
        if (flip) {
            g.drawImage(frame, dx + FRAME, dy, -FRAME, FRAME, null);
        } else {
            g.drawImage(frame, dx, dy, null);
        }
    }

    private static int tile(double pixel) {
        return (int) Math.floor(pixel / Level.TILE);
    }
}
