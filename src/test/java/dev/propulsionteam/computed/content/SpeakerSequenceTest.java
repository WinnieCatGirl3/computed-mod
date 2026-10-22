package dev.propulsionteam.computed.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SpeakerSequenceTest {
    @Test
    void computesCumulativeStartTicksFromGaps() {
        List<SpeakerSequence.Step> steps = SpeakerSequence.parse("harp:12:0.5 bell:18:0.25 pling:15:0");
        assertEquals(3, steps.size());
        assertEquals("harp", steps.get(0).instrument());
        assertEquals(12, steps.get(0).note());
        assertEquals(0, steps.get(0).startTick());
        assertEquals("bell", steps.get(1).instrument());
        assertEquals(10, steps.get(1).startTick());
        assertEquals("pling", steps.get(2).instrument());
        assertEquals(15, steps.get(2).startTick());
        assertEquals(15, steps.get(2).note());
    }

    @Test
    void acceptsCommaAndWhitespaceSeparatorsAndRoundsTicks() {
        List<SpeakerSequence.Step> steps = SpeakerSequence.parse("harp:0:1,  snare:12:1\tbit:24:0.05");
        assertEquals(3, steps.size());
        assertEquals(List.of(0, 20, 40), steps.stream().map(SpeakerSequence.Step::startTick).toList());
        assertEquals(1, SpeakerSequence.parse("harp:0:0.05 bell:1:0").get(1).startTick());
    }

    @Test
    void rejectsMalformedSpecs() {
        List<String> bad = List.of(
                "",
                "   ",
                "harp:12",
                "harp:12:0.5:2",
                "harp:x:0.5",
                "harp:30:0.5",
                "harp:-1:0.5",
                "harp:12:-1",
                "harp:12:11",
                ":12:0.5");
        bad.forEach(spec -> assertThrows(
                IllegalArgumentException.class,
                () -> SpeakerSequence.parse(spec),
                spec));
    }

    @Test
    void rejectsTooManyNotes() {
        String spec = IntStream.range(0, SpeakerSequence.MAX_NOTES + 1)
                .mapToObj(i -> "harp:12:0.1")
                .collect(Collectors.joining(" "));
        assertThrows(IllegalArgumentException.class, () -> SpeakerSequence.parse(spec));
        String atLimit = IntStream.range(0, SpeakerSequence.MAX_NOTES)
                .mapToObj(i -> "harp:12:0.1")
                .collect(Collectors.joining(" "));
        assertEquals(SpeakerSequence.MAX_NOTES, SpeakerSequence.parse(atLimit).size());
    }
}
