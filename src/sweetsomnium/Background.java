package sweetsomnium;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.Map;

/**
 * Everything behind the level: the banded polka-dot sky, two layers of clouds
 * (with parallax) and the dark fade at the bottom of the pits.
 */
public class Background {

    private static final int W = GamePanel.VIEW_W;
    private static final int H = GamePanel.VIEW_H;
    private static final int DOT_SPACING = 48;

    private static final double DOT_PARALLAX = 0.1;
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

    // A sleepy-happy face for the clouds (Candy Land's clouds smile).
    private static final String[] CLOUD_FACE = {
        "....kk....................kk....",
        "...kwkk..................kwkk...",
        "...kkkk..................kkkk...",
        "...kkkk..................kkkk...",
        "...kkkk..................kkkk...",
        "....kk....................kk....",
        "............k......k............",
        "pppp.........kkkkkk.........pppp",
        "pppp........................pppp",
    };

    private final BufferedImage sky;
    private final BufferedImage mainCloud;
    private final BufferedImage farCloud;
    private final BufferedImage pitShade;

    public Background() {
        sky = buildSky();
        mainCloud = withFace(Assets.CLOUD_MAUVE);
        farCloud = scaled(Assets.CLOUD_PINK, 0.5);
        pitShade = tinted(Assets.PIT_SHADE, new Color(0x0350A3));
    }

    public void draw(Graphics2D g, double camX, int tick) {
        int dotShift = (int) Math.floor(camX * DOT_PARALLAX) % DOT_SPACING;
        g.drawImage(sky, -dotShift, 0, null);

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

        // Fade into deep blue at the very bottom (only shows through the pits)
        for (int x = 0; x < W; x += pitShade.getWidth()) {
            g.drawImage(pitShade, x, H - pitShade.getHeight(), null);
        }
    }

    // ---------------------------------------------------------------- pre-drawn layers

    /** sky.png, tinted into the mockup's colour bands, with soft polka dots. One dot-period wider than the view so it can scroll. */
    private static BufferedImage buildSky() {
        int w = W + DOT_SPACING;
        BufferedImage img = new BufferedImage(w, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(Assets.SKY, 0, 0, w, H, null);

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f));
        for (int i = 0; i < BAND_COLORS.length; i++) {
            int y0 = (int) Math.round(BAND_EDGES[i] * Level.TILE);
            int y1 = (int) Math.round(BAND_EDGES[i + 1] * Level.TILE);
            g.setColor(BAND_COLORS[i]);
            g.fillRect(0, y0, w, y1 - y0);
        }
        g.setComposite(AlphaComposite.SrcOver);

        // Soft dots in the middle bands
        BufferedImage dot = softDot(15);
        for (int i = 1; i <= 3; i++) {
            int y0 = (int) Math.round(BAND_EDGES[i] * Level.TILE);
            int y1 = (int) Math.round(BAND_EDGES[i + 1] * Level.TILE);
            int shift = (i % 2) * DOT_SPACING / 2;
            for (int y = y0 + DOT_SPACING / 2; y + 12 < y1; y += DOT_SPACING) {
                for (int x = shift; x < w + DOT_SPACING; x += DOT_SPACING) {
                    g.drawImage(dot, x - dot.getWidth() / 2, y - dot.getHeight() / 2, null);
                }
            }
        }
        g.dispose();
        return img;
    }

    private static BufferedImage softDot(int radius) {
        BufferedImage img = new BufferedImage(radius * 2, radius * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setPaint(new RadialGradientPaint(new Point2D.Float(radius, radius), radius,
                new float[] {0f, 0.55f, 1f},
                new Color[] {new Color(255, 255, 255, 46), new Color(255, 255, 255, 30), new Color(255, 255, 255, 0)},
                MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g.fillRect(0, 0, radius * 2, radius * 2);
        g.dispose();
        return img;
    }

    private static BufferedImage withFace(BufferedImage cloud) {
        BufferedImage img = copy(cloud);
        Graphics2D g = img.createGraphics();
        PixelArt.draw(g, CLOUD_FACE, Map.of(
                'k', new Color(0x4A1640),
                'w', Color.WHITE,
                'p', new Color(0xEE6FAE)), 176, 101, 1);
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

    private static BufferedImage copy(BufferedImage src) {
        BufferedImage img = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return img;
    }
}
