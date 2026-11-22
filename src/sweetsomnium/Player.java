package sweetsomnium;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/**
 * The main character: a little marshmallow.
 * All speeds are in pixels per frame (the game runs at 60 frames per second).
 */
public class Player {

    /** Hitbox size. A bit narrower than the art so near-misses feel fair. */
    public static final int WIDTH = 22;
    public static final int HEIGHT = 30;

    // Movement feel
    private static final double RUN_SPEED = 4.0;
    private static final double GROUND_ACCEL = 0.7;
    private static final double GROUND_FRICTION = 0.8;
    private static final double AIR_ACCEL = 0.45;
    private static final double AIR_FRICTION = 0.12;
    private static final double GRAVITY = 0.55;
    private static final double MAX_FALL_SPEED = 13.0;
    private static final double JUMP_SPEED = 12.0;      // about 2.7 tiles high
    private static final double JUMP_CUT_SPEED = 4.0;   // let go early = smaller jump
    private static final int COYOTE_FRAMES = 6;         // can still jump just after walking off a ledge
    private static final int JUMP_BUFFER_FRAMES = 6;    // a jump pressed just before landing still counts
    private static final int RESPAWN_BLINK_FRAMES = 60; // blinking + can't be hurt after respawning
    private static final double EPS = 0.001;

    // Frames on marshmallow.png. The sheet isn't on an even grid, so each frame is listed by hand.
    private static final BufferedImage[] IDLE = frames(new int[][] {{1, 1, 30, 31}, {1, 33, 30, 31}});
    private static final BufferedImage[] WALK = frames(new int[][] {{37, 34, 24, 30}, {69, 35, 22, 29}, {101, 34, 24, 30}});
    private static final BufferedImage JUMP = frames(new int[][] {{1, 70, 30, 32}})[0];
    private static final BufferedImage FALL = frames(new int[][] {{1, 106, 29, 32}})[0];
    private static final BufferedImage[] HURT = frames(new int[][] {{2, 145, 27, 31}, {33, 145, 30, 31}});
    /** The side-view walk frames face this way on the sheet; flip this if he moonwalks. */
    private static final boolean SHEET_FACES_RIGHT = true;

    private double x;
    private double y;
    private double vx;
    private double vy;
    private boolean onGround;
    private boolean facingRight = true;
    private int coyoteTimer;
    private int jumpBufferTimer;
    private double walkPhase;
    private int animTick;
    private int respawnBlink;
    private boolean hurt;
    private Platform riding; // the platform we're standing on, so moving ones carry us

    // This level's look of the frames (paler in later levels)
    private final BufferedImage[] idle;
    private final BufferedImage[] walk;
    private final BufferedImage jump;
    private final BufferedImage fall;

    /** @param pallor 0 = the marshmallow as drawn; higher = paler and greyer */
    public Player(double x, double y, float pallor) {
        idle = pale(IDLE, pallor);
        walk = pale(WALK, pallor);
        jump = pale(new BufferedImage[] {JUMP}, pallor)[0];
        fall = pale(new BufferedImage[] {FALL}, pallor)[0];
        respawn(x, y);
        respawnBlink = 0;
    }

    public void respawn(double x, double y) {
        this.x = x;
        this.y = y;
        vx = 0;
        vy = 0;
        onGround = false;
        facingRight = true;
        coyoteTimer = 0;
        jumpBufferTimer = 0;
        riding = null;
        respawnBlink = RESPAWN_BLINK_FRAMES;
        hurt = false;
    }

    /** Shows the white "ouch" frames until the next respawn. */
    public void hurt() {
        hurt = true;
    }

    /** True right after respawning, so you can't get hit twice in a row. */
    public boolean isInvulnerable() {
        return respawnBlink > 0;
    }

    public void update(Input input, Level level) {
        // Ride along with a moving platform (or drop when a crumbling one gives way)
        if (onGround && riding != null) {
            if (riding.isSolid()) {
                x += riding.dx();
                y = riding.top() - HEIGHT;
                riding.standOn();
            } else {
                onGround = false;
                riding = null;
            }
        }

        // Run
        int dir = (input.right() ? 1 : 0) - (input.left() ? 1 : 0);
        if (dir != 0) {
            vx = approach(vx, dir * RUN_SPEED, onGround ? GROUND_ACCEL : AIR_ACCEL);
            facingRight = dir > 0;
        } else {
            vx = approach(vx, 0, onGround ? GROUND_FRICTION : AIR_FRICTION);
        }

        // Jump
        coyoteTimer = onGround ? COYOTE_FRAMES : Math.max(0, coyoteTimer - 1);
        jumpBufferTimer = input.consumeJump() ? JUMP_BUFFER_FRAMES : Math.max(0, jumpBufferTimer - 1);
        if (jumpBufferTimer > 0 && coyoteTimer > 0) {
            vy = -JUMP_SPEED;
            onGround = false;
            coyoteTimer = 0;
            jumpBufferTimer = 0;
        }
        if (!input.jumpHeld() && vy < -JUMP_CUT_SPEED) {
            vy = -JUMP_CUT_SPEED;
        }

        // Fall
        vy = Math.min(vy + GRAVITY, MAX_FALL_SPEED);

        // Move one axis at a time so we can tell walls from floors
        moveX(level);
        moveY(level);

        // Animation timers
        walkPhase = (onGround && Math.abs(vx) > 0.3) ? walkPhase + Math.abs(vx) * 0.05 : 0;
        if (respawnBlink > 0) {
            respawnBlink--;
        }
    }

    /** Ticks animations even while the game is paused on a respawn. */
    public void animate() {
        animTick++;
    }

