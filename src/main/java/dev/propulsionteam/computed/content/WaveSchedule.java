package dev.propulsionteam.computed.content;

/**
 * Pure timing math for speaker waveform envelopes and frequency sweeps. Kept free of
 * Minecraft classes so the ramp and glide math can be unit-tested on the stub classpath.
 *
 * <p>Envelope and sweep work by restarting the one-shot wave assets in short slices
 * (with a stop packet in between), because Minecraft one-shots cannot change pitch or
 * volume mid-play.
 */
public final class WaveSchedule {
    /** Length of the shipped wave assets in game ticks (1.5 s). */
    public static final int WAVE_TICKS = 30;
    /** Volume ramp granularity: one restarted slice every 4 ticks (0.2 s). */
    public static final int RAMP_STEP_TICKS = 4;
    /** Sweep glide granularity: one restarted slice every 2 ticks (0.1 s). */
    public static final int SWEEP_STEP_TICKS = 2;
    /** Attack/decay caps at 2 seconds (40 ticks). */
    public static final int MAX_ENVELOPE_TICKS = 40;
    /** Sweep duration clamped between 0.2 s and 10 s. */
    public static final int MIN_SWEEP_TICKS = 4;
    public static final int MAX_SWEEP_TICKS = 200;

    private WaveSchedule() {}

    /** Converts an envelope duration in seconds to clamped game ticks. */
    public static int envelopeTicks(double seconds) {
        if (!(seconds > 0.0)) {
            return 0;
        }
        return (int) Math.min(MAX_ENVELOPE_TICKS, Math.round(seconds * 20.0));
    }

    /** Converts a sweep duration in seconds to clamped game ticks. */
    public static int sweepTicks(double seconds) {
        return (int) Math.max(MIN_SWEEP_TICKS, Math.min(MAX_SWEEP_TICKS, Math.round(seconds * 20.0)));
    }

    /**
     * Volume factor of the attack slice starting at {@code elapsed}, or {@code -1} when no
     * slice starts on this tick. The first slice runs at tick 0; the caller plays the initial
     * sound itself, so tick 0 is reported here as well and the caller skips a duplicate.
     */
    public static float attackSlice(int elapsed, int attackTicks) {
        if (attackTicks <= 0 || elapsed >= attackTicks || elapsed % RAMP_STEP_TICKS != 0) {
            return -1;
        }
        return Math.min(1.0F, (elapsed + RAMP_STEP_TICKS) / (float) attackTicks);
    }

    /**
     * Volume factor of the decay slice starting at {@code elapsed}, or {@code -1} when no
     * slice starts here. Decay runs over the final {@code decayTicks} of the note.
     */
    public static float decaySlice(int elapsed, int totalTicks, int decayTicks) {
        if (decayTicks <= 0) {
            return -1;
        }
        int start = totalTicks - decayTicks;
        if (elapsed < start || elapsed >= totalTicks || (elapsed - start) % RAMP_STEP_TICKS != 0) {
            return -1;
        }
        return Math.max(0.0F, 1.0F - (elapsed - start) / (float) decayTicks);
    }

    /** Initial volume factor (first attack slice) when a note starts. */
    public static float initialAttackFactor(int attackTicks) {
        if (attackTicks <= 0) {
            return 1.0F;
        }
        return Math.min(1.0F, RAMP_STEP_TICKS / (float) attackTicks);
    }

    /** Whether the wave note has finished at {@code elapsed}. */
    public static boolean noteDone(int elapsed, int totalTicks) {
        return elapsed >= totalTicks;
    }

    /** Frequency at the given position of a linear glide from {@code from} to {@code to}. */
    public static double sweepFrequency(double from, double to, int elapsed, int totalTicks) {
        if (totalTicks <= 1) {
            return from;
        }
        double t = Math.min(1.0, Math.max(0.0, elapsed / (double) totalTicks));
        return from + (to - from) * t;
    }

    /** Pitch multiplier for a frequency in Hz, matching the vanilla pitch-shift range. */
    public static float pitchFor(double frequency) {
        return (float) Math.max(0.25, Math.min(4.0, frequency / 440.0));
    }
}
