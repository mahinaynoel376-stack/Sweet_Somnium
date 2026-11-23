import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.BufferedOutputStream;
import java.util.Random;

/**
 * Composes and renders Sweet Somnium's music as WAV files (22050 Hz, 16-bit, mono).
 *
 * Run from the project folder:   java tools/MusicGenerator.java
 * It writes into src/assets/music/. This tool is not part of the game; the game only plays the WAVs.
 *
 * Every track is built from the same little melody (the "theme"), which changes as the world does:
 *   title     - the theme as a music-box lullaby
 *   level1    - Candy Land: bright, bouncy, major key
 *   level2    - Sour Turn: the theme in a minor key, slightly out of tune
 *   level3    - Withdrawal: broken music-box fragments over a drone and a heartbeat
 *   ending    - the lullaby again, very slow and warped
 *   complete  - short jingle when a level is cleared
 *   gameover  - short jingle when the last heart is lost
 *
 * Songs are stored as arrays: each note is {midi note number, length in beats}, and -1 is a rest.
 */
public class MusicGenerator {

    static final int SR = 22050;

    // ------------------------------------------------------------------ the theme

    /** First half of the theme (8 bars of 4 beats), in C major. */
    static final double[][] THEME_A = {
        {76, 1}, {79, 1}, {84, 1.5}, {83, 0.5},
        {81, 1}, {79, 1}, {76, 2},
        {77, 1}, {81, 1}, {79, 1}, {76, 1},
        {74, 3}, {-1, 1},
        {76, 1}, {79, 1}, {84, 1.5}, {86, 0.5},
        {88, 1}, {86, 1}, {84, 1}, {81, 1},
        {79, 1.5}, {77, 0.5}, {76, 1}, {74, 1},
        {72, 3}, {-1, 1},
    };

    /** Second half of the theme (8 more bars), a little higher. */
    static final double[][] THEME_B = {
        {81, 0.5}, {84, 0.5}, {89, 1}, {88, 1}, {84, 1},
        {88, 1.5}, {86, 0.5}, {84, 2},
        {86, 0.5}, {89, 0.5}, {93, 1}, {91, 1}, {89, 1},
        {88, 1}, {86, 1}, {83, 2},
        {81, 1}, {84, 1}, {89, 1}, {88, 1},
        {86, 1}, {84, 1}, {83, 1}, {79, 1},
        {81, 1}, {84, 1}, {86, 1}, {83, 1},
        {84, 3}, {-1, 1},
    };

    // One chord per bar, written as 3 notes around middle C. The first note is the chord's root.
    static final int[] C = {60, 64, 67};
    static final int[] AM = {57, 60, 64};
    static final int[] F = {53, 57, 60};
    static final int[] G = {55, 59, 62};
    static final int[] DM = {62, 65, 69};
    static final int[] EM = {64, 67, 71};
    static final int[][] CHORDS_A = {C, AM, F, G, C, AM, G, C};
    static final int[][] CHORDS_B = {F, C, DM, G, F, EM, G, C};

    // ------------------------------------------------------------------ main

    public static void main(String[] args) throws IOException {
        File dir = new File(args.length > 0 ? args[0] : "src/assets/music");
        dir.mkdirs();
        write(title(), new File(dir, "title.wav"), 0.70);
        write(level1(), new File(dir, "level1.wav"), 0.89);
        write(level2(), new File(dir, "level2.wav"), 0.78);
        write(level3(), new File(dir, "level3.wav"), 0.80);
        write(ending(), new File(dir, "ending.wav"), 0.62);
        write(complete(), new File(dir, "complete.wav"), 0.80);
        write(gameOver(), new File(dir, "gameover.wav"), 0.78);
        System.out.println("Wrote 7 tracks to " + dir.getPath());
    }

    // ------------------------------------------------------------------ the tracks