    private void moveX(Level level) {
        x += vx;
        int top = tile(y);
        int bottom = tile(y + HEIGHT - EPS);
        if (vx > 0) {
            int col = tile(x + WIDTH - EPS);
            for (int row = top; row <= bottom; row++) {
                if (level.isSolid(col, row)) {
                    x = col * Level.TILE - WIDTH;
                    vx = 0;
                    break;
                }
            }
        } else if (vx < 0) {
            int col = tile(x);
            for (int row = top; row <= bottom; row++) {
                if (level.isSolid(col, row)) {
                    x = (col + 1) * Level.TILE;
                    vx = 0;
                    break;
                }
            }
        }
    }

    private void moveY(Level level) {
        double oldBottom = y + HEIGHT;
        y += vy;
        onGround = false;
        riding = null;
        int left = tile(x);
        int right = tile(x + WIDTH - EPS);

        if (vy > 0) {
            int row = tile(y + HEIGHT - EPS);
            for (int col = left; col <= right; col++) {
                if (level.isSolid(col, row)) {
                    y = row * Level.TILE - HEIGHT;
                    vy = 0;
                    onGround = true;
                    return;
                }
            }
            // One-way platforms: only catch us if we were above them last frame
            for (Platform p : level.platforms()) {
                if (!p.isSolid()) {
                    continue;
                }
                boolean wasAbove = oldBottom <= p.top() + EPS;
                boolean nowBelowTop = y + HEIGHT >= p.top();
                boolean overlapsX = x + WIDTH > p.left() && x < p.right();
                if (wasAbove && nowBelowTop && overlapsX) {
                    y = p.top() - HEIGHT;
                    vy = 0;
                    onGround = true;
                    riding = p;
                    return;
                }
            }
        } else if (vy < 0) {
            int row = tile(y);
            for (int col = left; col <= right; col++) {
                if (level.isSolid(col, row)) {
                    y = (row + 1) * Level.TILE; // bonk
                    vy = 0;
                    return;
                }
            }
        }
    }

    private static int tile(double pixel) {
        return (int) Math.floor(pixel / Level.TILE);
    }

    private static double approach(double value, double target, double step) {
        return value < target ? Math.min(value + step, target) : Math.max(value - step, target);
    }

    public Rectangle bounds() {
        return new Rectangle((int) x, (int) y, WIDTH, HEIGHT);
    }

    public double centerX() {
        return x + WIDTH / 2.0;
    }

    public boolean isFacingRight() {
        return facingRight;
    }

    /** True once the player has dropped out of the bottom of the level. */
    public boolean hasFallenOut(Level level) {
        return y > level.pixelHeight() + 40;
    }

    // ---------------------------------------------------------------- drawing

    public void draw(Graphics2D g) {
        if (!hurt && respawnBlink > 0 && (respawnBlink / 4) % 2 == 0) {
            return;
        }
        BufferedImage frame;
        boolean sideView = false;
        if (hurt) {
            frame = HURT[(animTick / 4) % 2];
        } else if (!onGround) {
            frame = vy < 0 ? jump : fall;
        } else if (walkPhase > 0) {
            frame = walk[(int) walkPhase % walk.length];
            sideView = true;
        } else {
            frame = idle[(animTick / 40) % 2];
        }

        // Feet on the hitbox's bottom edge, centred
        int w = frame.getWidth();
        int h = frame.getHeight();
        int dx = (int) Math.round(x + WIDTH / 2.0 - w / 2.0);
        int dy = (int) Math.round(y + HEIGHT - h);
        boolean flip = sideView && (facingRight != SHEET_FACES_RIGHT);
        if (flip) {
            g.drawImage(frame, dx + w, dy, -w, h, null);
        } else {
            g.drawImage(frame, dx, dy, null);
        }
    }

    /** Draws him standing still with his feet at (cx, feetY); used by the ending screen. */
    public void drawStanding(Graphics2D g, int cx, int feetY) {
        BufferedImage frame = idle[0];
        g.drawImage(frame, cx - frame.getWidth() / 2, feetY - frame.getHeight(), null);
    }

    /** Copies of the frames, washed towards a pale grey. */
    private static BufferedImage[] pale(BufferedImage[] src, float amount) {
        if (amount <= 0) {
            return src;
        }
        BufferedImage[] out = new BufferedImage[src.length];
        for (int i = 0; i < src.length; i++) {
            BufferedImage f = src[i];
            BufferedImage img = new BufferedImage(f.getWidth(), f.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < f.getHeight(); y++) {
                for (int x = 0; x < f.getWidth(); x++) {
                    int p = f.getRGB(x, y);
                    int a = p >>> 24;
                    int r = (p >> 16) & 255;
                    int g = (p >> 8) & 255;
                    int b = p & 255;
                    int grey = (int) (0.3 * r + 0.59 * g + 0.11 * b);
                    r = pale(r, grey, amount);
                    g = pale(g, grey, amount);
                    b = pale(b, grey, amount);
                    img.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
                }
            }
            out[i] = img;
        }
        return out;
    }

    /** Towards grey, then a little lighter. */
    private static int pale(int channel, int grey, float amount) {
        double c = channel + (grey - channel) * amount;
        c += (215 - c) * amount * 0.25;
        return (int) Math.max(0, Math.min(255, c));
    }

    private static BufferedImage[] frames(int[][] boxes) {
        BufferedImage[] out = new BufferedImage[boxes.length];
        for (int i = 0; i < boxes.length; i++) {
            int[] b = boxes[i];
            out[i] = Assets.MARSHMALLOW.getSubimage(b[0], b[1], b[2], b[3]);
        }
        return out;
    }
}
