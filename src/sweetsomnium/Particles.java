package sweetsomnium;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.Random;

/**
 * Little bits that burst out of the marshmallow when it gets hit.
 *
 * Built on parallel arrays: particle number i is x[i], y[i], vx[i], vy[i], life[i]...
 * The arrays are made once with room for MAX particles and reused forever (a "pool"):
 * a slot with life 0 is free, and a new burst fills free slots by searching for them.
 */
final class Particles {

    private static final int MAX = 160;

    private final double[] x = new double[MAX];
    private final double[] y = new double[MAX];
    private final double[] vx = new double[MAX];
    private final double[] vy = new double[MAX];
    private final double[] gravity = new double[MAX];
    private final int[] life = new int[MAX];      // frames left; 0 = this slot is free
    private final int[] maxLife = new int[MAX];
    private final int[] size = new int[MAX];
    private final Color[] color = new Color[MAX];
    private final Random random = new Random(11);

    /**
     * Throws {@code count} particles out from (cx, cy) in every direction.
     *
     * @param colors  each particle picks one of these
     * @param speed   how fast they fly out
     * @param fall    pulled down by this much each frame (negative = they float up)
     */
    void burst(double cx, double cy, int count, Color[] colors, double speed, double fall) {
        int slot = 0;
        for (int n = 0; n < count; n++) {
            while (slot < MAX && life[slot] > 0) { // linear search for a free slot
                slot++;
            }
            if (slot == MAX) {
                return; // pool is full: skip the rest
            }
            double angle = random.nextDouble() * 2 * Math.PI;
            double s = speed * (0.35 + random.nextDouble() * 0.65);
            x[slot] = cx;
            y[slot] = cy;
            vx[slot] = Math.cos(angle) * s;
            vy[slot] = Math.sin(angle) * s - speed * 0.4; // a little upward kick
            gravity[slot] = fall;
            maxLife[slot] = 30 + random.nextInt(30);
            life[slot] = maxLife[slot];
            size[slot] = 3 + random.nextInt(4);
            color[slot] = colors[random.nextInt(colors.length)];
        }
    }

    void update() {
        for (int i = 0; i < MAX; i++) {
            if (life[i] > 0) {
                x[i] += vx[i];
                y[i] += vy[i];
                vx[i] *= 0.96;          // air slows them down
                vy[i] += gravity[i];
                life[i]--;
            }
        }
    }

    /** g is already offset by the camera. Particles fade out as they run out of life. */
    void draw(Graphics2D g) {
        for (int i = 0; i < MAX; i++) {
            if (life[i] > 0) {
                int alpha = 255 * life[i] / maxLife[i];
                Color c = color[i];
                g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha));
                int s = Math.max(1, size[i] * life[i] / maxLife[i] + 1);
                g.fillRect((int) x[i] - s / 2, (int) y[i] - s / 2, s, s);
            }
        }
    }

    void clear() {
        for (int i = 0; i < MAX; i++) {
            life[i] = 0;
        }
    }
}