    /** Title: the theme on a music box, with soft chords. 76 BPM, loops. */
    static Track title() {
        double bpm = 76;
        Track t = new Track(32 * 60 / bpm, true, 0.35);
        Inst box = Inst.musicBox(0.30, 0.45);
        Inst pad = Inst.pad(0.045, 0.5);
        Inst bass = new Inst(SINE, 0.12, 0.3);
        bass.attack = 0.08;
        bass.release = 0.6;
        melody(t, box, THEME_A, 0, bpm, 0, null);
        for (int bar = 0; bar < 8; bar++) {
            double start = beats(bar * 4, bpm);
            chord(t, pad, CHORDS_A[bar], start, beats(4, bpm));
            note(t, bass, start, beats(3.5, bpm), bassRoot(CHORDS_A[bar]));
        }
        return t;
    }

    /** Level 1, Candy Land: bright and bouncy. 128 BPM, both halves of the theme, loops. */
    static Track level1() {
        double bpm = 128;
        Track t = new Track(64 * 60 / bpm, true, 0.18);
        Inst lead = new Inst(PULSE, 0.20, 0.10);
        lead.duty = 0.25;
        lead.cutoff = 2600;
        lead.attack = 0.006;
        lead.decay = 0.25;
        lead.sustain = 0.55;
        lead.release = 0.07;
        lead.vibrato = 0.10;
        Inst box = Inst.musicBox(0.11, 0.25);
        Inst bass = new Inst(TRIANGLE, 0.34, 0.0);
        bass.attack = 0.004;
        bass.decay = 0.18;
        bass.sustain = 0.25;
        bass.release = 0.05;
        Inst arp = new Inst(TRIANGLE, 0.07, 0.25);
        arp.attack = 0.003;
        arp.decay = 0.12;
        arp.sustain = 0.0;
        arp.release = 0.05;

        melody(t, lead, THEME_A, 0, bpm, 0, null);
        melody(t, lead, THEME_B, 32, bpm, 0, null);
        melody(t, box, THEME_B, 32, bpm, 12, null); // a music box joins an octave up in the second half
        int[] arpPattern = {0, 1, 2, 3, 2, 1, 2, 3}; // 3 = the root an octave up
        for (int bar = 0; bar < 16; bar++) {
            int[] chord = bar < 8 ? CHORDS_A[bar] : CHORDS_B[bar - 8];
            double barBeat = bar * 4;
            int root = bassRoot(chord);
            int[] bassLine = {root, root + 7, root + 12, root + 7}; // oom-pah
            for (int b = 0; b < 4; b++) {
                note(t, bass, beats(barBeat + b, bpm), beats(0.5, bpm), bassLine[b]);
            }
            for (int e = 0; e < 8; e++) {
                int i = arpPattern[e];
                int pitch = i == 3 ? chord[0] + 12 : chord[i];
                note(t, arp, beats(barBeat + e * 0.5, bpm), beats(0.4, bpm), pitch);
            }
            for (int b = 0; b < 4; b++) {
                double at = beats(barBeat + b, bpm);
                if (b % 2 == 0) {
                    kick(t, at, 0.42, 120, 45, 0.16);
                } else {
                    clap(t, at, 0.11);
                }
                hat(t, beats(barBeat + b + 0.5, bpm), 0.035);
            }
        }
        return t;
    }

    /** Level 2, Sour Turn: the theme in a minor key, detuned and wobbly. 100 BPM, loops. */
    static Track level2() {
        double bpm = 100;
        Track t = new Track(64 * 60 / bpm, true, 0.26);
        Inst lead1 = new Inst(SAW, 0.085, 0.15);
        lead1.cutoff = 1700;
        lead1.attack = 0.02;
        lead1.decay = 0.3;
        lead1.sustain = 0.6;
        lead1.release = 0.12;
        lead1.detune = -16;
        lead1.wowDepth = 12;
        lead1.wowRate = 0.37;
        Inst lead2 = lead1.copy();
        lead2.detune = 17;
        Inst sourBox = Inst.musicBox(0.10, 0.35);
        sourBox.detune = -32; // an out-of-tune toy
        sourBox.wowDepth = 10;
        sourBox.wowRate = 0.6;
        Inst bass = new Inst(TRIANGLE, 0.36, 0.0);
        bass.attack = 0.01;
        bass.decay = 0.5;
        bass.sustain = 0.4;
        bass.release = 0.15;
        Inst pad = Inst.pad(0.035, 0.35);
        pad.detune = 9;

        melody(t, lead1, THEME_A, 0, bpm, 0, CHORDS_A);
        melody(t, lead2, THEME_A, 0, bpm, 0, CHORDS_A);
        melody(t, lead1, THEME_B, 32, bpm, 0, CHORDS_B);
        melody(t, lead2, THEME_B, 32, bpm, 0, CHORDS_B);
        melody(t, sourBox, THEME_B, 32, bpm, -12, CHORDS_B);
        for (int bar = 0; bar < 16; bar++) {
            int[] chord = minorChord(bar < 8 ? CHORDS_A[bar] : CHORDS_B[bar - 8]);
            double barBeat = bar * 4;
            int root = bassRoot(chord);
            note(t, bass, beats(barBeat, bpm), beats(1.8, bpm), root);
            note(t, bass, beats(barBeat + 2, bpm), beats(1.8, bpm), root);
            chord(t, pad, chord, beats(barBeat, bpm), beats(4, bpm));
            kick(t, beats(barBeat, bpm), 0.40, 100, 42, 0.2);
            kick(t, beats(barBeat + 2.5, bpm), 0.30, 100, 42, 0.18);
            rim(t, beats(barBeat + 1, bpm), 0.09);
            rim(t, beats(barBeat + 3, bpm), 0.09);
        }
        return t;
    }

