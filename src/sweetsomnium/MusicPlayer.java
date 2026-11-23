package sweetsomnium;

import java.io.BufferedInputStream;
import java.io.InputStream;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;

/**
 * Plays the background music and the short jingles.
 *
 * The music is WAV files in src/assets/music, made by tools/MusicGenerator.java.
 * Every track is loaded once at startup into an array of clips, so a track is
 * found by its number: {@code clips[LEVEL_2]} is Level 2's music.
 *
 * Only one track plays at a time. Switching tracks fades the old one out over a
 * few frames, so the change isn't abrupt. If the computer has no sound device,
 * the game simply runs without music.
 */
final class MusicPlayer {

    // Track numbers (also the index into the arrays below)
    static final int TITLE = 0;
    static final int LEVEL_1 = 1;
    static final int LEVEL_2 = 2;
    static final int LEVEL_3 = 3;
    static final int COMPLETE = 4;
    static final int GAME_OVER = 5;
    static final int ENDING = 6;

    private static final String[] FILES = {"title", "level1", "level2", "level3", "complete", "gameover", "ending"};
    private static final boolean[] LOOPS = {true, true, true, true, false, false, true};

    private static final float VOLUME = 0.8f;   // 1 = full volume
    private static final int FADE_FRAMES = 30;  // half a second

    private final Clip[] clips = new Clip[FILES.length];
    private int current = -1;     // the track playing now, -1 = none
    private Clip fadingOut;       // the previous track, while it fades out
    private int fadeTimer;

    MusicPlayer() {
        for (int i = 0; i < FILES.length; i++) {
            clips[i] = loadClip("/assets/music/" + FILES[i] + ".wav");
        }
    }

    /** The music for level 1, 2 or 3. */
    static int forLevel(int levelNumber) {
        return LEVEL_1 + levelNumber - 1;
    }

    /**
     * Starts a track from the beginning, fading out whatever was playing.
     * Asking for the track that's already playing does nothing.
     */
    void play(int track) {
        if (track == current) {
            return;
        }
        fadeOutCurrent();
        current = track;
        Clip clip = clips[track];
        if (clip == null) {
            return;
        }
        if (clip == fadingOut) { // coming straight back to a track that was fading out
            fadingOut = null;
        }
        clip.stop();
        clip.setFramePosition(0);
        setVolume(clip, VOLUME);
        if (LOOPS[track]) {
            clip.loop(Clip.LOOP_CONTINUOUSLY);
        } else {
            clip.start();
        }
    }

    /** Fades out whatever is playing. */
    void stop() {
        fadeOutCurrent();
        current = -1;
    }

    /** Called once per frame by the game loop: moves the fade-out along. */
    void update() {
        if (fadingOut == null) {
            return;
        }
        fadeTimer--;
        if (fadeTimer <= 0) {
            fadingOut.stop();
            fadingOut = null;
        } else {
            setVolume(fadingOut, VOLUME * fadeTimer / FADE_FRAMES);
        }
    }

    private void fadeOutCurrent() {
        if (fadingOut != null) { // a fade was already going: just cut that one off
            fadingOut.stop();
            fadingOut = null;
        }
        if (current >= 0 && clips[current] != null && clips[current].isRunning()) {
            fadingOut = clips[current];
            fadeTimer = FADE_FRAMES;
        }
    }

    /** Sets a clip's loudness, 0 to 1. Java Sound measures it in decibels, so convert. */
    static void setVolume(Clip clip, float volume) {
        if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            return;
        }
        FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        float db = (float) (20 * Math.log10(Math.max(volume, 0.0001f)));
        gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db)));
    }

    /** Loads one WAV into memory, or returns null (and the game stays quiet) if that isn't possible. */
    static Clip loadClip(String path) {
        try (InputStream raw = MusicPlayer.class.getResourceAsStream(path)) {
            if (raw == null) {
                System.err.println("Missing sound " + path);
                return null;
            }
            try (AudioInputStream audio = AudioSystem.getAudioInputStream(new BufferedInputStream(raw))) {
                Clip clip = AudioSystem.getClip();
                clip.open(audio);
                return clip;
            }
        } catch (Exception e) { // no sound device, unsupported format, ...
            System.err.println("Sound disabled for " + path + ": " + e);
            return null;
        }
    }
}
