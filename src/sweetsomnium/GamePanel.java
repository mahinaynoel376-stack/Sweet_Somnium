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
import java.util.Random;

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
    // Palettes are arrays indexed by the pattern character: HEART_FULL['p'] is the pink fill.
    private static final Color[] HEART_FULL = PixelArt.palette("kpw",
            INK, new Color(0xEC5FA8), Color.WHITE);
    private static final Color[] HEART_EMPTY = PixelArt.palette("kpw",
            INK, new Color(0xB9A9C9), new Color(0xB9A9C9));

    private static final int TOAST_FRAMES = 150;    // how long one message stays up
    private static final int TOAST_MIN_FRAMES = 60; // shown at least this long before the next one may cut in
    private static final int TOAST_FADE = 20;

    // Death effects get stronger and darker level by level. Each array is looked up with [level number - 1].
    private static final Color[][] BURST_COLORS = {
        {Color.WHITE, new Color(0xFFD1EC), new Color(0xF6A8D2)},           // level 1: pink marshmallow fluff
        {new Color(0xE8E6D8), new Color(0xC9CDB4), new Color(0xA6AD8E)},   // level 2: washed-out, sour
        {new Color(0x5A0F14), new Color(0x1A0A0C), new Color(0x8C8A8E)},   // level 3: dark red, black and ash
    };
    private static final double[] BURST_GRAVITY = {0.35, 0.30, -0.06};    // level 3's bits drift up like smoke
    private static final int[] BURST_COUNT = {36, 48, 64};
    private static final Color[] FLASH_COLORS = {Color.WHITE, new Color(214, 222, 160), new Color(120, 0, 10)};
    private static final int[] SHAKE_FRAMES = {12, 16, 26};
    private static final int[] SHAKE_PIXELS = {4, 6, 10};
    private static final int FLASH_FRAMES = 18;

    // Which screen the game is on
    private static final int STATE_TITLE = 0;
    private static final int STATE_PLAYING = 1;
    private static final int STATE_LEVEL_COMPLETE = 2;
    private static final int STATE_GAME_OVER = 3;
    private static final int STATE_ENDING = 4;

    private final Input input = new Input();
    private final MusicPlayer music = new MusicPlayer();
    private final SoundEffects sfx = new SoundEffects();
    private final Particles particles = new Particles();
    private final Random shakeRandom = new Random(5);
    private Background background;

    // Double buffering between the loop thread and Swing
    private BufferedImage frontFrame = newFrame();
    private BufferedImage backFrame = newFrame();
    private final Object frameLock = new Object();
    private volatile boolean running;

    // Game state (only touched by the loop thread)
    private int state = STATE_TITLE;
    private int stateTimer;
    private int tick;
    private Level level;
    private Player player;
    private final HeartStack hearts = new HeartStack(START_LIVES); // the player's health
    private int candiesCollected;
    private double camX;
    private int respawnTimer;
    private int bannerTimer;
    private final MessageQueue<String> toasts = new MessageQueue<>(); // messages waiting their turn
    private String toastText = "";   // the message on screen right now
    private int toastTimer;
    private int shakeTimer;     // screen shake after a death
    private int shakeFrames;
    private int shakePixels;
    private int shakeX;
    private int shakeY;
    private int flashTimer;     // colour flash after a hit
    private Color flashColor = Color.WHITE;

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
        playMusicFor(state);
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
        updateToasts();
        updateEffects();
        music.update();
        switch (state) {
            case STATE_TITLE:
                updateTitle();
                break;
            case STATE_PLAYING:
                updatePlaying();
                break;
            case STATE_LEVEL_COMPLETE:
                level.update(null);
                if (stateTimer > 60 && input.consumeConfirm()) {
                    startLevel(level.number() + 1);
                }
                break;
            case STATE_GAME_OVER:
                level.update(null);
                if (stateTimer > 30 && input.consumeConfirm()) {
                    startLevel(level.number()); // try this level again
                }
                break;
            case STATE_ENDING:
                if (stateTimer > 300 && input.consumeConfirm()) {
                    loadLevel(1);
                    setState(STATE_TITLE);
                }
                break;
            default:
                break;
        }
    }

    private void updateTitle() {
        level.update(null);
        double maxCam = level.pixelWidth() - VIEW_W;
        camX = (0.5 - 0.5 * Math.cos(tick * 0.0025)) * maxCam; // slow pan back and forth
        boolean confirm = input.consumeConfirm();
        boolean jump = input.consumeJump();
        if (confirm || jump) {
            startGame();
        }
    }

    private void startGame() {
        startLevel(1);
    }

    /** Every level starts with full lives: the heart stack is emptied and 3 hearts are pushed back on. */
    private void startLevel(int number) {
        hearts.clear();
        for (int heart = 1; heart <= START_LIVES; heart++) {
            hearts.push(heart);
        }
        loadLevel(number);
        setState(STATE_PLAYING);
        bannerTimer = BANNER_FRAMES;
    }

    private void loadLevel(int number) {
        level = Level.create(number);
        background = new Background(level.theme());
        player = new Player(level.spawnX(), level.spawnY(), level.theme().playerPallor);
        candiesCollected = 0;
        respawnTimer = 0;
        toastTimer = 0;
        toasts.clear();
        particles.clear();
        shakeTimer = 0;
        shakeX = 0;
        shakeY = 0;
        flashTimer = 0;
        camX = cameraTarget();
    }

    private void setState(int newState) {
        state = newState;
        stateTimer = 0;
        input.clearPresses();
        playMusicFor(newState);
    }

    /** Each screen has its own music; levels get their own track, and clearing or failing a level plays a jingle. */
    private void playMusicFor(int screen) {
        switch (screen) {
            case STATE_TITLE:
                music.play(MusicPlayer.TITLE);
                break;
            case STATE_PLAYING:
                music.play(MusicPlayer.forLevel(level.number()));
                break;
            case STATE_LEVEL_COMPLETE:
                music.play(MusicPlayer.COMPLETE);
                break;
            case STATE_GAME_OVER:
                music.play(MusicPlayer.GAME_OVER);
                break;
            case STATE_ENDING:
                music.play(MusicPlayer.ENDING);
                break;
            default:
                break;
        }
    }

    private void updatePlaying() {
        level.update(respawnTimer > 0 ? null : player.bounds());
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

        // Walk the candy linked list, node by node, and pick up any candy the player touches
        for (CandyList.Node n = level.candies().first(); n != null; n = n.next) {
            if (n.candy.tryCollect(body)) {
                candiesCollected++;
                sfx.play(SoundEffects.PICKUP, level.number()); // candy, capsule or pill: sweeter to scarier
                if (level.totalCandies() - candiesCollected == 1) {
                    toast("Only one candy left!");
                }
            }
        }

        Door door = level.door();
        if (door.isLocked() && level.candies().allCollected()) { // linear search for any candy left
            door.unlock();
            toast("The door is open!");
        }
        if (body.intersects(door.bounds())) {
            if (!door.isLocked()) {
                setState(level.number() == Level.LAST_LEVEL ? STATE_ENDING : STATE_LEVEL_COMPLETE);
                return;
            }
            toast("Collect every candy to open the door");
        }

        if (!player.isInvulnerable() && touchesDanger(body)) {
            player.hurt();
            deathEffects(true);
            loseLife();
            return;
        }

        if (player.hasFallenOut(level)) {
            deathEffects(false);
            loseLife();
        }

        camX += (cameraTarget() - camX) * 0.1;
    }

    private boolean touchesDanger(Rectangle body) {
        for (Enemy e : level.enemies()) {
            if (body.intersects(e.bounds())) {
                return true;
            }
        }
        for (Spike s : level.spikes()) {
            if (body.intersects(s.bounds())) {
                return true;
            }
        }
        for (Monster m : level.monsters()) {
            if (body.intersects(m.bounds())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sound, screen shake, a colour flash and a burst of fluff when the player dies.
     * Each level's version is stronger and darker than the last.
     *
     * @param hit true = touched an enemy, spike or monster; false = fell into a pit
     */
    private void deathEffects(boolean hit) {
        int i = level.number() - 1;
        if (hit) {
            sfx.play(SoundEffects.HURT, level.number());
            Rectangle b = player.bounds();
            particles.burst(b.getCenterX(), b.getCenterY(), BURST_COUNT[i], BURST_COLORS[i], 3.5 + i, BURST_GRAVITY[i]);
            flashColor = FLASH_COLORS[i];
            flashTimer = FLASH_FRAMES;
            shake(SHAKE_FRAMES[i], SHAKE_PIXELS[i]);
        } else {
            sfx.play(SoundEffects.FALL, level.number());
            shake(SHAKE_FRAMES[i] / 2, SHAKE_PIXELS[i] / 2 + 1); // a smaller jolt: he's already off screen
        }
    }

    private void shake(int frames, int pixels) {
        shakeFrames = frames;
        shakeTimer = frames;
        shakePixels = pixels;
    }

    /** Runs every frame on every screen, so effects finish even after the game is over. */
    private void updateEffects() {
        particles.update();
        if (flashTimer > 0) {
            flashTimer--;
        }
        if (shakeTimer > 0) {
            shakeTimer--;
            int strength = (shakePixels * shakeTimer + shakeFrames - 1) / shakeFrames; // dies down over time
            shakeX = shakeRandom.nextInt(2 * strength + 1) - strength;
            shakeY = shakeRandom.nextInt(2 * strength + 1) - strength;
        } else {
            shakeX = 0;
            shakeY = 0;
        }
    }

    /** Pops the top heart off the stack. No hearts left means Game Over. */
    private void loseLife() {
        hearts.pop();
        if (hearts.isEmpty()) {
            setState(STATE_GAME_OVER);
        } else {
            respawnTimer = RESPAWN_FRAMES;
        }
    }

    private double cameraTarget() {
        double lookAhead = player.isFacingRight() ? 48 : -48;
        double target = player.centerX() - VIEW_W / 2.0 + lookAhead;
        return Math.max(0, Math.min(target, level.pixelWidth() - VIEW_W));
    }

    /**
     * Queues a message. Messages are shown one at a time in the order they were queued;
     * a message that is already on screen or already waiting is not queued twice.
     */
    private void toast(String text) {
        boolean showing = toastTimer > 0 && toastText.equals(text);
        if (!showing && !toasts.contains(text)) {
            toasts.enqueue(text);
        }
    }

    /** Counts down the current message and brings in the next one from the front of the queue. */
    private void updateToasts() {
        if (toastTimer > 0) {
            toastTimer--;
            boolean someoneWaiting = !toasts.isEmpty();
            boolean shownLongEnough = TOAST_FRAMES - toastTimer >= TOAST_MIN_FRAMES;
            if (someoneWaiting && shownLongEnough && toastTimer > TOAST_FADE) {
                toastTimer = TOAST_FADE; // start fading out early so the queue keeps moving
            }
        }
        if (toastTimer == 0 && !toasts.isEmpty()) {
            toastText = toasts.dequeue();
            toastTimer = TOAST_FRAMES;
        }
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

        boolean showPlayer = state == STATE_PLAYING || state == STATE_GAME_OVER;
        drawWorld(g, showPlayer);
        drawFlash(g);

        switch (state) {
            case STATE_TITLE:
                drawTitle(g);
                break;
            case STATE_PLAYING:
                drawHud(g);
                drawBanner(g);
                drawToast(g);
                drawRespawnFade(g);
                break;
            case STATE_LEVEL_COMPLETE:
                drawHud(g);
                drawLevelComplete(g);
                break;
            case STATE_GAME_OVER:
                drawGameOver(g);
                break;
            case STATE_ENDING:
                drawEnding(g);
                break;
            default:
                break;
        }
    }

    private void drawWorld(Graphics2D g, boolean showPlayer) {
        background.draw(g, camX, tick);
        Graphics2D w = (Graphics2D) g.create();
        w.translate(-(int) Math.round(camX) + shakeX, shakeY); // the sky stays still; the world shakes
        level.draw(w);
        if (showPlayer) {
            player.draw(w);
        }
        particles.draw(w);
        w.dispose();
    }

    private void drawFlash(Graphics2D g) {
        if (flashTimer <= 0) {
            return;
        }
        int alpha = 150 * flashTimer / FLASH_FRAMES;
        g.setColor(new Color(flashColor.getRed(), flashColor.getGreen(), flashColor.getBlue(), alpha));
        g.fillRect(0, 0, VIEW_W, VIEW_H);
    }

    private void drawHud(Graphics2D g) {
        Graphics2D h = smooth(g);
        int total = level.totalCandies();

        // Candies + lives
        panel(h, 14, 12, 248, 46);
        BufferedImage icon = level.theme().collectible;
        h.drawImage(icon, 12 + (48 - icon.getWidth()) / 2, 11 + (48 - icon.getHeight()) / 2, null);
        h.setFont(HUD_FONT);
        shadowText(h, candiesCollected + " / " + total, 58, 42);
        for (int i = 0; i < START_LIVES; i++) { // hearts still on the stack are full, popped ones are grey
            PixelArt.draw(h, HEART, i < hearts.size() ? HEART_FULL : HEART_EMPTY, 140 + i * 38, 25, 2);
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
        float alpha = Math.min(1f, toastTimer / (float) TOAST_FADE);
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
        int alpha = (int) (255 * Math.min(1f, a));
        Color fade; // the fade gets darker as the world does
        if (level.number() == 1) {
            fade = new Color(255, 240, 248, alpha);
        } else if (level.number() == 2) {
            fade = new Color(190, 192, 204, alpha);
        } else {
            fade = new Color(0, 0, 0, alpha);
        }
        g.setColor(fade);
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
        if (stateTimer > 60 && (tick / 30) % 2 == 0) {
            outlinedText(h, "Press ENTER to continue", BODY_FONT, VIEW_W / 2, 400, SOFT_WHITE, INK, 6);
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

    /** After level 3: quiet, dark, and left for the player to read. */
    private void drawEnding(Graphics2D g) {
        float dark = Math.min(1f, stateTimer / 90f);
        g.setColor(new Color(0, 0, 0, (int) (255 * dark)));
        g.fillRect(0, 0, VIEW_W, VIEW_H);
        if (stateTimer < 120) {
            return;
        }
        float a = Math.min(1f, (stateTimer - 120) / 120f);
        Graphics2D h = smooth(g);
        h.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
        player.drawStanding(h, VIEW_W / 2, VIEW_H / 2 + 20);
        h.setFont(BODY_FONT);
        h.setColor(new Color(0xB8B0B4));
        String line = "Sweet dreams.";
        h.drawString(line, (VIEW_W - h.getFontMetrics().stringWidth(line)) / 2, VIEW_H / 2 + 90);
        if (stateTimer > 300) {
            h.setFont(HUD_FONT);
            h.setColor(new Color(0x5A5458));
            String hint = "Press ENTER";
            h.drawString(hint, (VIEW_W - h.getFontMetrics().stringWidth(hint)) / 2, VIEW_H - 60);
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