    /** Level 3, Withdrawal: pieces of the theme on a broken music box, a drone and a heartbeat. 56 BPM, loops. */
    static Track level3() {
        double bpm = 56;
        Track t = new Track(32 * 60 / bpm, true, 0.48);
        Inst box = Inst.musicBox(0.26, 0.55);
        box.detune = -38;
        box.wowDepth = 26;
        box.wowRate = 0.23;

        // Drone: a low C and G, plus a faint F# that rubs against them
        drone(t, 36, 0.13, 0.25);
        drone(t, 43, 0.08, 0.25);
        drone(t, 54, 0.022, 0.4);
        drone(t, 48, 0.035, 0.3);

        // The theme, an octave down, with notes missing here and there
        Random random = new Random(7);
        double beat = 0;
        int noteIndex = 0;
        for (double[] n : THEME_A) {
            int bar = (int) (beat / 4);
            boolean dropped = random.nextDouble() < 0.3 && noteIndex > 0;
            if (n[0] >= 0 && !dropped) {
                int pitch = toMinor((int) n[0], CHORDS_A[bar][0] % 12 == 7) - 12;
                note(t, box, beats(beat, bpm), beats(n[1], bpm), pitch);
            }
            beat += n[1];
            noteIndex++;
        }

        // Heartbeat: "lub-dub" on every beat (pitched high enough to be heard on laptop speakers)
        for (int b = 0; b < 32; b++) {
            double at = beats(b, bpm);
            kick(t, at, 0.34, 115, 58, 0.11);
            kick(t, at + 0.19, 0.22, 100, 52, 0.09);
        }
        return t;
    }

    /** Ending: the lullaby once more, very slow, warped and faraway. 50 BPM, loops. */
    static Track ending() {
        double bpm = 50;
        Track t = new Track(32 * 60 / bpm, true, 0.55);
        Inst box = Inst.musicBox(0.30, 0.6);
        box.detune = -22;
        box.wowDepth = 18;
        box.wowRate = 0.3;
        Inst pad = Inst.pad(0.03, 0.7);
        pad.attack = 1.5;
        pad.release = 1.5;
        melody(t, box, THEME_A, 0, bpm, 0, null);
        for (int bar = 0; bar < 8; bar++) {
            chord(t, pad, CHORDS_A[bar], beats(bar * 4, bpm), beats(4, bpm));
        }
        drone(t, 36, 0.05, 0.4);
        return t;
    }

