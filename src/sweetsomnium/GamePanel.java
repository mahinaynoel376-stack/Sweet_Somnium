package sweetsomnium;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Map;

import javax.swing.JPanel;

/**
 * The game loop (60 updates per second) and all drawing.
 *
 * The game always draws into a fixed 1024x768 image; that image is then scaled
 * to fit the window. The loop thread owns all game state; Swing only ever
 * copies the finished frame onto the screen.
 */
public class GamePanel extends JPanel implements Runnable {

    private static final long serialVersionUID = 1L;

    public static final int VIEW_W = 1024;
    public static final int VIEW_H = 768;

    private static final int FPS = 60;
    private static final int START_LIVES = 3;
    private static final int RESPAWN_FRAMES = 70;
    private static final int BANNER_FRAMES = 150;

    private static final Font HUD_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 20);
    private static final Font BODY_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 22);
    private static final Font BIG_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 54);
    private static final Font TITLE_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 80);
    private static final Color INK = new Color(0x4A1640);
    private static final Color SOFT_WHITE = new Color(0xFFF4FA);

    private static final String[] HEART = {
        ".kkk...kkk.",
        "kppwk.kpppk",
        "kpwppkppppk",
        "kpppppppppk",
        "kpppppppppk",
        ".kpppppppk.",
        "..kpppppk..",
        "...kpppk...",
        "....kpk....",
        ".....k.....",
    };
    private static final Map<Character, Color> HEART_FULL = Map.of(
            'k', INK, 'p', new Color(0xEC5FA8), 'w', Color.WHITE);
    private static final Map<Character, Color> HEART_EMPTY = Map.of(
            'k', INK, 'p', new Color(0xB9A9C9), 'w', new Color(0xB9A9C9));

    private enum State { TITLE, PLAYING, LEVEL_COMPLETE, GAME_OVER }

    private final Input input = new Input();
    private final Background background = new Background();

    // Double buffering between the loop thread and Swing
    private BufferedImage frontFrame = newFrame();
    private BufferedImage backFrame = newFrame();
    private final Object frameLock = new Object();
    private volatile boolean running;

    // Game state (only touched by the loop thread)
    private State state = State.TITLE;
    private int stateTimer;
    private int tick;
    private Level level;
    private Player player;
    private int lives;
    private int candiesCollected;
    private double camX;
    private int respawnTimer;
    private int bannerTimer;
    private String toastText = "";
    private int toastTimer;

    public GamePanel() {
        setPreferredSize(windowSize());
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(input);
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                input.releaseAll();
            }
        });
        loadLevel(1); // also used as the title screen's backdrop
    }

    // ---------------------------------------------------------------- loop

    public void start() {
        if (running) {
            return;
        }
        running = true;
        Thread loopThread = new Thread(this, "game-loop");
        loopThread.setDaemon(true);
        loopThread.start();
    }

    @Override
    public void run() {
        final long frameNs = 1_000_000_000L / FPS;
        long next = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            if (now - next > frameNs * 5) {
                next = now; // we fell far behind (e.g. window was dragged): don't fast-forward
            }
            boolean updated = false;
            while (now >= next) {
                update();
                next += frameNs;
                updated = true;
            }
            if (updated) {
                renderFrame();
                repaint();
                Toolkit.getDefaultToolkit().sync();
            }
            long sleepNs = next - System.nanoTime();
            if (sleepNs > 0) {
                try {
                    Thread.sleep(sleepNs / 1_000_000, (int) (sleepNs % 1_000_000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    // ---------------------------------------------------------------- update

    private void update() {
        tick++;
        stateTimer++;
        if (toastTimer > 0) {
            toastTimer--;
        }
        switch (state) {
            case TITLE -> updateTitle();
            case PLAYING -> updatePlaying();
            case LEVEL_COMPLETE -> {
                level.update();
                if (stateTimer > 60 && input.consumeConfirm()) {
                    loadLevel(1);
                    setState(State.TITLE);
                }
            }
            case GAME_OVER -> {
                level.update();
                if (stateTimer > 30 && input.consumeConfirm()) {
                    startGame();
                }
            }
        }
    }

    private void updateTitle() {
        level.update();
        double maxCam = level.pixelWidth() - VIEW_W;
        camX = (0.5 - 0.5 * Math.cos(tick * 0.0025)) * maxCam; // slow pan back and forth
        boolean confirm = input.consumeConfirm();
        boolean jump = input.consumeJump();
        if (confirm || jump) {
            startGame();
        }
    }

    private void startGame() {
        lives = START_LIVES;
        loadLevel(1);
        setState(State.PLAYING);
        bannerTimer = BANNER_FRAMES;
    }

    private void loadLevel(int number) {
        level = Level.create(number);
        player = new Player(level.spawnX(), level.spawnY());
        candiesCollected = 0;
        respawnTimer = 0;
        toastTimer = 0;
        camX = cameraTarget();
    }

    private void setState(State newState) {
        state = newState;
        stateTimer = 0;
        input.clearPresses();
    }

    private void updatePlaying() {
        level.update();
        if (bannerTimer > 0) {
            bannerTimer--;
        }

        player.animate();
        if (respawnTimer > 0) { // fading out and back in after a fall or a hit
            respawnTimer--;
            if (respawnTimer == RESPAWN_FRAMES / 2) {
                player.respawn(level.spawnX(), level.spawnY());
                camX = cameraTarget();
                input.clearPresses();
            }
            return;
        }

        player.update(input, level);
        Rectangle body = player.bounds();

        for (Collectible c : level.collectibles()) {
            if (c.tryCollect(body)) {
                candiesCollected++;
            }
        }

        Door door = level.door();
        if (door.isLocked() && candiesCollected == level.collectibles().size()) {
            door.unlock();
            toast("The door is open!");
        }
        if (body.intersects(door.bounds())) {
            if (!door.isLocked()) {
                setState(State.LEVEL_COMPLETE);
                return;
            }
            if (toastTimer == 0) {
                toast("Collect every candy to open the door");
            }
        }

        if (!player.isInvulnerable()) {
            for (Enemy e : level.enemies()) {
                if (body.intersects(e.bounds())) {
                    player.hurt();
                    loseLife();
                    return;
                }
            }
        }

        if (player.hasFallenOut(level)) {
            loseLife();
        }

        camX += (cameraTarget() - camX) * 0.1;
    }

    private void loseLife() {
        lives--;
        if (lives <= 0) {
            setState(State.GAME_OVER);
        } else {
            respawnTimer = RESPAWN_FRAMES;
        }
    }

    private double cameraTarget() {
        double lookAhead = player.isFacingRight() ? 48 : -48;
        double target = player.centerX() - VIEW_W / 2.0 + lookAhead;
        return Math.max(0, Math.min(target, level.pixelWidth() - VIEW_W));
    }

    private void toast(String text) {
        toastText = text;
        toastTimer = 150;
    }

    // ---------------------------------------------------------------- render

    private void renderFrame() {
        Graphics2D g = backFrame.createGraphics();
        render(g);
        g.dispose();
        synchronized (frameLock) {
            BufferedImage done = backFrame;
            backFrame = frontFrame;
            frontFrame = done;
        }
    }

    /** Draws one whole frame at 1024x768. */
    void render(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        boolean showPlayer = state == State.PLAYING || state == State.GAME_OVER;
        drawWorld(g, showPlayer);

        switch (state) {
            case TITLE -> drawTitle(g);
            case PLAYING -> {
                drawHud(g);
                drawBanner(g);
                drawToast(g);
                drawRespawnFade(g);
            }
            case LEVEL_COMPLETE -> {
                drawHud(g);
                drawLevelComplete(g);
            }
            case GAME_OVER -> drawGameOver(g);
        }
    }

    private void drawWorld(Graphics2D g, boolean showPlayer) {
        background.draw(g, camX, tick);
        Graphics2D w = (Graphics2D) g.create();
        w.translate(-(int) Math.round(camX), 0);
        level.draw(w);
        if (showPlayer) {
            player.draw(w);
        }
        w.dispose();
    }

    private void drawHud(Graphics2D g) {
        Graphics2D h = smooth(g);
        int total = level.collectibles().size();

        // Candies + lives
        panel(h, 14, 12, 248, 46);
        h.drawImage(Assets.CANDY, 12, 11, null);
        h.setFont(HUD_FONT);
        shadowText(h, candiesCollected + " / " + total, 58, 42);
        for (int i = 0; i < START_LIVES; i++) {
            PixelArt.draw(h, HEART, i < lives ? HEART_FULL : HEART_EMPTY, 140 + i * 38, 25, 2);
        }

        // Level name
        String label = "Level " + level.number() + "  ·  " + level.name();
        FontMetrics fm = h.getFontMetrics(HUD_FONT);
        int w = fm.stringWidth(label) + 36;
        panel(h, VIEW_W - 14 - w, 12, w, 46);
        shadowText(h, label, VIEW_W - 14 - w + 18, 42);
        h.dispose();
    }

    private void drawBanner(Graphics2D g) {
        if (bannerTimer <= 0) {
            return;
        }
        float t = BANNER_FRAMES - bannerTimer;
        float alpha = Math.min(1f, Math.min(t / 20f, bannerTimer / 30f));
        Graphics2D h = smooth(g);
        h.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        outlinedText(h, "LEVEL " + level.number(), BODY_FONT, VIEW_W / 2, 238, SOFT_WHITE, INK, 6);
        outlinedText(h, level.name(), BIG_FONT, VIEW_W / 2, 300, new Color(0xFFD1EC), INK, 9);
        h.dispose();
    }

    private void drawToast(Graphics2D g) {
        if (toastTimer <= 0) {
            return;
        }
        Graphics2D h = smooth(g);
        float alpha = Math.min(1f, toastTimer / 20f);
        h.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        h.setFont(BODY_FONT);
        FontMetrics fm = h.getFontMetrics();
        int w = fm.stringWidth(toastText) + 44;
        panel(h, (VIEW_W - w) / 2, 76, w, 44);
        shadowText(h, toastText, (VIEW_W - w) / 2 + 22, 106);
        h.dispose();
    }

    private void drawRespawnFade(Graphics2D g) {
        if (respawnTimer <= 0) {
            return;
        }
        int half = RESPAWN_FRAMES / 2;
        float a = respawnTimer > half ? (RESPAWN_FRAMES - respawnTimer) / (float) half : respawnTimer / (float) half;
        g.setColor(new Color(255, 240, 248, (int) (255 * Math.min(1f, a))));
        g.fillRect(0, 0, VIEW_W, VIEW_H);
    }

    private void drawTitle(Graphics2D g) {
        g.setColor(new Color(255, 245, 252, 70));
        g.fillRect(0, 0, VIEW_W, VIEW_H);
        Graphics2D h = smooth(g);
        int bob = (int) Math.round(Math.sin(tick * 0.04) * 4);
        outlinedText(h, "Sweet Somnium", TITLE_FONT, VIEW_W / 2, 270 + bob, new Color(0xFFD1EC), INK, 12);
        if ((tick / 30) % 2 == 0) {
            outlinedText(h, "Press ENTER to start", BODY_FONT, VIEW_W / 2, 400, SOFT_WHITE, INK, 6);
        }
        outlinedText(h, "← →  or  A D  to move      SPACE  ↑  or  W  to jump",
                HUD_FONT, VIEW_W / 2, 460, SOFT_WHITE, INK, 5);
        h.dispose();
    }

    private void drawLevelComplete(Graphics2D g) {
        float a = Math.min(1f, stateTimer / 40f);
        g.setColor(new Color(255, 240, 248, (int) (225 * a)));
        g.fillRect(0, 0, VIEW_W, VIEW_H);
        if (stateTimer < 30) {
            return;
        }
        Graphics2D h = smooth(g);
        outlinedText(h, level.name() + " cleared!", BIG_FONT, VIEW_W / 2, 300, new Color(0xFFD1EC), INK, 9);
        outlinedText(h, "Level 2 isn't built yet.", BODY_FONT, VIEW_W / 2, 380, SOFT_WHITE, INK, 6);
        if (stateTimer > 60 && (tick / 30) % 2 == 0) {
            outlinedText(h, "Press ENTER to return to the title", BODY_FONT, VIEW_W / 2, 430, SOFT_WHITE, INK, 6);
        }
        h.dispose();
    }

    private void drawGameOver(Graphics2D g) {
        float a = Math.min(1f, stateTimer / 30f);
        g.setColor(new Color(40, 16, 46, (int) (190 * a)));
        g.fillRect(0, 0, VIEW_W, VIEW_H);
        Graphics2D h = smooth(g);
        outlinedText(h, "GAME OVER", BIG_FONT, VIEW_W / 2, 320, SOFT_WHITE, INK, 9);
        if (stateTimer > 30 && (tick / 30) % 2 == 0) {
            outlinedText(h, "Press ENTER to try again", BODY_FONT, VIEW_W / 2, 400, SOFT_WHITE, INK, 6);
        }
        h.dispose();
    }

    // ---------------------------------------------------------------- drawing helpers

    /** A copy of g with anti-aliasing on, for text and UI. */
    private static Graphics2D smooth(Graphics2D g) {
        Graphics2D h = (Graphics2D) g.create();
        h.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        h.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return h;
    }

    private static void panel(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(255, 245, 252, 190));
        g.fillRoundRect(x, y, w, h, 20, 20);
        g.setColor(INK);
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(x, y, w, h, 20, 20);
    }

    private static void shadowText(Graphics2D g, String text, int x, int y) {
        g.setColor(new Color(0xF2B5DA));
        g.drawString(text, x + 1, y + 2);
        g.setColor(INK);
        g.drawString(text, x, y);
    }

    /** Text with a thick outline, centred on cx. */
    private static void outlinedText(Graphics2D g, String text, Font font, int cx, int baseline,
                                     Color fill, Color outline, float thickness) {
        TextLayout layout = new TextLayout(text, font, g.getFontRenderContext());
        Rectangle2D b = layout.getBounds();
        Shape shape = layout.getOutline(AffineTransform.getTranslateInstance(cx - b.getWidth() / 2 - b.getX(), baseline));
        g.setStroke(new BasicStroke(thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(outline);
        g.draw(shape);
        g.setColor(fill);
        g.fill(shape);
    }

    // ---------------------------------------------------------------- Swing side

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        double scale = Math.min(getWidth() / (double) VIEW_W, getHeight() / (double) VIEW_H);
        int w = (int) Math.round(VIEW_W * scale);
        int h = (int) Math.round(VIEW_H * scale);
        int ox = (getWidth() - w) / 2;
        int oy = (getHeight() - h) / 2;

        // Crisp pixels when the final size is a whole multiple, smooth otherwise (e.g. 125% Windows scaling)
        double deviceScale = g.getTransform().getScaleX() * scale;
        boolean wholeNumber = Math.abs(deviceScale - Math.rint(deviceScale)) < 0.01;
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, wholeNumber
                ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        synchronized (frameLock) {
            g.drawImage(frontFrame, ox, oy, w, h, null);
        }
    }

    /** 1024x768, or smaller if the screen can't fit that (keeps the 4:3 shape). */
    private static Dimension windowSize() {
        if (GraphicsEnvironment.isHeadless()) {
            return new Dimension(VIEW_W, VIEW_H);
        }
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        double scale = Math.min(1.0, Math.min((screen.width - 32) / (double) VIEW_W,
                                              (screen.height - 48) / (double) VIEW_H));
        return new Dimension((int) (VIEW_W * scale), (int) (VIEW_H * scale));
    }

    private static BufferedImage newFrame() {
        return new BufferedImage(VIEW_W, VIEW_H, BufferedImage.TYPE_INT_RGB);
    }
}
