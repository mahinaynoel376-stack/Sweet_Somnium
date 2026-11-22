package sweetsomnium;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Everything behind the level: the banded sky, two layers of clouds
 * (with parallax) and the dark fade at the bottom of the pits.
 */
public class Background {

    private static final int W = GamePanel.VIEW_W;
    private static final int H = GamePanel.VIEW_H;

    private static final double FAR_CLOUD_PARALLAX = 0.15;
    private static final double MAIN_CLOUD_PARALLAX = 0.5;

    // Sky bands traced from docs/reference/level1_mockup.png: band edges in tile rows, and colors.
    private static final double[] BAND_EDGES = {0, 3, 5.5, 8, 10.25, 16};
    private static final Color[] BAND_COLORS = {
        new Color(0x8BABFE), new Color(0x7FA3F4), new Color(0x5B9DE4), new Color(0x4698DA), new Color(0x3392CD),
    };

    // Big mauve clouds {x, y}. x is in "parallax space": they move at half the camera's speed.
    private static final int[][] MAIN_CLOUDS = {{34, 241}, {363, 67}, {650, 232}, {1049, 77}, {1400, 224}};
    // Small pale clouds far away, drifting slowly.
    private static final int[][] FAR_CLOUDS = {{120, 34}, {470, 150}, {790, 16}, {1110, 128}, {1380, 58}};

    private final BufferedImage sky;
    private final BufferedImage mainCloud;
    private final BufferedImage farCloud;
    private final BufferedImage pitShade;
    private final boolean clouds;

    public Background(Theme theme) {
        clouds = theme.bandsAndClouds;
        sky = buildSky(theme.sky, theme.bandsAndClouds);
        mainCloud = Assets.CLOUD_MAUVE;
        farCloud = scaled(Assets.CLOUD_PINK, 0.5);
        pitShade = tinted(Assets.PIT_SHADE, theme.pitColor);
    }

    public void draw(Graphics2D g, double camX, int tick) {
        g.drawImage(sky, 0, 0, null);
        if (clouds) {
            drawClouds(g, camX, tick);
        }

        // Fade into the dark at the very bottom (only shows through the pits)
        for (int x = 0; x < W; x += pitShade.getWidth()) {
            g.drawImage(pitShade, x, H - pitShade.getHeight(), null);
        }
    }

    private void drawClouds(Graphics2D g, double camX, int tick) {
        // Far clouds wrap around as they drift left
        double span = W + farCloud.getWidth() + 200;
        for (int[] c : FAR_CLOUDS) {
            double sx = c[0] - tick * 0.08 - camX * FAR_CLOUD_PARALLAX;
            sx = ((sx % span) + span) % span - farCloud.getWidth() - 100;
            g.drawImage(farCloud, (int) sx, c[1], null);
        }

        for (int i = 0; i < MAIN_CLOUDS.length; i++) {
            int sx = (int) Math.round(MAIN_CLOUDS[i][0] - camX * MAIN_CLOUD_PARALLAX);
            int sy = (int) Math.round(MAIN_CLOUDS[i][1] + Math.sin(tick * 0.02 + i * 1.7) * 3);
            if (sx < W && sx + mainCloud.getWidth() > 0) {
                g.drawImage(mainCloud, sx, sy, null);
            }
        }
    }

    // ---------------------------------------------------------------- pre-drawn layers

    /** The level's sky image stretched to the screen; level 1's gets the mockup's colour bands. */
    private static BufferedImage buildSky(BufferedImage skyImage, boolean bands) {
        int w = W;
        BufferedImage img = new BufferedImage(w, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(skyImage, 0, 0, w, H, null);
        if (!bands) {
            g.dispose();
            return img;
        }

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f));
        for (int i = 0; i < BAND_COLORS.length; i++) {
            int y0 = (int) Math.round(BAND_EDGES[i] * Level.TILE);
            int y1 = (int) Math.round(BAND_EDGES[i + 1] * Level.TILE);
            g.setColor(BAND_COLORS[i]);
            g.fillRect(0, y0, w, y1 - y0);
        }
        g.setComposite(AlphaComposite.SrcOver);

        g.dispose();
        return img;
    }

    private static BufferedImage scaled(BufferedImage src, double factor) {
        int w = (int) Math.round(src.getWidth() * factor);
        int h = (int) Math.round(src.getHeight() * factor);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return img;
    }

    /** Same alpha as src, every pixel recoloured to `color`. */
    private static BufferedImage tinted(BufferedImage src, Color color) {
        BufferedImage img = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        int rgb = color.getRGB() & 0xFFFFFF;
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int alpha = src.getRGB(x, y) >>> 24;
                img.setRGB(x, y, (alpha << 24) | rgb);
            }
        }
        return img;
    }

}
