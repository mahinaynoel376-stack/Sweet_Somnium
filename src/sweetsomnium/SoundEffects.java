package sweetsomnium;

import javax.sound.sampled.Clip;

/**
 * Short sound effects: picking up a candy, getting hit, and falling into a pit.
 *
 * Each effect has one version per level, and they get darker as the game goes on:
 * a sweet chime for candy, a sour plastic pop for capsules, a rattle and a warped
 * chord for pills. The WAVs are in src/assets/sfx, made by tools/MusicGenerator.java.
 *
 * The clips are in one array, three per effect, in level order:
 * <pre>
 *   index:  0        1        2        3      4      5      6      7      8
 *           pickup1  pickup2  pickup3  hurt1  hurt2  hurt3  fall1  fall2  fall3
 * </pre>
 * so the clip for an effect on a level is {@code clips[effect + level - 1]}.
 */
final class SoundEffects {

    // Where each effect's three versions start in the array
    static final int PICKUP = 0;
    static final int HURT = 3;
    static final int FALL = 6;

    private static final String[] FILES = {
        "pickup1", "pickup2", "pickup3",
        "hurt1", "hurt2", "hurt3",
        "fall1", "fall2", "fall3",
    };
    private static final float VOLUME = 0.9f;

    private final Clip[] clips = new Clip[FILES.length];

    SoundEffects() {
        for (int i = 0; i < FILES.length; i++) {
            clips[i] = MusicPlayer.loadClip("/assets/sfx/" + FILES[i] + ".wav");
        }
    }

    /** Plays an effect (PICKUP, HURT or FALL) in its version for level 1, 2 or 3. */
    void play(int effect, int levelNumber) {
        Clip clip = clips[effect + levelNumber - 1];
        if (clip == null) {
            return;
        }
        clip.stop();             // if it's still playing from last time, start it over
        clip.setFramePosition(0);
        MusicPlayer.setVolume(clip, VOLUME);
        clip.start();
    }
}
