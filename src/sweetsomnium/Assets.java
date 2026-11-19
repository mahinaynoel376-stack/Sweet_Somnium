package sweetsomnium;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

/**
 * Loads every sprite once, at startup, from src/assets.
 * Eclipse copies that folder into bin/ automatically, so the images are found
 * on the classpath/module path.
 */
public final class Assets {

    public static final BufferedImage GROUND_TOP      = load("ground_top.png");
    public static final BufferedImage GROUND_FILL     = load("ground_fill.png");
    public static final BufferedImage CHOCOLATE_BLOCK = load("chocolate_block.png");
    public static final BufferedImage PLATFORM_SMALL  = load("platform_small.png");
    public static final BufferedImage PLATFORM_LONG   = load("platform_long.png");
    public static final BufferedImage CANDY           = load("candy.png");
    public static final BufferedImage CLOUD_MAUVE     = load("cloud_mauve.png");
    public static final BufferedImage CLOUD_PINK      = load("cloud_pink.png");
    public static final BufferedImage SKY             = load("sky.png");
    public static final BufferedImage PIT_SHADE       = load("pit_shade.png");
    public static final BufferedImage MARSHMALLOW     = load("marshmallow.png"); // the hero's sprite sheet
    public static final BufferedImage GUMMY           = load("gummy.png");       // 2 frames, 32x32 each
    public static final BufferedImage GUMMY_WORM      = load("gummy_worm.png");  // 2 frames, 32x32 each
    // candy_spike.png is in the folder too; it's saved for the spike hazards in later levels.

    private Assets() { }

    private static BufferedImage load(String name) {
        String path = "/assets/" + name;
        try (InputStream in = Assets.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing sprite " + path
                        + " (is src/assets on the build path and copied into bin/?)");
            }
            BufferedImage raw = ImageIO.read(in);
            // Copy into a plain ARGB image: draws faster and behaves the same everywhere.
            BufferedImage img = new BufferedImage(raw.getWidth(), raw.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.drawImage(raw, 0, 0, null);
            g.dispose();
            return img;
        } catch (IOException e) {
            throw new IllegalStateException("Could not read sprite " + path, e);
        }
    }
}
