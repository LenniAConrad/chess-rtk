package application.gui.workbench.audio;

import java.awt.GraphicsEnvironment;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.prefs.Preferences;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.swing.SwingUtilities;

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
    private static final int DEFAULT_VOLUME_PERCENT = 28;

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
     * Settings listeners used by visible sound chips and settings controls.
     */
    private static final List<Runnable> SETTINGS_LISTENERS = new CopyOnWriteArrayList<>();

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
        if (muted == value) {
            return;
        }
        muted = value;
        PREFS.putBoolean(PREF_MUTED, value);
        notifySettingsListeners();
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
        if (volumePercent == clamped) {
            return;
        }
        volumePercent = clamped;
        PREFS.putInt(PREF_VOLUME_PERCENT, clamped);
        notifySettingsListeners();
    }

    /**
     * Registers a listener for sound setting changes.
     *
     * @param listener listener to run after mute or volume changes
     */
    public static void addSettingsListener(Runnable listener) {
        SETTINGS_LISTENERS.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * Removes a sound settings listener.
     *
     * @param listener listener to remove
     */
    public static void removeSettingsListener(Runnable listener) {
        SETTINGS_LISTENERS.remove(listener);
    }

    /**
     * Notifies sound setting listeners on the Swing thread.
     */
    private static void notifySettingsListeners() {
        for (Runnable listener : SETTINGS_LISTENERS) {
            if (SwingUtilities.isEventDispatchThread()) {
                listener.run();
            } else {
                SwingUtilities.invokeLater(listener);
            }
        }
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
            case UI_CLICK -> uiClick(volume);
            case POSITION_LOAD -> positionLoad(volume);
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
            case MCTS_START -> mctsStart(volume);
            case MCTS_PROGRESS -> mctsProgress(volume);
            case MCTS_PAUSE -> mctsPause(volume);
            case MCTS_RESUME -> mctsResume(volume);
            case MCTS_COMPLETE -> mctsComplete(volume);
            case MCTS_STOP -> mctsStop(volume);
        };
    }

    /**
     * Builds the general UI click cue.
     *
     * @param volume output multiplier
     * @return PCM bytes
     */
    private static byte[] uiClick(double volume) {
        double[] mix = buffer(74);
        wood(mix, 0, 48, 610.0, 0.068);
        wood(mix, 15, 38, 880.0, 0.028);
        return pcm(mix, volume * 0.62);
    }

    /**
     * Builds the position-loaded cue.
     *
     * @param volume output multiplier
     * @return PCM bytes
     */
    private static byte[] positionLoad(double volume) {
        double[] mix = buffer(165);
        wood(mix, 0, 68, 355.0, 0.105);
        wood(mix, 50, 62, 525.0, 0.065);
        bell(mix, 82, 70, 720.0, 0.038);
        return pcm(mix, volume * 0.82);
    }

    /**
     * Builds the normal move cue.
     *
     * @param volume output multiplier
     * @return PCM bytes
     */
    private static byte[] move(double volume) {
        double[] mix = buffer(92);
        wood(mix, 0, 78, 370.0, 0.22);
        wood(mix, 22, 46, 520.0, 0.055);
        return pcm(mix, volume);
    }

    /**
     * Builds the capture cue.
     *
     * @param volume output multiplier
     * @return PCM bytes
     */
    private static byte[] capture(double volume) {
        double[] mix = buffer(130);
        thud(mix, 0, 102, 220.0, 0.22);
        wood(mix, 16, 72, 430.0, 0.14);
        wood(mix, 44, 48, 610.0, 0.055);
        return pcm(mix, volume);
    }

    /**
     * Builds the check cue.
     *
     * @param volume output multiplier
     * @return PCM bytes
     */
    private static byte[] check(double volume) {
        double[] mix = buffer(170);
        wood(mix, 0, 55, 520.0, 0.085);
        bell(mix, 28, 104, 659.25, 0.105);
        bell(mix, 82, 78, 880.0, 0.075);
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
        wood(mix, 0, 68, 330.0, 0.17);
        wood(mix, 72, 70, 460.0, 0.17);
        wood(mix, 100, 42, 620.0, 0.045);
        return pcm(mix, volume);
    }

    /**
     * Builds the promotion cue.
     *
     * @param volume output multiplier
     * @return PCM bytes
     */
    private static byte[] promotion(double volume) {
        double[] mix = buffer(235);
        wood(mix, 0, 56, 430.0, 0.07);
        bell(mix, 38, 100, 523.25, 0.10);
        bell(mix, 102, 100, 659.25, 0.085);
        bell(mix, 164, 60, 783.99, 0.06);
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
        bell(mix, 0, 150, 392.0, 0.10);
        bell(mix, 126, 172, 493.88, 0.095);
        bell(mix, 272, 162, 587.33, 0.09);
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
        thud(mix, 0, 104, 128.0, 0.23);
        sweep(mix, 0, 96, 180.0, 118.0, 0.055);
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
        wood(mix, 0, 48, 560.0, 0.065);
        bell(mix, 44, 78, 700.0, 0.07);
        bell(mix, 82, 60, 920.0, 0.045);
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
        thud(mix, 0, 102, 158.0, 0.18);
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
        bell(mix, 0, 112, 523.25, 0.10);
        bell(mix, 96, 118, 659.25, 0.09);
        bell(mix, 214, 120, 783.99, 0.08);
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
        bell(mix, 0, 72, 987.77, 0.055);
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
        sweep(mix, 0, 170, 420.0, 640.0, 0.075);
        bell(mix, 84, 84, 640.0, 0.045);
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
        wood(mix, 0, 82, 480.0, 0.10);
        bell(mix, 52, 62, 640.0, 0.055);
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
        thud(mix, 0, 92, 238.0, 0.17);
        thud(mix, 78, 78, 178.0, 0.14);
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
        thud(mix, 0, 82, 204.0, 0.12);
        wood(mix, 42, 48, 310.0, 0.045);
        return pcm(mix, volume);
    }

    /**
     * Builds the MCTS start cue.
     *
     * @param volume output multiplier
     * @return PCM bytes
     */
    private static byte[] mctsStart(double volume) {
        double[] mix = buffer(150);
        wood(mix, 0, 70, 410.0, 0.11);
        bell(mix, 58, 78, 620.0, 0.055);
        return pcm(mix, volume);
    }

    /**
     * Builds the soft MCTS progress cue.
     *
     * @param volume output multiplier
     * @return PCM bytes
     */
    private static byte[] mctsProgress(double volume) {
        double[] mix = buffer(58);
        wood(mix, 0, 42, 560.0, 0.052);
        return pcm(mix, volume * 0.55);
    }

    /**
     * Builds the MCTS pause cue.
     *
     * @param volume output multiplier
     * @return PCM bytes
     */
    private static byte[] mctsPause(double volume) {
        double[] mix = buffer(125);
        sweep(mix, 0, 104, 420.0, 260.0, 0.075);
        return pcm(mix, volume);
    }

    /**
     * Builds the MCTS resume cue.
     *
     * @param volume output multiplier
     * @return PCM bytes
     */
    private static byte[] mctsResume(double volume) {
        double[] mix = buffer(125);
        sweep(mix, 0, 104, 260.0, 420.0, 0.075);
        wood(mix, 72, 42, 520.0, 0.04);
        return pcm(mix, volume);
    }

    /**
     * Builds the MCTS completion cue.
     *
     * @param volume output multiplier
     * @return PCM bytes
     */
    private static byte[] mctsComplete(double volume) {
        double[] mix = buffer(260);
        bell(mix, 0, 100, 440.0, 0.075);
        bell(mix, 92, 104, 554.37, 0.068);
        bell(mix, 178, 72, 659.25, 0.052);
        return pcm(mix, volume);
    }

    /**
     * Builds the MCTS stop cue.
     *
     * @param volume output multiplier
     * @return PCM bytes
     */
    private static byte[] mctsStop(double volume) {
        double[] mix = buffer(105);
        thud(mix, 0, 82, 184.0, 0.12);
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
     * Adds a soft wooden pluck with a small filtered transient.
     *
     * @param mix target buffer
     * @param startMs start offset in milliseconds
     * @param durationMs duration in milliseconds
     * @param frequency fundamental frequency in Hertz
     * @param gain linear gain
     */
    private static void wood(double[] mix, int startMs, int durationMs, double frequency, double gain) {
        add(mix, startMs, durationMs, frequency, frequency * 0.965, gain, true);
        add(mix, startMs + 1, Math.max(18, durationMs - 12),
                frequency * 1.53, frequency * 1.48, gain * 0.25, true);
        add(mix, startMs + 3, Math.max(16, durationMs - 18),
                frequency * 2.32, frequency * 2.24, gain * 0.105, true);
        noise(mix, startMs, 13, gain * 0.12);
    }

    /**
     * Adds a restrained chime with a quiet harmonic.
     *
     * @param mix target buffer
     * @param startMs start offset in milliseconds
     * @param durationMs duration in milliseconds
     * @param frequency fundamental frequency in Hertz
     * @param gain linear gain
     */
    private static void bell(double[] mix, int startMs, int durationMs, double frequency, double gain) {
        add(mix, startMs, durationMs, frequency, frequency, gain, false);
        add(mix, startMs, durationMs, frequency * 2.01, frequency * 2.01, gain * 0.27, false);
    }

    /**
     * Adds a low, rounded thud for negative feedback.
     *
     * @param mix target buffer
     * @param startMs start offset in milliseconds
     * @param durationMs duration in milliseconds
     * @param frequency fundamental frequency in Hertz
     * @param gain linear gain
     */
    private static void thud(double[] mix, int startMs, int durationMs, double frequency, double gain) {
        add(mix, startMs, durationMs, frequency, frequency * 0.92, gain, true);
        add(mix, startMs + 4, Math.max(20, durationMs - 18),
                frequency * 0.5, frequency * 0.48, gain * 0.20, true);
        noise(mix, startMs, 11, gain * 0.075);
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
        double filtered = 0.0;
        for (int i = 0; i < count && start + i < mix.length; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            double raw = (((seed >>> 40) & 0xffff) / 32768.0) - 1.0;
            filtered = filtered * 0.68 + raw * 0.32;
            mix[start + i] += filtered * gain * decayEnvelope(i, count);
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
        double attack = Math.min(1.0, index / Math.max(1.0, SAMPLE_RATE * 0.0026));
        return attack * Math.exp(-7.8 * t);
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
        double master = Math.max(0.0, Math.min(1.0, volume)) * 0.58;
        for (int i = 0; i < mix.length; i++) {
            double edge = edgeEnvelope(i, mix.length);
            double softened = Math.tanh(mix[i] * 1.2) * master * edge;
            int sample = (int) Math.round(Math.max(-1.0, Math.min(1.0, softened)) * 32767.0);
            out[i * 2] = (byte) (sample & 0xff);
            out[i * 2 + 1] = (byte) ((sample >>> 8) & 0xff);
        }
        return out;
    }

    /**
     * Returns a short global fade that prevents boundary clicks.
     *
     * @param index sample index
     * @param count total sample count
     * @return edge envelope in {@code [0.0, 1.0]}
     */
    private static double edgeEnvelope(int index, int count) {
        if (count <= 0) {
            return 0.0;
        }
        int fadeSamples = Math.max(1, samples(3));
        double in = Math.min(1.0, index / (double) fadeSamples);
        double out = Math.min(1.0, (count - index - 1) / (double) fadeSamples);
        return Math.max(0.0, Math.min(in, out));
    }
}
