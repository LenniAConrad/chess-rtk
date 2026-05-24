package application.gui.workbench.audio;

import java.awt.GraphicsEnvironment;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.prefs.Preferences;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/**
 * Procedural, non-blocking sound playback for short workbench feedback cues.
 *
 * <p>The service intentionally synthesizes every cue in Java. No bundled audio
 * files are used, so there are no asset provenance or license obligations.</p>
 */
public final class SoundService {

    /**
     * Preferences node for persisted sound settings.
     */
    private static final Preferences PREFS =
            Preferences.userNodeForPackage(SoundService.class);

    /**
     * Preference key for the global muted flag.
     */
    private static final String PREF_MUTED = "sound.muted";

    /**
     * Preference key for output volume as a percentage.
     */
    private static final String PREF_VOLUME_PERCENT = "sound.volumePercent";

    /**
     * Default conservative output volume.
     */
    private static final int DEFAULT_VOLUME_PERCENT = 30;

    /**
     * Minimum accepted volume percentage.
     */
    private static final int MIN_VOLUME_PERCENT = 0;

    /**
     * Maximum accepted volume percentage.
     */
    private static final int MAX_VOLUME_PERCENT = 100;

    /**
     * PCM sample rate in Hertz.
     */
    private static final float SAMPLE_RATE = 44_100.0f;

    /**
     * Audio output format: 16-bit signed mono little-endian PCM.
     */
    private static final AudioFormat FORMAT =
            new AudioFormat(SAMPLE_RATE, 16, 1, true, false);

    /**
     * Maximum queued sounds before new cues are dropped. This prevents a rapid
     * burst of UI events from building a long delayed audio tail.
     */
    private static final int MAX_PENDING_CUES = 6;