    /** Level cleared: a rising music-box arpeggio and a sparkly chord. Plays once. */
    static Track complete() {
        Track t = new Track(4.2, false, 0.35);
        Inst box = Inst.musicBox(0.32, 0.4);
        Inst bell = Inst.musicBox(0.12, 0.6);
        Inst pad = Inst.pad(0.06, 0.4);
        pad.attack = 0.1;
        pad.release = 1.2;
        Inst lead = new Inst(PULSE, 0.12, 0.25);
        lead.duty = 0.25;
        lead.cutoff = 2600;
        lead.attack = 0.005;
        lead.decay = 0.3;
        lead.sustain = 0.4;
        lead.release = 0.4;
        int[] arpeggio = {72, 76, 79, 84, 88};
        for (int i = 0; i < arpeggio.length; i++) {
            note(t, box, i * 0.12, 0.3, arpeggio[i]);
            note(t, lead, i * 0.12, 0.1, arpeggio[i]);
        }
        note(t, lead, 0.62, 1.1, 84);
        chord(t, pad, C, 0.62, 1.4);
        note(t, bell, 0.62, 1, 96);
        note(t, bell, 0.86, 1, 103);
        note(t, bell, 1.1, 1, 100);
        return t;
    }

    /** Game over: the theme's first notes falling and slowing down, in minor. Plays once. */
    static Track gameOver() {
        Track t = new Track(5.0, false, 0.45);
        Inst box = Inst.musicBox(0.32, 0.5);
        box.detune = -15;
        box.wowDepth = 20;
        box.wowRate = 0.5;
        Inst low = new Inst(TRIANGLE, 0.16, 0.3);
        low.cutoff = 1200;
        low.attack = 0.02;
        low.decay = 0.4;
        low.sustain = 0.6;
        low.release = 0.6;
        double[][] fall = {{67, 0.0, 0.4}, {63, 0.45, 0.5}, {62, 1.0, 0.65}, {60, 1.75, 2.0}};
        for (double[] n : fall) {
            note(t, box, n[1], n[2], (int) n[0]);
            note(t, low, n[1], n[2], (int) n[0] - 12);
        }
        Inst drone = Inst.pad(0.10, 0.4);
        drone.attack = 0.6;
        drone.release = 1.5;
        note(t, drone, 1.75, 1.8, 36);
        note(t, drone, 1.75, 1.8, 43);
        return t;
    }

    // ------------------------------------------------------------------ music helpers

    static double beats(double beat, double bpm) {
        return beat * 60.0 / bpm;
    }

    /** Plays a melody starting at startBeat. If chords is given, the melody is turned minor (Level 2). */
    static void melody(Track t, Inst inst, double[][] notes, double startBeat, double bpm, int transpose, int[][] chords) {
        double beat = 0;
        for (double[] n : notes) {
            if (n[0] >= 0) {
                int pitch = (int) n[0];
                if (chords != null) {
                    int bar = (int) (beat / 4);
                    pitch = toMinor(pitch, chords[bar][0] % 12 == 7);
                }
                note(t, inst, beats(startBeat + beat, bpm), beats(n[1], bpm) * 0.95, pitch + transpose);
            }
            beat += n[1];
        }
    }

    /**
     * C major to C minor: E, A and B drop a semitone. Over a G chord (the "dominant"),
     * B stays natural so the music still pulls back home to C.
     */
    static int toMinor(int midi, boolean overDominant) {
        int pc = midi % 12;
        if (pc == 4 || pc == 9 || (pc == 11 && !overDominant)) {
            return midi - 1;
        }
        return midi;
    }

    static int[] minorChord(int[] chord) {
        boolean dominant = chord[0] % 12 == 7;
        int[] result = new int[chord.length];
        for (int i = 0; i < chord.length; i++) {
            result[i] = toMinor(chord[i], dominant);
        }
        return result;
    }

    /** The chord's root in the low bass octave (C2 to B2). */
    static int bassRoot(int[] chord) {
        return 36 + chord[0] % 12;
    }

    static void chord(Track t, Inst inst, int[] notes, double start, double dur) {
        for (int n : notes) {
            note(t, inst, start, dur, n);
        }
    }

    // ------------------------------------------------------------------ the synthesizer

    static final int SINE = 0;
    static final int TRIANGLE = 1;
    static final int PULSE = 2;
    static final int SAW = 3;
    static final int MUSIC_BOX = 4;

    /** How one instrument sounds. */
    static final class Inst {
        int wave;
        double volume;
        double send;          // how much goes to the reverb
        double attack = 0.01, decay = 0.2, sustain = 0.7, release = 0.1; // seconds / level
        double duty = 0.5;    // pulse width
        double cutoff = 0;    // low-pass filter in Hz, 0 = off
        double detune = 0;    // cents
        double wowDepth = 0;  // slow pitch wobble, in cents
        double wowRate = 0;   // wobbles per second
        double vibrato = 0;   // quick vibrato depth in semitones (after a short delay)

