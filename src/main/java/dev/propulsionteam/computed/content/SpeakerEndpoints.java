package dev.propulsionteam.computed.content;

import dev.propulsionteam.computed.Computed;
import dev.propulsionteam.computed.content.blocks.ComputerBlockEntity;
import dev.propulsionteam.computed.content.blocks.SpeakerBlockEntity;
import dev.propulsionteam.computed.lua.endpoint.ComputedEndpoints;
import dev.propulsionteam.computed.lua.endpoint.EndpointInvocation;
import dev.propulsionteam.computed.lua.endpoint.EndpointPolicy;
import dev.propulsionteam.computed.lua.endpoint.EndpointResult;
import dev.propulsionteam.computed.lua.endpoint.EndpointSignature;
import dev.propulsionteam.computed.lua.endpoint.EndpointType;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import org.luaj.vm2.LuaValue;

public final class SpeakerEndpoints {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    private SpeakerEndpoints() {}

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        ComputedEndpoints.register("computed:speaker", endpoint -> endpoint
                .method(
                        "play_note",
                        EndpointSignature.of(
                                List.of(EndpointType.STRING, EndpointType.NUMBER),
                                List.of()),
                        EndpointPolicy.serverThread(true, false),
                        SpeakerEndpoints::playNote,
                        null,
                        "Plays a note-block instrument note on the adjacent speaker. "
                                + "Instrument: harp, basedrum, snare, hat, bass, flute, bell, guitar, chime, "
                                + "xylophone, iron_xylophone, cow_bell, didgeridoo, bit, banjo, pling. "
                                + "Note: 0-24.")
                .method(
                        "play_sound",
                        EndpointSignature.of(
                                List.of(EndpointType.STRING, EndpointType.NUMBER, EndpointType.NUMBER),
                                List.of()),
                        EndpointPolicy.serverThread(true, false),
                        SpeakerEndpoints::playSound,
                        null,
                        "Plays a registered sound event (namespace:path) at the speaker. "
                                + "Volume defaults are relative to 1.0; pitch 1.0 is normal.")
                .method(
                        "play_wave",
                        EndpointSignature.of(
                                List.of(EndpointType.STRING, EndpointType.NUMBER, EndpointType.NUMBER,
                                        EndpointType.NUMBER, EndpointType.NUMBER),
                                List.of()),
                        EndpointPolicy.serverThread(true, false),
                        SpeakerEndpoints::playWave,
                        null,
                        "Synthesizes a waveform tone at the speaker. "
                                + "Wave: sine, square, saw, triangle, noise. "
                                + "Frequency in Hz (55-3520, 440 = base pitch); volume 0-3. "
                                + "Attack and decay in seconds (0-2) fade the note in and out.")
                .method(
                        "play_sweep",
                        EndpointSignature.of(
                                List.of(EndpointType.STRING, EndpointType.NUMBER, EndpointType.NUMBER,
                                        EndpointType.NUMBER, EndpointType.NUMBER),
                                List.of()),
                        EndpointPolicy.serverThread(true, false),
                        SpeakerEndpoints::playSweep,
                        null,
                        "Glides between two frequencies over the given duration. "
                                + "Wave: sine, square, saw, triangle, noise. "
                                + "From/to in Hz (55-3520), duration in seconds (0.2-10), volume 0-3.")
                .method(
                        "play_sequence",
                        EndpointSignature.of(List.of(EndpointType.STRING), List.of()),
                        EndpointPolicy.serverThread(true, false),
                        SpeakerEndpoints::playSequence,
                        null,
                        "Plays a timed note sequence at the speaker (no disc needed). The spec is "
                                + "instrument:note:seconds tokens separated by spaces or commas: each "
                                + "seconds value is the gap until the next note. Instruments and note "
                                + "range as in play_note; 0-10 seconds per gap, up to 64 notes. "
                                + "stop() cancels a running sequence.")
                .method(
                        "play",
                        EndpointSignature.of(List.of(), List.of()),
                        EndpointPolicy.serverThread(true, false),
                        SpeakerEndpoints::play,
                        null,
                        "Starts playback of the inserted music disc (pulling the first playlist "
                                + "disc when the slot is empty). When discs are queued behind it, "
                                + "the song ends by dropping the finished disc and starting the "
                                + "next; with set_loop(true) the playlist cycles instead.")
                .method(
                        "set_loop",
                        EndpointSignature.of(List.of(EndpointType.BOOLEAN), List.of()),
                        EndpointPolicy.serverThread(true, false),
                        SpeakerEndpoints::setLoop,
                        null,
                        "Enables or disables automatic restart of the disc started with play.")
                .method(
                        "is_looping",
                        EndpointSignature.of(List.of(), List.of(EndpointType.BOOLEAN)),
                        EndpointPolicy.serverThread(false, false),
                        SpeakerEndpoints::isLooping,
                        null,
                        "Returns whether disc playback is set to loop.")
                .method(
                        "stop",
                        EndpointSignature.of(List.of(), List.of()),
                        EndpointPolicy.serverThread(true, false),
                        SpeakerEndpoints::stop,
                        null,
                        "Stops the current disc or note immediately.")
                .method(
                        "is_playing",
                        EndpointSignature.of(List.of(), List.of(EndpointType.BOOLEAN)),
                        EndpointPolicy.serverThread(false, false),
                        SpeakerEndpoints::isPlaying,
                        null,
                        "Returns whether a disc is currently playing.")
                .method(
                        "has_disc",
                        EndpointSignature.of(List.of(), List.of(EndpointType.BOOLEAN)),
                        EndpointPolicy.serverThread(false, false),
                        SpeakerEndpoints::hasDisc,
                        null,
                        "Returns whether a music disc is inserted.")
                .method(
                        "queue_size",
                        EndpointSignature.of(List.of(), List.of(EndpointType.NUMBER)),
                        EndpointPolicy.serverThread(false, false),
                        SpeakerEndpoints::queueSize,
                        null,
                        "Returns how many discs wait in the playlist behind the inserted one "
                                + "(up to 8; right-click a speaker with a disc to queue it).")
                .method(
                        "eject",
                        EndpointSignature.of(List.of(), List.of()),
                        EndpointPolicy.serverThread(true, false),
                        SpeakerEndpoints::eject,
                        null,
                        "Ejects the inserted music disc onto the ground in front of the speaker; "
                                + "with an empty slot it pops the next queued disc."));
    }

    private static EndpointResult playNote(EndpointInvocation invocation) {
        SpeakerBlockEntity speaker = requireSpeaker(invocation);
        String name = invocation.arguments().get(0).checkjstring();
        int note = Mth.clamp(invocation.arguments().get(1).checkint(), 0, 24);
        speaker.playNote(instrumentByName(name), note);
        return immediate();
    }

    private static EndpointResult playSound(EndpointInvocation invocation) {
        SpeakerBlockEntity speaker = requireSpeaker(invocation);
        ResourceLocation id = requireSoundId(invocation.arguments().get(0).checkjstring());
        float volume = (float) invocation.arguments().get(1).checkdouble();
        float pitch = (float) invocation.arguments().get(2).checkdouble();
        if (!speaker.playSound(id, Mth.clamp(volume, 0.0F, 4.0F), Mth.clamp(pitch, 0.5F, 2.0F))) {
            throw new IllegalArgumentException("Unknown sound event: " + id);
        }
        return immediate();
    }

    /** Waveform synthesis: pitch-shift a generated base tone (440 Hz) to the requested frequency. */
    private static EndpointResult playWave(EndpointInvocation invocation) {
        SpeakerBlockEntity speaker = requireSpeaker(invocation);
        ResourceLocation id = waveIdByName(invocation.arguments().get(0).checkjstring());
        double frequency = Mth.clamp(invocation.arguments().get(1).checkdouble(), 55.0, 3520.0);
        float volume = (float) Mth.clamp(invocation.arguments().get(2).checkdouble(), 0.0, 3.0);
        int attackTicks = WaveSchedule.envelopeTicks(numberArg(invocation, 3, 0.0));
        int decayTicks = WaveSchedule.envelopeTicks(numberArg(invocation, 4, 0.0));
        speaker.playWave(id, volume, WaveSchedule.pitchFor(frequency), attackTicks, decayTicks);
        return immediate();
    }

    private static EndpointResult playSweep(EndpointInvocation invocation) {
        SpeakerBlockEntity speaker = requireSpeaker(invocation);
        ResourceLocation id = waveIdByName(invocation.arguments().get(0).checkjstring());
        double from = Mth.clamp(invocation.arguments().get(1).checkdouble(), 55.0, 3520.0);
        double to = Mth.clamp(invocation.arguments().get(2).checkdouble(), 55.0, 3520.0);
        double seconds = Mth.clamp(invocation.arguments().get(3).checkdouble(), 0.2, 10.0);
        float volume = (float) Mth.clamp(
                numberArg(invocation, 4, 1.0), 0.0, 3.0);
        speaker.playSweep(id, from, to, volume, WaveSchedule.sweepTicks(seconds));
        return immediate();
    }

    private static ResourceLocation waveIdByName(String wave) {
        String normalized = wave.toLowerCase(java.util.Locale.ROOT);
        switch (normalized) {
            case "sine", "sin" -> {
                return waveId("sine");
            }
            case "square", "sq" -> {
                return waveId("square");
            }
            case "saw", "sawtooth" -> {
                return waveId("saw");
            }
            case "triangle", "tri" -> {
                return waveId("triangle");
            }
            case "noise", "white" -> {
                return waveId("noise");
            }
            default -> throw new IllegalArgumentException(
                    "Unknown wave: " + wave + " (use sine, square, saw, triangle, noise)");
        }
    }

    /** Reads an optional trailing number argument, falling back when absent or nil. */
    private static double numberArg(EndpointInvocation invocation, int index, double fallback) {
        var arguments = invocation.arguments();
        if (arguments.size() <= index || arguments.get(index).isnil()) {
            return fallback;
        }
        return arguments.get(index).checkdouble();
    }

    private static ResourceLocation waveId(String wave) {
        return ResourceLocation.fromNamespaceAndPath(Computed.MODID, "speaker_wave_" + wave);
    }

    private static EndpointResult play(EndpointInvocation invocation) {
        requireSpeaker(invocation).playDisc();
        return immediate();
    }

    /** Parses the sequence spec, resolves instruments, and schedules the notes on the speaker. */
    private static EndpointResult playSequence(EndpointInvocation invocation) {
        SpeakerBlockEntity speaker = requireSpeaker(invocation);
        List<SpeakerSequence.Step> steps =
                SpeakerSequence.parse(invocation.arguments().get(0).checkjstring());
        List<SpeakerBlockEntity.SequenceNote> notes = new java.util.ArrayList<>(steps.size());
        int previousStart = 0;
        for (SpeakerSequence.Step step : steps) {
            notes.add(new SpeakerBlockEntity.SequenceNote(
                    step.startTick() - previousStart,
                    instrumentByName(step.instrument()),
                    step.note()));
            previousStart = step.startTick();
        }
        speaker.playSequence(notes);
        return immediate();
    }

    private static EndpointResult setLoop(EndpointInvocation invocation) {
        requireSpeaker(invocation).setDiscLoop(invocation.arguments().get(0).checkboolean());
        return immediate();
    }

    private static EndpointResult isLooping(EndpointInvocation invocation) {
        return immediate(LuaValue.valueOf(requireSpeaker(invocation).isDiscLoop()));
    }

    private static EndpointResult stop(EndpointInvocation invocation) {
        requireSpeaker(invocation).stop();
        return immediate();
    }

    private static EndpointResult isPlaying(EndpointInvocation invocation) {
        return immediate(LuaValue.valueOf(requireSpeaker(invocation).isPlaying()));
    }

    private static EndpointResult hasDisc(EndpointInvocation invocation) {
        return immediate(LuaValue.valueOf(requireSpeaker(invocation).hasDisc()));
    }

    private static EndpointResult queueSize(EndpointInvocation invocation) {
        return immediate(LuaValue.valueOf(requireSpeaker(invocation).queueSize()));
    }

    private static EndpointResult eject(EndpointInvocation invocation) {
        SpeakerBlockEntity speaker = requireSpeaker(invocation);
        var level = speaker.getLevel();
        if (level != null && speaker.hasDisc()) {
            var disc = speaker.ejectDisc();
            if (!disc.isEmpty()) {
                ItemEntity item = new ItemEntity(
                        level, speaker.getBlockPos().getX() + 0.5, speaker.getBlockPos().getY() + 0.5,
                        speaker.getBlockPos().getZ() + 0.5, disc);
                item.setDefaultPickUpDelay();
                level.addFreshEntity(item);
            }
        }
        return immediate();
    }

    private static SpeakerBlockEntity requireSpeaker(EndpointInvocation invocation) {
        if (!(invocation.host() instanceof ComputerBlockEntity computer) || computer.getLevel() == null) {
            throw new IllegalStateException("Speaker endpoints require a server computer");
        }
        Direction direction = computer.worldFaceForEndpoint(invocation.target());
        if (direction == null) {
            throw new IllegalArgumentException("Unknown computer side: " + invocation.target());
        }
        BlockPos pos = computer.getBlockPos().relative(direction);
        if (!(computer.getLevel().getBlockEntity(pos) instanceof SpeakerBlockEntity speaker)) {
            throw new IllegalStateException("No speaker on side: " + invocation.target());
        }
        return speaker;
    }

    static NoteBlockInstrument instrumentByName(String name) {
        for (NoteBlockInstrument instrument : NoteBlockInstrument.values()) {
            if (instrument.getSerializedName().equalsIgnoreCase(name)) {
                return instrument;
            }
        }
        throw new IllegalArgumentException("Unknown instrument: " + name);
    }

    static ResourceLocation requireSoundId(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            throw new IllegalArgumentException("Invalid sound id: " + id);
        }
        return location;
    }

    private static EndpointResult.Immediate immediate(LuaValue... values) {
        return new EndpointResult.Immediate(List.of(values));
    }
}