    /**
     * Tiny playback executor. All Java Sound work happens off the Swing event
     * dispatch thread.
     */
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "crtk-workbench-sound");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * Queue limiter used without blocking callers.
     */
    private static final Semaphore QUEUE_LIMIT = new Semaphore(MAX_PENDING_CUES);

    /**
     * Global muted flag.
     */
    private static volatile boolean muted = PREFS.getBoolean(PREF_MUTED, false);

    /**
     * Output volume as a percentage.
     */
    private static volatile int volumePercent = clampVolume(
            PREFS.getInt(PREF_VOLUME_PERCENT, DEFAULT_VOLUME_PERCENT));

    /**
     * Prevents instantiation.
     */
    private SoundService() {
        // utility
    }

    /**
     * Plays one cue asynchronously when sound is enabled.
     *
     * @param cue cue to play
     */
    public static void play(SoundCue cue) {
        Objects.requireNonNull(cue, "cue");
        int percent = volumePercent;
        if (muted || percent <= MIN_VOLUME_PERCENT || GraphicsEnvironment.isHeadless()) {
            return;
        }
        if (!QUEUE_LIMIT.tryAcquire()) {
            return;
        }
        EXECUTOR.execute(() -> {
            try {
                playPcm(synthesize(cue, percent / 100.0));
            } finally {
                QUEUE_LIMIT.release();
            }
        });
    }

    /**
     * Returns whether global workbench sound is muted.
     *
     * @return true when muted
     */
    public static boolean isMuted() {
        return muted;
    }

    /**
     * Sets the global muted flag and persists it.
     *
     * @param value true to mute workbench sounds
     */
    public static void setMuted(boolean value) {
        muted = value;
        PREFS.putBoolean(PREF_MUTED, value);
    }

    /**
     * Returns the current output volume as a percentage.
     *
     * @return volume in {@code [0, 100]}
     */
    public static int volumePercent() {
        return volumePercent;
    }

    /**
     * Sets and persists the output volume percentage.
     *
     * @param value requested volume percentage
     */
    public static void setVolumePercent(int value) {
        int clamped = clampVolume(value);
        volumePercent = clamped;
        PREFS.putInt(PREF_VOLUME_PERCENT, clamped);
    }

    /**
     * Clamps a volume percentage to the supported range.
     *
     * @param value requested percentage
     * @return clamped percentage
     */
    private static int clampVolume(int value) {
        return Math.max(MIN_VOLUME_PERCENT, Math.min(MAX_VOLUME_PERCENT, value));
    }

    /**
     * Writes synthesized PCM to Java Sound.
     *
     * @param pcm little-endian PCM bytes
     */
    private static void playPcm(byte[] pcm) {
        try (SourceDataLine line = AudioSystem.getSourceDataLine(FORMAT)) {
            line.open(FORMAT, pcm.length);
            line.start();
            line.write(pcm, 0, pcm.length);
            line.drain();
        } catch (IllegalArgumentException | LineUnavailableException ex) {
            // Some systems have no default output device. Sound is auxiliary;
            // failing silently keeps the workbench usable.
        }
    }

    /**
     * Synthesizes one cue into a 16-bit PCM byte buffer.
     *
     * @param cue cue to synthesize
     * @param volume output multiplier in {@code [0.0, 1.0]}
     * @return PCM bytes
     */
    private static byte[] synthesize(SoundCue cue, double volume) {
        return switch (cue) {
            case MOVE -> move(volume);
            case CAPTURE -> capture(volume);
            case CHECK -> check(volume);
            case CASTLE -> castle(volume);
            case PROMOTION -> promotion(volume);
            case GAME_END -> gameEnd(volume);
            case ILLEGAL -> illegal(volume);
            case PUZZLE_CORRECT -> puzzleCorrect(volume);
            case PUZZLE_WRONG -> puzzleWrong(volume);
            case PUZZLE_COMPLETE -> puzzleComplete(volume);
            case HINT -> hint(volume);
            case REVEAL -> reveal(volume);
            case JOB_SUCCESS -> jobSuccess(volume);
            case JOB_FAILURE -> jobFailure(volume);
            case JOB_CANCELLED -> jobCancelled(volume);
        };
    }

    /**
     * Builds the normal move cue.
     *
     * @param volume output multiplier
     * @return PCM bytes
     */
    private static byte[] move(double volume) {
        double[] mix = buffer(90);
        tick(mix, 0, 78, 430.0, 0.38);
        noise(mix, 0, 18, 0.08);
        return pcm(mix, volume);
    }

    /**
     * Builds the capture cue.
     *
     * @param volume output multiplier
     * @return PCM bytes
     */
    private static byte[] capture(double volume) {
        double[] mix = buffer(125);
        tick(mix, 0, 105, 270.0, 0.44);
        noise(mix, 0, 32, 0.14);
        return pcm(mix, volume);
    }

    /**
     * Builds the check cue.
     *
     * @param volume output multiplier
     * @return PCM bytes
     */
    private static byte[] check(double volume) {
        double[] mix = buffer(150);
        tone(mix, 0, 82, 660.0, 0.25);
        tone(mix, 54, 92, 880.0, 0.22);
        return pcm(mix, volume);
    }

    /**
     * Builds the castle cue.
     *
     * @param volume output multiplier
     * @return PCM bytes
     */
    private static byte[] castle(double volume) {
        double[] mix = buffer(180);
        tick(mix, 0, 70, 390.0, 0.32);
        tick(mix, 72, 76, 450.0, 0.32);
        return pcm(mix, volume);
    }

    /**
     * Builds the promotion cue.
     *
     * @param volume output multiplier
     * @return PCM bytes
     */
    private static byte[] promotion(double volume) {
        double[] mix = buffer(220);
        tone(mix, 0, 95, 523.25, 0.20);
        tone(mix, 70, 120, 659.25, 0.18);
        tone(mix, 132, 82, 783.99, 0.16);
        return pcm(mix, volume);
    }

    /**
     * Builds the game-end cue.
     *
     * @param volume output multiplier
     * @return PCM bytes
     */
    private static byte[] gameEnd(double volume) {
        double[] mix = buffer(460);
        tone(mix, 0, 160, 392.0, 0.20);
        tone(mix, 120, 180, 493.88, 0.18);
        tone(mix, 260, 185, 587.33, 0.18);
        return pcm(mix, volume);
    }

    /**
     * Builds the illegal/snapback cue.
     *
     * @param volume output multiplier
     * @return PCM bytes
     */
    private static byte[] illegal(double volume) {
        double[] mix = buffer(115);
        tone(mix, 0, 110, 142.0, 0.36);
        noise(mix, 0, 30, 0.10);
        return pcm(mix, volume);
    }

    /**
     * Builds the puzzle-correct cue.
     *
     * @param volume output multiplier
     * @return PCM bytes
     */
    private static byte[] puzzleCorrect(double volume) {
        double[] mix = buffer(155);
        tone(mix, 0, 78, 620.0, 0.18);
        tone(mix, 54, 95, 820.0, 0.16);
        return pcm(mix, volume);
    }

    /**
     * Builds the puzzle-wrong cue.
     *
     * @param volume output multiplier
     * @return PCM bytes
     */
    private static byte[] puzzleWrong(double volume) {
        double[] mix = buffer(115);
        tone(mix, 0, 105, 180.0, 0.28);
        noise(mix, 0, 24, 0.06);
        return pcm(mix, volume);
    }

    /**
     * Builds the puzzle-complete cue.
     *
     * @param volume output multiplier
     * @return PCM bytes
     */
    private static byte[] puzzleComplete(double volume) {
        double[] mix = buffer(360);
        tone(mix, 0, 110, 523.25, 0.18);
        tone(mix, 100, 120, 659.25, 0.17);
        tone(mix, 215, 135, 783.99, 0.16);
        return pcm(mix, volume);
    }

    /**
     * Builds the hint cue.
     *
     * @param volume output multiplier
     * @return PCM bytes
     */
    private static byte[] hint(double volume) {
        double[] mix = buffer(85);
        tone(mix, 0, 74, 980.0, 0.12);
        return pcm(mix, volume);
    }

    /**
     * Builds the reveal cue.
     *
     * @param volume output multiplier
     * @return PCM bytes
     */
    private static byte[] reveal(double volume) {
        double[] mix = buffer(190);
        sweep(mix, 0, 170, 420.0, 660.0, 0.13);
        return pcm(mix, volume);
    }

    /**
     * Builds the job-success cue.
     *
     * @param volume output multiplier
     * @return PCM bytes
     */
    private static byte[] jobSuccess(double volume) {
        double[] mix = buffer(125);
        tone(mix, 0, 105, 520.0, 0.16);
        tone(mix, 55, 60, 690.0, 0.11);
        return pcm(mix, volume);
    }

    /**
     * Builds the job-failure cue.
     *
     * @param volume output multiplier
     * @return PCM bytes
     */
    private static byte[] jobFailure(double volume) {
        double[] mix = buffer(170);
        tone(mix, 0, 80, 260.0, 0.22);
        tone(mix, 74, 86, 196.0, 0.20);
        return pcm(mix, volume);
    }

    /**
     * Builds the job-cancelled cue.
     *
     * @param volume output multiplier
     * @return PCM bytes
     */
    private static byte[] jobCancelled(double volume) {
        double[] mix = buffer(105);
        tone(mix, 0, 88, 210.0, 0.15);
        return pcm(mix, volume);
    }

    /**
     * Allocates a sample buffer.
     *
     * @param millis duration in milliseconds
     * @return empty sample buffer
     */
    private static double[] buffer(int millis) {
        return new double[samples(millis)];
    }

    /**
     * Adds a short decaying tick to a buffer.
     *
     * @param mix target buffer
     * @param startMs start offset in milliseconds
     * @param durationMs duration in milliseconds
     * @param frequency frequency in Hertz
     * @param gain linear gain
     */
    private static void tick(double[] mix, int startMs, int durationMs, double frequency, double gain) {
        add(mix, startMs, durationMs, frequency, frequency, gain, true);
    }

    /**
     * Adds a steady sine tone to a buffer.
     *
     * @param mix target buffer
     * @param startMs start offset in milliseconds
     * @param durationMs duration in milliseconds
     * @param frequency frequency in Hertz
     * @param gain linear gain
     */
    private static void tone(double[] mix, int startMs, int durationMs, double frequency, double gain) {
        add(mix, startMs, durationMs, frequency, frequency, gain, false);
    }

    /**
     * Adds a sine sweep to a buffer.
     *
     * @param mix target buffer
     * @param startMs start offset in milliseconds
     * @param durationMs duration in milliseconds
     * @param fromFrequency starting frequency in Hertz
     * @param toFrequency ending frequency in Hertz
     * @param gain linear gain
     */
    private static void sweep(double[] mix, int startMs, int durationMs,
            double fromFrequency, double toFrequency, double gain) {
        add(mix, startMs, durationMs, fromFrequency, toFrequency, gain, false);
    }

    /**
     * Adds a deterministic noise transient to a buffer.
     *
     * @param mix target buffer
     * @param startMs start offset in milliseconds
     * @param durationMs duration in milliseconds
     * @param gain linear gain
     */
    private static void noise(double[] mix, int startMs, int durationMs, double gain) {
        int start = samples(startMs);
        int count = samples(durationMs);
        long seed = 0x5DEECE66DL + start * 31L + count;
        for (int i = 0; i < count && start + i < mix.length; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            double raw = (((seed >>> 40) & 0xffff) / 32768.0) - 1.0;
            mix[start + i] += raw * gain * decayEnvelope(i, count);
        }
    }

    /**
     * Adds a tone or sweep to a buffer.
     *
     * @param mix target buffer
     * @param startMs start offset in milliseconds
     * @param durationMs duration in milliseconds
     * @param fromFrequency starting frequency in Hertz
     * @param toFrequency ending frequency in Hertz
     * @param gain linear gain
     * @param percussive true for a faster decay envelope
     */
    private static void add(double[] mix, int startMs, int durationMs,
            double fromFrequency, double toFrequency, double gain, boolean percussive) {
        int start = samples(startMs);
        int count = samples(durationMs);
        double phase = 0.0;
        for (int i = 0; i < count && start + i < mix.length; i++) {
            double t = count <= 1 ? 0.0 : i / (double) (count - 1);
            double frequency = fromFrequency + (toFrequency - fromFrequency) * t;
            phase += 2.0 * Math.PI * frequency / SAMPLE_RATE;
            double envelope = percussive ? decayEnvelope(i, count) : smoothEnvelope(i, count);
            mix[start + i] += Math.sin(phase) * gain * envelope;
        }
    }

    /**
     * Returns a quick-decay envelope.
     *
     * @param index sample index within the tone
     * @param count total tone samples
     * @return envelope value
     */
    private static double decayEnvelope(int index, int count) {
        if (count <= 0) {
            return 0.0;
        }
        double t = index / (double) count;
        double attack = Math.min(1.0, index / Math.max(1.0, SAMPLE_RATE * 0.004));
        return attack * Math.exp(-5.5 * t);
    }

    /**
     * Returns a soft attack/release envelope.
     *
     * @param index sample index within the tone
     * @param count total tone samples
     * @return envelope value
     */
    private static double smoothEnvelope(int index, int count) {
        if (count <= 0) {
            return 0.0;
        }
        int attackSamples = Math.max(1, samples(8));
        int releaseSamples = Math.max(1, samples(28));
        double attack = Math.min(1.0, index / (double) attackSamples);
        double release = Math.min(1.0, (count - index) / (double) releaseSamples);
        return Math.max(0.0, Math.min(attack, release));
    }

    /**
     * Converts milliseconds to sample count.
     *
     * @param millis milliseconds
     * @return sample count
     */
    private static int samples(int millis) {
        return Math.max(0, (int) Math.round(SAMPLE_RATE * millis / 1000.0));
    }

    /**
     * Converts a normalized sample buffer to 16-bit little-endian PCM.
     *
     * @param mix mixed sample buffer
     * @param volume output multiplier
     * @return PCM bytes
     */
    private static byte[] pcm(double[] mix, double volume) {
        byte[] out = new byte[mix.length * 2];
        double master = Math.max(0.0, Math.min(1.0, volume)) * 0.72;
        for (int i = 0; i < mix.length; i++) {
            int sample = (int) Math.round(Math.max(-1.0, Math.min(1.0, mix[i] * master)) * 32767.0);
            out[i * 2] = (byte) (sample & 0xff);
            out[i * 2 + 1] = (byte) ((sample >>> 8) & 0xff);
        }
        return out;
    }
}