        Inst(int wave, double volume, double send) {
            this.wave = wave;
            this.volume = volume;
            this.send = send;
        }

        static Inst musicBox(double volume, double send) {
            return new Inst(MUSIC_BOX, volume, send);
        }

        static Inst pad(double volume, double send) {
            Inst p = new Inst(SINE, volume, send);
            p.attack = 0.6;
            p.decay = 0.1;
            p.sustain = 1;
            p.release = 0.8;
            return p;
        }

        Inst copy() {
            Inst c = new Inst(wave, volume, send);
            c.attack = attack;
            c.decay = decay;
            c.sustain = sustain;
            c.release = release;
            c.duty = duty;
            c.cutoff = cutoff;
            c.detune = detune;
            c.wowDepth = wowDepth;
            c.wowRate = wowRate;
            c.vibrato = vibrato;
            return c;
        }
    }

    /** A buffer of samples. Looping tracks wrap around, so tails at the end spill into the start. */
    static final class Track {
        final float[] dry;
        final float[] wet;
        final boolean loop;
        final double reverb;

        Track(double seconds, boolean loop, double reverb) {
            int n = (int) Math.round(seconds * SR);
            dry = new float[n];
            wet = new float[n];
            this.loop = loop;
            this.reverb = reverb;
        }

        void add(int i, double value, double send) {
            if (loop) {
                i = Math.floorMod(i, dry.length);
            } else if (i < 0 || i >= dry.length) {
                return;
            }
            dry[i] += value;
            wet[i] += value * send;
        }
    }

    static double frequency(double midi) {
        return 440.0 * Math.pow(2, (midi - 69) / 12.0);
    }

    /** Renders one note into the track. */
    static void note(Track t, Inst in, double start, double dur, double midi) {
        double baseHz = frequency(midi);
        boolean box = in.wave == MUSIC_BOX;
        double ring = box ? Math.min(4.0, 1.6 * Math.pow(440 / baseHz, 0.3) * 3.2) : dur + in.release;
        int first = (int) Math.round(start * SR);
        int count = (int) (ring * SR);
        double phase = 0;
        double filtered = 0;
        double alpha = in.cutoff > 0 ? 1 - Math.exp(-2 * Math.PI * in.cutoff / SR) : 1;
        double nyquist = SR / 2.0;
        for (int i = 0; i < count; i++) {
            double time = i / (double) SR;
            double global = start + time;
            double cents = in.detune + in.wowDepth * Math.sin(2 * Math.PI * in.wowRate * global);
            double semis = cents / 100.0;
            if (in.vibrato > 0 && time > 0.25) {
                semis += in.vibrato * Math.min(1, (time - 0.25) * 3) * Math.sin(2 * Math.PI * 5.5 * time);
            }
            double hz = baseHz * Math.pow(2, semis / 12.0);
            phase += hz / SR;
            double p = phase - Math.floor(phase);
            double v;
            if (box) {
                double tau = 1.6 * Math.pow(440 / baseHz, 0.3);
                v = Math.sin(2 * Math.PI * p) * Math.exp(-time / tau)
                        + 0.30 * Math.sin(2 * Math.PI * 2 * p) * Math.exp(-time / (tau * 0.35))
                        + 0.10 * Math.sin(2 * Math.PI * 3 * p) * Math.exp(-time / (tau * 0.2));
                if (hz * 5.4 < nyquist) {
                    v += 0.12 * Math.sin(2 * Math.PI * 5.4 * phase) * Math.exp(-time / 0.08);
                }
                v *= Math.min(1, time / 0.002);
            } else {
                v = oscillator(in.wave, p, hz, in.duty, nyquist) * envelope(in, time, dur);
            }
            filtered += alpha * (v - filtered);
            t.add(first + i, filtered * in.volume, in.send);
        }
    }

