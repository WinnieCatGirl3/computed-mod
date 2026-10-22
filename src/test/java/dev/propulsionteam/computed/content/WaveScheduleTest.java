package dev.propulsionteam.computed.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WaveScheduleTest {
    @Test
    void clampsEnvelopeAndSweepTickRanges() {
        assertEquals(0, WaveSchedule.envelopeTicks(0.0));
        assertEquals(0, WaveSchedule.envelopeTicks(-1.0));
        assertEquals(10, WaveSchedule.envelopeTicks(0.5));
        assertEquals(WaveSchedule.MAX_ENVELOPE_TICKS, WaveSchedule.envelopeTicks(99.0));
        assertEquals(WaveSchedule.MIN_SWEEP_TICKS, WaveSchedule.sweepTicks(0.01));
        assertEquals(40, WaveSchedule.sweepTicks(2.0));
        assertEquals(WaveSchedule.MAX_SWEEP_TICKS, WaveSchedule.sweepTicks(60.0));
    }

    @Test
    void attackSlicesStepUpFromInitialFactorToFullVolume() {
        assertEquals(0.5F, WaveSchedule.initialAttackFactor(8), 1e-6);
        assertEquals(1.0F, WaveSchedule.initialAttackFactor(0), 1e-6);
        assertEquals(0.5F, WaveSchedule.attackSlice(0, 8), 1e-6);
        assertEquals(1.0F, WaveSchedule.attackSlice(4, 8), 1e-6);
        assertEquals(-1.0F, WaveSchedule.attackSlice(6, 8));
        assertEquals(-1.0F, WaveSchedule.attackSlice(8, 8));
        assertEquals(-1.0F, WaveSchedule.attackSlice(4, 0));
    }

    @Test
    void decaySlicesRunOverTheFinalTicksOfTheNote() {
        assertEquals(-1.0F, WaveSchedule.decaySlice(20, 30, 8));
        assertEquals(1.0F, WaveSchedule.decaySlice(22, 30, 8), 1e-6);
        assertEquals(0.5F, WaveSchedule.decaySlice(26, 30, 8), 1e-6);
        assertEquals(-1.0F, WaveSchedule.decaySlice(28, 30, 8));
        assertEquals(-1.0F, WaveSchedule.decaySlice(30, 30, 8));
        assertEquals(-1.0F, WaveSchedule.decaySlice(26, 30, 0));
    }

    @Test
    void sweepGlidesLinearlyAndFinishesAtTheEnd() {
        assertEquals(220.0, WaveSchedule.sweepFrequency(220, 880, 0, 40), 1e-6);
        assertEquals(550.0, WaveSchedule.sweepFrequency(220, 880, 20, 40), 1e-6);
        assertEquals(880.0, WaveSchedule.sweepFrequency(220, 880, 40, 40), 1e-6);
        assertEquals(220.0, WaveSchedule.sweepFrequency(220, 880, 10, 1), 1e-6);
    }

    @Test
    void pitchMatchesVanillaShiftRange() {
        assertEquals(1.0F, WaveSchedule.pitchFor(440.0), 1e-6);
        assertEquals(0.25F, WaveSchedule.pitchFor(1.0), 1e-6);
        assertEquals(4.0F, WaveSchedule.pitchFor(10_000.0), 1e-6);
    }

    @Test
    void noteDoneAfterTotalTicks() {
        assertFalse(WaveSchedule.noteDone(29, 30));
        assertTrue(WaveSchedule.noteDone(30, 30));
    }
}
