package sweetsomnium;

import java.awt.Color;
import java.awt.image.BufferedImage;

/**
 * How a level looks: which sprites it uses and how washed-out things are.
 * Every level is the same world; each theme is that world a bit further gone.
 */
public final class Theme {

    final BufferedImage groundTop;
    final BufferedImage groundFill;
    final BufferedImage block;
    final BufferedImage platformSmall;
    final BufferedImage platformLong;
    final BufferedImage collectible;
    final BufferedImage spike;
    final BufferedImage[] gummyFrames;
    final BufferedImage[] wormFrames;
    final BufferedImage sky;
    /** Level 1's sky has the mockup's colour bands and clouds; later skies are bare. */
    final boolean bandsAndClouds;
    /** Colour the bottom of the pits fades into. */
    final Color pitColor;
    /** 1 = the door's full candy colours, lower = greyer. */
    final float doorSaturation;
    /** 0 = the marshmallow as drawn, higher = paler and greyer. */
    final float playerPallor;
    /**
     * A full picture of the level drawn behind everything (level 3's map with the faces).
     * When set, the ground, blocks and still platforms are already in the picture, so they aren't drawn again.
     */
    final BufferedImage backdrop;

    private Theme(BufferedImage groundTop, BufferedImage groundFill, BufferedImage block,
                  BufferedImage platformSmall, BufferedImage platformLong, BufferedImage collectible,
                  BufferedImage spike, BufferedImage[] gummyFrames, BufferedImage[] wormFrames,
                  BufferedImage sky, boolean bandsAndClouds, Color pitColor,
                  float doorSaturation, float playerPallor, BufferedImage backdrop) {
        this.groundTop = groundTop;
        this.groundFill = groundFill;
        this.block = block;
        this.platformSmall = platformSmall;
        this.platformLong = platformLong;
        this.collectible = collectible;
        this.spike = spike;
        this.gummyFrames = gummyFrames;
        this.wormFrames = wormFrames;
        this.sky = sky;
        this.bandsAndClouds = bandsAndClouds;
        this.pitColor = pitColor;
        this.doorSaturation = doorSaturation;
        this.playerPallor = playerPallor;
        this.backdrop = backdrop;
    }

    /** Level 1: bright and sweet. */
    static Theme candyLand() {
        return new Theme(Assets.GROUND_TOP, Assets.GROUND_FILL, Assets.CHOCOLATE_BLOCK,
                Assets.PLATFORM_SMALL, Assets.PLATFORM_LONG, Assets.CANDY, Assets.CANDY_SPIKE,
                row(Assets.GUMMY, 0), row(Assets.GUMMY_WORM, 0),
                Assets.SKY, true, new Color(0x0350A3), 1f, 0f, null);
    }

    /** Level 2: washed out, candies become capsules, the marshmallow looks a little pale. */
    static Theme sourTurn() {
        return new Theme(Assets.SOUR_GROUND_TOP, Assets.SOUR_GROUND_FILL, Assets.SOUR_BLOCK,
                Assets.SOUR_PLATFORM_SMALL, Assets.SOUR_PLATFORM_LONG, Assets.CAPSULE, Assets.CANDY_SPIKE,
                row(Assets.SOUR_ENEMIES, 0), row(Assets.SOUR_ENEMIES, 1),
                Assets.SOUR_SKY, false, new Color(0x10233F), 0.45f, 0.35f, null);
    }

    /** Level 3: dark and sick. Plain white pills; the marshmallow is pale and grey. */
    static Theme withdrawal() {
        return new Theme(Assets.WITHDRAWAL_GROUND_TOP, Assets.WITHDRAWAL_GROUND_FILL, Assets.WITHDRAWAL_BLOCK,
                Assets.WITHDRAWAL_PLATFORM_SMALL, Assets.WITHDRAWAL_PLATFORM_LONG, Assets.PILL, Assets.CANDY_SPIKE,
                row(Assets.SOUR_ENEMIES, 0), row(Assets.SOUR_ENEMIES, 1),
                Assets.WITHDRAWAL_SKY, false, new Color(0x0A0404), 0.08f, 0.7f, Assets.WITHDRAWAL_BACKDROP);
    }

    /** The two 32x32 frames on one row of an enemy sheet. */
    private static BufferedImage[] row(BufferedImage sheet, int row) {
        return new BufferedImage[] {
            sheet.getSubimage(0, row * 32, 32, 32),
            sheet.getSubimage(32, row * 32, 32, 32),
        };
    }

    /** Pulls a colour towards grey. amount 0 = unchanged, 1 = fully grey. */
    static Color desaturate(Color c, float amount) {
        int r = c.getRed();
        int g = c.getGreen();
        int b = c.getBlue();
        int grey = (int) (0.3 * r + 0.59 * g + 0.11 * b);
        return new Color(
                (int) (r + (grey - r) * amount),
                (int) (g + (grey - g) * amount),
                (int) (b + (grey - b) * amount),
                c.getAlpha());
    }
}