    /** One sample of a band-limited waveform (built from sine waves, so it doesn't buzz). */
    static double oscillator(int wave, double p, double hz, double duty, double nyquist) {
        double v = 0;
        switch (wave) {
            case SINE:
                return Math.sin(2 * Math.PI * p);
            case TRIANGLE:
                for (int k = 1; k * hz < nyquist && k <= 15; k += 2) {
                    double sign = ((k - 1) / 2) % 2 == 0 ? 1 : -1;
                    v += sign * Math.sin(2 * Math.PI * k * p) / (k * k);
                }
                return v * 8 / (Math.PI * Math.PI);
            case PULSE:
                for (int k = 1; k * hz < nyquist && k <= 28; k++) {
                    v += Math.sin(k * Math.PI * duty) * Math.cos(2 * Math.PI * k * p) / k;
                }
                return v * 4 / Math.PI * 0.7;
            case SAW:
                for (int k = 1; k * hz < nyquist && k <= 28; k++) {
                    v += Math.sin(2 * Math.PI * k * p) / k * (k % 2 == 0 ? -1 : 1);
                }
                return v * 2 / Math.PI * 0.8;
            default:
                return 0;
        }
    }

    static double envelope(Inst in, double time, double dur) {
        double level;
        if (time < in.attack) {
            level = time / in.attack;
        } else if (time < in.attack + in.decay) {
            level = 1 - (1 - in.sustain) * (time - in.attack) / in.decay;
        } else {
            level = in.sustain;
        }
        if (time > dur) {
            double held = envelopeAt(in, dur);
            level = Math.max(0, held * (1 - (time - dur) / in.release));
        }
        return level;
    }

    private static double envelopeAt(Inst in, double time) {
        if (time < in.attack) {
            return time / in.attack;
        } else if (time < in.attack + in.decay) {
            return 1 - (1 - in.sustain) * (time - in.attack) / in.decay;
        }
        return in.sustain;
    }

    /** A note that lasts the whole loop. Its pitch is nudged so it fits a whole number of cycles (no click at the seam). */
    static void drone(Track t, double midi, double volume, double send) {
        int n = t.dry.length;
        double seconds = n / (double) SR;
        double hz = Math.round(frequency(midi) * seconds) / seconds;
        for (int i = 0; i < n; i++) {
            double time = i / (double) SR;
            double swell = 0.75 + 0.25 * Math.sin(2 * Math.PI * 2 * time / seconds); // two slow swells per loop
            double angle = 2 * Math.PI * hz * time;
            double v = Math.sin(angle) + 0.35 * Math.sin(2 * angle) + 0.12 * Math.sin(3 * angle); // overtones help small speakers
            t.add(i, v * volume * swell, send);
        }
    }

    // ------------------------------------------------------------------ drums

    static final Random NOISE = new Random(1234);

    static void kick(Track t, double start, double volume, double fromHz, double toHz, double decay) {
        int first = (int) Math.round(start * SR);
        int count = (int) (decay * 5 * SR);
        double phase = 0;
        for (int i = 0; i < count; i++) {
            double time = i / (double) SR;
            double hz = toHz + (fromHz - toHz) * Math.exp(-time / 0.035);
            phase += hz / SR;
            double v = Math.sin(2 * Math.PI * phase) * Math.exp(-time / decay) * Math.min(1, time / 0.001);
            t.add(first + i, v * volume, 0.05);
        }
    }

    static void clap(Track t, double start, double volume) {
        noiseHit(t, start, volume, 0.06, 1, 0.2);
    }

    static void hat(Track t, double start, double volume) {
        noiseHit(t, start, volume, 0.018, 2, 0.05);
    }

    static void rim(Track t, double start, double volume) {
        noiseHit(t, start, volume * 0.6, 0.012, 2, 0.3);
        int first = (int) Math.round(start * SR);
        for (int i = 0; i < SR * 0.05; i++) {
            double time = i / (double) SR;
            t.add(first + i, Math.sin(2 * Math.PI * 1650 * time) * Math.exp(-time / 0.012) * volume, 0.3);
        }
    }

