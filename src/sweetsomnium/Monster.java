package sweetsomnium;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/**
 * Level 3's creature. It hangs on a thread at the top of the screen and drops
 * fast onto anyone who walks underneath, waits, then slowly climbs back up.
 * Touching it costs a life. Lure it down, wait, and slip past while it climbs.
 */
public class Monster {

    // Where things are inside monster.png (191x144)
    private static final int HIT_X = 68;     // its body; the long legs don't hurt
    private static final int HIT_Y = 40;
    private static final int HIT_W = 64;
    private static final int HIT_H = 84;
    private static final int THREAD_X = 100; // where the thread meets its back
    private static final int THREAD_Y = 40;

    private static final double REST_Y = -40;      // sprite top while hanging (body just on screen)
    private static final double TRIGGER_RANGE = 100;
    private static final double DROP_ACCEL = 1.1;
    private static final double DROP_MAX = 22;
    private static final double CLIMB_SPEED = 2.2;
    private static final int WAIT_FRAMES = 45;
    private static final int COOLDOWN_FRAMES = 30;
    private static final Color THREAD = new Color(0x2A1414);

    private enum State { HANGING, DROPPING, WAITING, CLIMBING }

    private final BufferedImage sprite = Assets.MONSTER;
    private final double left;    // sprite x
    private final double lowestY; // sprite y when it has dropped all the way
    private double y = REST_Y;
    private double vy;
    private State state = State.HANGING;
    private int timer;
    private int tick;

    /** Hangs above column col and drops onto the first ground below it (or into the pit). */
    public Monster(int col, Level level) {
        double centreX = col * Level.TILE + Level.TILE / 2.0;
        this.left = centreX - (HIT_X + HIT_W / 2.0);
        double floor = level.pixelHeight() + 160; // no ground: drops out of sight
        for (int row = 0; row < level.pixelHeight() / Level.TILE; row++) {
            if (level.isSolid(col, row)) {
                floor = row * Level.TILE;
                break;
            }
        }
        this.lowestY = floor - sprite.getHeight(); // legs touch the ground
        this.tick = col * 17;
    }

    public void update(Rectangle player) {
        tick++;
        switch (state) {
            case HANGING -> {
                if (timer > 0) {
                    timer--;
                } else if (player != null && isBelow(player)) {
                    state = State.DROPPING;
                    vy = 0;
                }
            }
            case DROPPING -> {
                vy = Math.min(vy + DROP_ACCEL, DROP_MAX);
                y += vy;
                if (y >= lowestY) {
                    y = lowestY;
                    state = State.WAITING;
                    timer = WAIT_FRAMES;
                }
            }
            case WAITING -> {
                if (--timer <= 0) {
                    state = State.CLIMBING;
                }
            }
            case CLIMBING -> {
                y -= CLIMB_SPEED;
                if (y <= REST_Y) {
                    y = REST_Y;
                    state = State.HANGING;
                    timer = COOLDOWN_FRAMES;
                }
            }
        }
    }

    private boolean isBelow(Rectangle player) {
        double centre = left + sway() + HIT_X + HIT_W / 2.0;
        return Math.abs(player.getCenterX() - centre) < TRIGGER_RANGE && player.getY() > y + HIT_Y;
    }

    /** A slow, uneasy swing on its thread. */
    private double sway() {
        return state == State.HANGING ? Math.sin(tick * 0.03) * 4 : 0;
    }

    public Rectangle bounds() {
        return new Rectangle((int) (left + sway() + HIT_X), (int) (y + HIT_Y), HIT_W, HIT_H);
    }

    public void draw(Graphics2D g) {
        int sx = (int) Math.round(left + sway());
        int sy = (int) Math.round(y);
        g.setColor(THREAD);
        g.fillRect(sx + THREAD_X, -2000, 1, sy + THREAD_Y + 2000);
        g.drawImage(sprite, sx, sy, null);
    }
}
