package sweetsomnium;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * A thin striped candy platform. It's one-way: you can jump up through it
 * from below and land on top of it. It can also glide back and forth,
 * or crumble: shake when you stand on it, drop away, and come back later.
 */
public class Platform {

    /** The striped bar sits this many pixels down inside its 48px-tall sprite. */
    private static final int BAR_OFFSET_Y = 33;
    private static final int SHAKE_FRAMES = 30;   // how long you can stand on a crumbling one
    private static final int RETURN_FRAMES = 150; // how long until it comes back

    // What a crumbling platform is doing right now
    private static final int STEADY = 0;
    private static final int SHAKING = 1;
    private static final int FALLING = 2;
    private static final int GONE = 3;

    private final BufferedImage sprite;
    private final double startX;
    private final double startY;
    private final double rangeX;
    private final double rangeY;
    private final int period;   // frames for a full trip there and back; 0 = doesn't move
    private double x;
    private double y;
    private double dx;          // how far it moved sideways last frame (to carry the player)
    private int tick;
    private final boolean crumbles;
    private int state = STEADY;
    private int stateTimer;
    private double fallSpeed;

    /**
     * @param tileX      left edge, in tiles (can be fractional, e.g. 25.5)
     * @param topRow     the tile row line the platform's top surface sits on
     * @param widthTiles 1 (small sprite) or 2 (long sprite)
     * @param moveX      how far it travels sideways, in tiles (0 = it doesn't)
     * @param moveY      how far it travels down, in tiles (negative = up)
     * @param period     frames for one full trip there and back
     * @param crumbles   true = drops away shortly after you stand on it
     */
    public Platform(double tileX, int topRow, int widthTiles, double moveX, double moveY, int period,
                    boolean crumbles, Theme theme) {
        this.crumbles = crumbles;
        if (widthTiles == 1) {
            sprite = theme.platformSmall;
        } else if (widthTiles == 2) {
            sprite = theme.platformLong;
        } else {
            throw new IllegalArgumentException("Platforms are 1 or 2 tiles wide, not " + widthTiles);
        }
        startX = tileX * Level.TILE;
        startY = topRow * Level.TILE - BAR_OFFSET_Y;
        rangeX = moveX * Level.TILE;
        rangeY = moveY * Level.TILE;
        this.period = period;
        x = startX;
        y = startY;
    }

    public void update() {
        if (crumbles) {
            updateCrumble();
            return;
        }
        if (period <= 0) {
            return;
        }
        tick++;
        // Eases in and out at each end of its path
        double t = 0.5 - 0.5 * Math.cos(2 * Math.PI * tick / period);
        double nx = startX + rangeX * t;
        double ny = startY + rangeY * t;
        dx = nx - x;
        x = nx;
        y = ny;
    }

    private void updateCrumble() {
        tick++;
        switch (state) {
            case SHAKING:
                if (--stateTimer <= 0) {
                    state = FALLING;
                    fallSpeed = 0;
                }
                break;
            case FALLING:
                fallSpeed = Math.min(fallSpeed + 0.5, 12);
                y += fallSpeed;
                if (y > GamePanel.VIEW_H + 50) {
                    state = GONE;
                    stateTimer = RETURN_FRAMES;
                }
                break;
            case GONE:
                if (--stateTimer <= 0) {
                    y = startY;
                    state = STEADY;
                }
                break;
            default:
                break;
        }
    }

    /** Called every frame the player stands on this platform. */
    public void standOn() {
        if (crumbles && state == STEADY) {
            state = SHAKING;
            stateTimer = SHAKE_FRAMES;
        }
    }

    /** False while a crumbling platform is falling or gone. */
    public boolean isSolid() {
        return state == STEADY || state == SHAKING;
    }

    public boolean crumbles() {
        return crumbles;
    }

    /** Where the bar is drawn when still: {left x, top y, width} (used to paint it out of level 3's backdrop). */
    public int[] barArea() {
        return new int[] {(int) startX, (int) (startY + BAR_OFFSET_Y), sprite.getWidth()};
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

    public double dx() {
        return dx;
    }

    public void draw(Graphics2D g) {
        if (state == GONE) {
            return;
        }
        int shake = state == SHAKING ?((tick / 2) % 2 == 0 ? 1 : -1) : 0;
        g.drawImage(sprite, (int) Math.round(x) + shake, (int) Math.round(y), null);
    }
}