    /** A burst of noise; each high-pass pass makes it thinner and brighter. */
    static void noiseHit(Track t, double start, double volume, double decay, int highPasses, double send) {
        int first = (int) Math.round(start * SR);
        int count = (int) (decay * 6 * SR);
        double[] prev = new double[highPasses + 1];
        for (int i = 0; i < count; i++) {
            double time = i / (double) SR;
            double v = NOISE.nextDouble() * 2 - 1;
            for (int h = 0; h < highPasses; h++) {
                double out = v - prev[h];
                prev[h] = v;
                v = out * 0.5;
            }
            t.add(first + i, v * Math.exp(-time / decay) * volume, send);
        }
    }

    // ------------------------------------------------------------------ reverb and output

    /** A simple Schroeder reverb: 4 echoing delay lines in parallel, then 2 all-pass filters. */
    static float[] reverb(float[] in, boolean loop) {
        int n = in.length;
        int passes = loop ? 2 : 1; // for loops, run twice so the reverb tail wraps into the start
        int[] combs = {557, 593, 641, 677};
        double feedback = 0.83;
        double damp = 0.25;
        double[][] combBuf = new double[combs.length][];
        int[] combPos = new int[combs.length];
        double[] combLow = new double[combs.length];
        for (int c = 0; c < combs.length; c++) {
            combBuf[c] = new double[combs[c]];
        }
        int[] alls = {277, 109};
        double[][] allBuf = {new double[alls[0]], new double[alls[1]]};
        int[] allPos = new int[2];
        float[] out = new float[n];
        for (int pass = 0; pass < passes; pass++) {
            for (int i = 0; i < n; i++) {
                double x = in[i];
                double sum = 0;
                for (int c = 0; c < combs.length; c++) {
                    double y = combBuf[c][combPos[c]];
                    combLow[c] = y * (1 - damp) + combLow[c] * damp;
                    combBuf[c][combPos[c]] = x + combLow[c] * feedback;
                    combPos[c] = (combPos[c] + 1) % combs[c];
                    sum += y;
                }
                double y = sum * 0.25;
                for (int a = 0; a < 2; a++) {
                    double buffered = allBuf[a][allPos[a]];
                    double o = -y + buffered;
                    allBuf[a][allPos[a]] = y + buffered * 0.5;
                    allPos[a] = (allPos[a] + 1) % alls[a];
                    y = o;
                }
                if (pass == passes - 1) {
                    out[i] = (float) y;
                }
            }
        }
        return out;
    }

    /** Mixes dry + reverb, sets the level, and saves a 16-bit mono WAV. */
    static void write(Track t, File file, double peakLevel) throws IOException {
        float[] wet = reverb(t.wet, t.loop);
        int n = t.dry.length;
        double[] mix = new double[n];
        double peak = 0;
        for (int i = 0; i < n; i++) {
            mix[i] = t.dry[i] + wet[i] * t.reverb * 2.2;
            peak = Math.max(peak, Math.abs(mix[i]));
        }
        double gain = peakLevel / peak;
        if (!t.loop) { // jingles fade out at the very end
            int fade = SR / 4;
            for (int i = 0; i < fade; i++) {
                mix[n - 1 - i] *= i / (double) fade;
            }
        }
        byte[] pcm = new byte[n * 2];
        for (int i = 0; i < n; i++) {
            int s = (int) Math.round(Math.max(-1, Math.min(1, mix[i] * gain)) * 32767);
            pcm[2 * i] = (byte) s;
            pcm[2 * i + 1] = (byte) (s >> 8);
        }
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            out.writeBytes("RIFF");
            out.writeInt(Integer.reverseBytes(36 + pcm.length));
            out.writeBytes("WAVE");
            out.writeBytes("fmt ");
            out.writeInt(Integer.reverseBytes(16));
            out.writeShort(Short.reverseBytes((short) 1));      // PCM
            out.writeShort(Short.reverseBytes((short) 1));      // mono
            out.writeInt(Integer.reverseBytes(SR));
            out.writeInt(Integer.reverseBytes(SR * 2));         // bytes per second
            out.writeShort(Short.reverseBytes((short) 2));      // bytes per sample
            out.writeShort(Short.reverseBytes((short) 16));     // bits
            out.writeBytes("data");
            out.writeInt(Integer.reverseBytes(pcm.length));
            out.write(pcm);
        }
        System.out.printf("%-14s %5.1f s%n", file.getName(), n / (double) SR);
    }
}
