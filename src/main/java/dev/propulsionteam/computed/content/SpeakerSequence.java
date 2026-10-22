package dev.propulsionteam.computed.content;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses a speaker note sequence spec ({@code instrument:note:seconds} tokens separated by spaces
 * or commas) into start positions on the game-tick timeline. Pure Java — instrument names are
 * validated by the caller, so unit tests never load Minecraft classes.
 *
 * <p>The duration after a note is the gap until the next one; the spec's rhythm comes from those
 * gaps. Example: {@code harp:12:0.5 bell:18:0.25} plays a harp note, then a bell half a second
 * later, then stops.
 */
public final class SpeakerSequence {
    /** Upper bound for the number of notes in one sequence. */
    public static final int MAX_NOTES = 64;
    /** Longest gap after one note, in seconds. */
    public static final double MAX_GAP_SECONDS = 10.0;
    public static final int TICKS_PER_SECOND = 20;

    private SpeakerSequence() {}

    /** One note with its start position measured in game ticks from the sequence begin. */
    public record Step(String instrument, int note, int startTick) {}

    /**
     * @throws IllegalArgumentException for an empty spec, too many notes, or a malformed token
     */
    public static List<Step> parse(String spec) {
        if (spec == null || spec.isBlank()) {
            throw new IllegalArgumentException("Sequence is empty (use instrument:note:seconds notes)");
        }
        String[] tokens = spec.trim().split("[\\s,]+");
        if (tokens.length > MAX_NOTES) {
            throw new IllegalArgumentException("Too many notes (max " + MAX_NOTES + ")");
        }
        List<Step> steps = new ArrayList<>(tokens.length);
        double cursorSeconds = 0.0;
        for (String token : tokens) {
            String[] parts = token.split(":");
            if (parts.length != 3 || parts[0].isBlank()) {
                throw new IllegalArgumentException(
                        "Bad note '" + token + "': expected instrument:note:seconds");
            }
            int note;
            try {
                note = Integer.parseInt(parts[1]);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(
                        "Bad note '" + token + "': note must be a whole number (0-24)");
            }
            if (note < 0 || note > 24) {
                throw new IllegalArgumentException(
                        "Bad note '" + token + "': note must be 0-24");
            }
            double gap;
            try {
                gap = Double.parseDouble(parts[2]);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(
                        "Bad note '" + token + "': seconds must be a number");
            }
            if (gap < 0.0 || gap > MAX_GAP_SECONDS) {
                throw new IllegalArgumentException(
                        "Bad note '" + token + "': seconds must be 0-" + MAX_GAP_SECONDS);
            }
            steps.add(new Step(parts[0], note, (int) Math.round(cursorSeconds * TICKS_PER_SECOND)));
            cursorSeconds += gap;
        }
        return steps;
    }
}
