package sweetsomnium;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * A candy creature. Touching one costs a life.
 * Each enemy has a body (gummy or worm, drawn with the level's sprites) and a behaviour.
 * They all stay on whatever they're standing on: walls, edges and spikes turn them around.
 */
public class Enemy {

    public enum Kind { GUMMY, WORM }

    public enum Behavior {
        /** Level 1: walks slowly back and forth. */
        PATROL(0.5),
        /** Level 1: bounces on the spot (for spots too small to walk on). */
        IDLE(0),
        /** Level 2: quicker, stops and starts, sometimes turns for no reason. */
        TWITCHY(1.1),
        /** Level 2: walks, and hops when you get close. */
        HOPPER(0.8),
        /** Level 2: crawls slowly, then rushes at you when you're near. */
        CHASER(0.5);

        final double speed;

        Behavior(double speed) {
            this.speed = speed;
        }
    }

    private static final int FRAME = 32;           // sprite frames are 32x32
    private static final double CHASE_SPEED = 1.4;
    private static final double CHASE_RANGE = 200; // px, sideways
    private static final double HOP_RANGE = 150;
    private static final double HOP_SPEED = 6.5;
    private static final double HOP_GRAVITY = 0.45;
    private static final double EPS = 0.001;

    private final Behavior behavior;
    private final BufferedImage[] frames;
    private final boolean artFacesLeft;
    private final int frameTicks;
    private final int hitW;
    private final int hitH;
    private final double artCenterX; // where the art's middle is inside its 32x32 frame
    private final Random random;
    private final double baseSpeed;

    private double x;          // hitbox left
    private final double floorY;
    private double hop;        // how high it is off the floor right now
    private double hopVy;
    private int hopCooldown;
    private int dir = -1;
    private int pauseTimer;
    private int twitchTimer;
    private int tick;

    /** Creates the enemy standing on the nearest floor at or below tile (col, row). */
    public Enemy(Kind kind, Behavior behavior, int col, int row, Level level) {
        this.behavior = behavior;
        this.baseSpeed = (kind == Kind.WORM && behavior == Behavior.PATROL) ? 0.35 : behavior.speed; // worms crawl slower
        Theme theme = level.theme();
        this.frames = kind == Kind.GUMMY ? theme.gummyFrames : theme.wormFrames;
        this.artFacesLeft = false; // every enemy sprite so far looks to the right
        this.frameTicks = behavior == Behavior.TWITCHY ? 7 : (kind == Kind.GUMMY ? 12 : 16);

        // Size the hitbox from the art itself (a bit smaller, so near-misses are fair)
        int[] art = artBounds(frames);
        this.hitW = Math.max(8, (int) Math.round((art[2] - art[0]) * 0.75));
        this.hitH = Math.max(8, (int) Math.round((art[3] - art[1]) * 0.8));
        this.artCenterX = (art[0] + art[2]) / 2.0;

        double[] spot = level.standingSpot(col, row);
        this.x = spot[0] - hitW / 2.0;
        this.floorY = spot[1];
        clampToFloor(level);
        if (behavior == Behavior.IDLE) { // stand at the far right, leaving room to land on the left
            for (int i = 0; i < Level.TILE && level.hasFloorAt(x + hitW + 1, floorY); i++) {
                x++;
            }
        }
        this.random = new Random(col * 31L + row);
        this.tick = (col * 13) % frameTicks;
        this.twitchTimer = 40 + random.nextInt(80);
    }

    public void update(Level level, Rectangle player) {
        tick++;
        double speed = baseSpeed;

        switch (behavior) {
            case IDLE -> {
                return;
            }
            case TWITCHY -> {
                if (pauseTimer > 0) {
                    pauseTimer--;
                    return;
                }
                if (--twitchTimer <= 0) {
                    pauseTimer = 12 + random.nextInt(25);
                    if (random.nextBoolean()) {
                        dir = -dir;
                    }
                    twitchTimer = 40 + random.nextInt(90);
                }
            }
            case HOPPER -> updateHop(player);
            case CHASER -> {
                if (player != null && isNear(player, CHASE_RANGE)) {
                    dir = player.getCenterX() > x + hitW / 2.0 ? 1 : -1;
                    speed = CHASE_SPEED;
                    if (!canStep(level, dir, speed)) {
                        return; // waits at the edge instead of turning away
                    }
                }
            }
            default -> { }
        }

        if (canStep(level, dir, speed)) {
            x += dir * speed;
        } else {
            dir = -dir;
        }
    }

    private void updateHop(Rectangle player) {
        if (hop > 0 || hopVy != 0) {
            hopVy -= HOP_GRAVITY;
            hop += hopVy;
            if (hop <= 0) {
                hop = 0;
                hopVy = 0;
                hopCooldown = 45;
            }
        } else if (hopCooldown > 0) {
            hopCooldown--;
        } else if (player != null && isNear(player, HOP_RANGE)) {
            hopVy = HOP_SPEED;
            hop = HOP_SPEED;
        }
    }

    /** Close sideways, and roughly on the same level. */
    private boolean isNear(Rectangle player, double range) {
        double dx = Math.abs(player.getCenterX() - (x + hitW / 2.0));
        double dy = Math.abs(player.getMaxY() - floorY);
        return dx < range && dy < Level.TILE * 1.5;
    }

    private boolean canStep(Level level, int direction, double speed) {
        double nextX = x + direction * speed;
        double front = direction > 0 ? nextX + hitW - EPS : nextX;
        int col = tile(front);
        boolean wall = level.isSolid(col, tile(floorY - 1)) || level.isSolid(col, tile(floorY - hitH));
        boolean spike = level.isSpike(col, tile(floorY - 1));
        return !wall && !spike && level.hasFloorAt(front, floorY);
    }

    /** Slides the enemy back onto its floor if its spawn point hangs over an edge. */
    private void clampToFloor(Level level) {
        for (int i = 0; i < Level.TILE && !level.hasFloorAt(x, floorY); i++) {
            x++;
        }
        for (int i = 0; i < Level.TILE && !level.hasFloorAt(x + hitW - EPS, floorY); i++) {
            x--;
        }
    }

    public Rectangle bounds() {
        return new Rectangle((int) x, (int) (floorY - hitH - hop), hitW, hitH);
    }

    public void draw(Graphics2D g) {
        BufferedImage frame = frames[(tick / frameTicks) % 2];
        boolean flip = behavior != Behavior.IDLE && ((dir > 0) == artFacesLeft);
        double centre = x + hitW / 2.0;
        int dy = (int) Math.round(floorY - hop - FRAME); // art sits on the bottom of its frame
        if (flip) {
            int dx = (int) Math.round(centre - (FRAME - artCenterX));
            g.drawImage(frame, dx + FRAME, dy, -FRAME, FRAME, null);
        } else {
            int dx = (int) Math.round(centre - artCenterX);
            g.drawImage(frame, dx, dy, null);
        }
    }

    /** {minX, minY, maxX, maxY} of the visible pixels across all frames. */
    private static int[] artBounds(BufferedImage[] frames) {
        int minX = FRAME;
        int minY = FRAME;
        int maxX = 0;
        int maxY = 0;
        for (BufferedImage f : frames) {
            for (int y = 0; y < f.getHeight(); y++) {
                for (int x = 0; x < f.getWidth(); x++) {
                    if ((f.getRGB(x, y) >>> 24) > 40) {
                        minX = Math.min(minX, x);
                        minY = Math.min(minY, y);
                        maxX = Math.max(maxX, x + 1);
                        maxY = Math.max(maxY, y + 1);
                    }
                }
            }
        }
        return new int[] {minX, minY, maxX, maxY};
    }

    private static int tile(double pixel) {
        return (int) Math.floor(pixel / Level.TILE);
    }
}
