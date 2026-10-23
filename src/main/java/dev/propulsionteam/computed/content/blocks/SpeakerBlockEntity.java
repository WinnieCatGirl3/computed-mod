package dev.propulsionteam.computed.content.blocks;

import dev.propulsionteam.computed.Computed;
import dev.propulsionteam.computed.content.ComputedRegistries;
import dev.propulsionteam.computed.content.WaveSchedule;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import org.jetbrains.annotations.Nullable;

public class SpeakerBlockEntity extends BlockEntity {
    public static final String DISC_TAG = "Disc";
    public static final String PLAYING_TAG = "Playing";
    public static final String REMAINING_TAG = "Remaining";
    public static final String QUEUE_TAG = "DiscQueue";
    public static final String LOOP_TAG = "DiscLoop";
    /** Playlist capacity: how many discs wait behind the inserted one. */
    public static final int MAX_QUEUE_SIZE = 8;

    private ItemStack disc = ItemStack.EMPTY;
    /** Playlist discs waiting after the inserted one; play advances through them in order. */
    private final List<ItemStack> queue = new ArrayList<>();
    private boolean playing;
    private int remainingTicks;
    private boolean discLoop;

    // Active synthesized wave note (attack/decay envelope), server-side only.
    @Nullable
    private ResourceLocation waveSound;
    private boolean waveActive;
    private int waveElapsed;
    private int waveTotal = WaveSchedule.WAVE_TICKS;
    private int waveAttackTicks;
    private int waveDecayTicks;
    private float waveVolume = 1.0F;
    private float wavePitch = 1.0F;

    // Active frequency sweep, server-side only.
    @Nullable
    private ResourceLocation sweepSound;
    private boolean sweepActive;
    private int sweepElapsed;
    private int sweepTotal;
    private double sweepFrom;
    private double sweepTo;
    private float sweepVolume = 1.0F;

    /**
     * Scheduled note-sequence steps, server-side only: the head plays after its delay, then the
     * countdown moves to the next step. Not persisted — a sequence stops on chunk reload.
     */
    private final List<SequenceNote> sequence = new ArrayList<>();
    private int sequenceCountdown;

    /** One scheduled note: ticks to wait after the previous note, then pitch data. */
    public record SequenceNote(int delayTicks, NoteBlockInstrument instrument, int note) {}

    public SpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(ComputedRegistries.SPEAKER_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.saveAdditional(tag, lookup);
        if (!disc.isEmpty()) {
            tag.put(DISC_TAG, disc.save(lookup));
        }
        if (playing) {
            tag.putBoolean(PLAYING_TAG, true);
            tag.putInt(REMAINING_TAG, remainingTicks);
        }
        if (!queue.isEmpty()) {
            ListTag queued = new ListTag();
            queue.forEach(item -> queued.add(item.save(lookup)));
            tag.put(QUEUE_TAG, queued);
        }
        if (discLoop) {
            tag.putBoolean(LOOP_TAG, true);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.loadAdditional(tag, lookup);
        disc = tag.contains(DISC_TAG) ? ItemStack.parseOptional(lookup, tag.getCompound(DISC_TAG)) : ItemStack.EMPTY;
        playing = tag.getBoolean(PLAYING_TAG);
        remainingTicks = tag.getInt(REMAINING_TAG);
        discLoop = tag.getBoolean(LOOP_TAG);
        queue.clear();
        if (tag.contains(QUEUE_TAG, Tag.TAG_LIST)) {
            ListTag queued = tag.getList(QUEUE_TAG, Tag.TAG_COMPOUND);
            for (int i = 0; i < queued.size() && i < MAX_QUEUE_SIZE; i++) {
                ItemStack item = ItemStack.parseOptional(lookup, queued.getCompound(i));
                if (!item.isEmpty()) {
                    queue.add(item);
                }
            }
        }
    }

    public ItemStack getDisc() {
        return disc;
    }

    public boolean hasDisc() {
        return !disc.isEmpty();
    }

    public boolean isPlaying() {
        return playing;
    }

    public void setDiscLoop(boolean loop) {
        if (discLoop == loop) {
            return;
        }
        discLoop = loop;
        Computed.LOGGER.info("Speaker at {} disc loop {}", worldPosition, loop ? "enabled" : "disabled");
        setChanged();
        syncToClient();
    }

    public boolean isDiscLoop() {
        return discLoop;
    }

    /**
     * Starts a waveform note with an optional attack/decay envelope. Minecraft one-shots cannot
     * change volume mid-play, so the envelope restarts the tone in short ramp slices.
     */
    public void playWave(ResourceLocation soundId, float volume, float pitch, int attackTicks, int decayTicks) {
        if (level == null || level.isClientSide) {
            return;
        }
        cutActiveSynth();
        waveSound = soundId;
        waveActive = true;
        waveElapsed = 0;
        waveTotal = WaveSchedule.WAVE_TICKS;
        waveAttackTicks = attackTicks;
        waveDecayTicks = decayTicks;
        waveVolume = volume;
        wavePitch = pitch;
        playSound(soundId, Math.max(0.0F, volume * WaveSchedule.initialAttackFactor(attackTicks)), pitch);
    }

    /** Starts a linear frequency glide by restarting the tone in short slices. */
    public void playSweep(ResourceLocation soundId, double fromHz, double toHz, float volume, int totalTicks) {
        if (level == null || level.isClientSide) {
            return;
        }
        cutActiveSynth();
        sweepSound = soundId;
        sweepActive = true;
        sweepElapsed = 0;
        sweepTotal = totalTicks;
        sweepFrom = fromHz;
        sweepTo = toHz;
        sweepVolume = volume;
        playSound(soundId, volume, WaveSchedule.pitchFor(fromHz));
    }

    private void cutActiveSynth() {
        if (waveActive && waveSound != null) {
            sendStopSound(waveSound);
        }
        if (sweepActive && sweepSound != null) {
            sendStopSound(sweepSound);
        }
        waveActive = false;
        sweepActive = false;
    }

    private void sendStopSound(ResourceLocation soundId) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }
        var packet = new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(soundId, SoundSource.RECORDS);
        for (var player : serverLevel.players()) {
            player.connection.send(packet);
        }
    }

    private void tickWaveNote() {
        if (!waveActive || waveSound == null) {
            return;
        }
        int elapsed = waveElapsed;
        float attack = WaveSchedule.attackSlice(elapsed, waveAttackTicks);
        float decay = WaveSchedule.decaySlice(elapsed, waveTotal, waveDecayTicks);
        if (attack >= 0.0F && elapsed > 0) {
            sendStopSound(waveSound);
            playSound(waveSound, Math.max(0.0F, waveVolume * attack), wavePitch);
        } else if (decay >= 0.0F && elapsed > 0) {
            sendStopSound(waveSound);
            if (decay > 0.0F) {
                playSound(waveSound, waveVolume * decay, wavePitch);
            }
        }
        waveElapsed++;
        if (WaveSchedule.noteDone(waveElapsed, waveTotal)) {
            if (waveDecayTicks > 0) {
                sendStopSound(waveSound);
            }
            waveActive = false;
        }
    }

    private void tickSweep() {
        if (!sweepActive || sweepSound == null) {
            return;
        }
        int elapsed = sweepElapsed;
        if (elapsed > 0 && elapsed % WaveSchedule.SWEEP_STEP_TICKS == 0) {
            double frequency = WaveSchedule.sweepFrequency(sweepFrom, sweepTo, elapsed, sweepTotal);
            sendStopSound(sweepSound);
            playSound(sweepSound, sweepVolume, WaveSchedule.pitchFor(frequency));
        }
        sweepElapsed++;
        if (sweepElapsed >= sweepTotal) {
            sendStopSound(sweepSound);
            sweepActive = false;
        }
    }

    /** Inserts a music disc. An empty slot takes the disc; a filled one queues it for the playlist. */
    public boolean tryInsertDisc(ItemStack stack, @Nullable Player player) {
        if (level == null || level.isClientSide || !isMusicDisc(level, stack)) {
            return false;
        }
        if (!disc.isEmpty()) {
            if (queue.size() >= MAX_QUEUE_SIZE) {
                return false;
            }
            queue.add(stack.copyWithCount(1));
        } else {
            stop();
            disc = stack.copyWithCount(1);
        }
        if (player != null && !player.hasInfiniteMaterials()) {
            stack.shrink(1);
        }
        setChanged();
        syncToClient();
        return true;
    }

    /** Number of discs waiting behind the inserted one (playlist capacity 8). */
    public int queueSize() {
        return queue.size();
    }

    /** Drops a finished disc onto the ground at the speaker, like a vanilla jukebox. */
    private void dropDiscToGround(ItemStack finished) {
        if (level == null || finished.isEmpty()) {
            return;
        }
        ItemEntity item = new ItemEntity(
                level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5, finished);
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
    }

    /**
     * Removes and returns the inserted disc (empty stack if none), falling back to the head of the
     * playlist queue so queued discs stay reachable. Stops playback first.
     */
    public ItemStack ejectDisc() {
        if (level == null || level.isClientSide) {
            return ItemStack.EMPTY;
        }
        ItemStack result;
        if (!disc.isEmpty()) {
            stop();
            result = disc;
            disc = ItemStack.EMPTY;
        } else if (!queue.isEmpty()) {
            result = queue.removeFirst();
        } else {
            return ItemStack.EMPTY;
        }
        setChanged();
        syncToClient();
        return result;
    }

    /** Clears and returns every held disc (inserted one plus queue) so the block can drop them. */
    public List<ItemStack> detachContents() {
        List<ItemStack> contents = new ArrayList<>();
        if (!disc.isEmpty()) {
            contents.add(disc);
        }
        contents.addAll(queue);
        disc = ItemStack.EMPTY;
        queue.clear();
        return contents;
    }

    /** Starts playback of the inserted disc via the vanilla jukebox level event. */
    public boolean playDisc() {
        if (level == null || level.isClientSide) {
            return false;
        }
        if (disc.isEmpty() && !queue.isEmpty()) {
            disc = queue.removeFirst();
            setChanged();
            syncToClient();
        }
        if (disc.isEmpty()) {
            return false;
        }
        var song = JukeboxSong.fromStack(level.registryAccess(), disc);
        if (song.isEmpty()) {
            return false;
        }
        int songId = level.registryAccess()
                .registryOrThrow(Registries.JUKEBOX_SONG)
                .getId(song.get().value());
        if (songId < 0) {
            return false;
        }
        level.levelEvent(null, LevelEvent.SOUND_PLAY_JUKEBOX_SONG, worldPosition, songId);
        playing = true;
        remainingTicks = song.get().value().lengthInTicks();
        setChanged();
        return true;
    }

    public void stop() {
        if (level == null || level.isClientSide) {
            return;
        }
        stopDiscPlayback();
        sequence.clear();
        sequenceCountdown = 0;
        setChanged();
    }

    /** Ends disc playback and active synth notes; a running note sequence is left untouched. */
    private void stopDiscPlayback() {
        if (level == null || level.isClientSide) {
            return;
        }
        if (playing) {
            level.levelEvent(null, LevelEvent.SOUND_STOP_JUKEBOX_SONG, worldPosition, 0);
        }
        playing = false;
        remainingTicks = 0;
        cutActiveSynth();
        setChanged();
    }

    /** Plays a note-block instrument note (0-24), matching vanilla note-block pitch. */
    public void playNote(NoteBlockInstrument instrument, int note) {
        if (level == null || level.isClientSide) {
            return;
        }
        float pitch = (float) Math.pow(2.0, (Mth.clamp(note, 0, 24) - 12) / 12.0);
        level.playSound(null, worldPosition, instrument.getSoundEvent().value(), SoundSource.RECORDS, 3.0F, pitch);
    }

    /**
     * Schedules a timed note sequence, replacing any sequence already running. Each step carries
     * the gap since the previous note; {@code stop()} cancels the sequence.
     */
    public void playSequence(List<SequenceNote> notes) {
        if (level == null || level.isClientSide) {
            return;
        }
        sequence.clear();
        if (notes.isEmpty()) {
            sequenceCountdown = 0;
            return;
        }
        sequence.addAll(notes);
        sequenceCountdown = notes.getFirst().delayTicks();
    }

    /** Plays due sequence notes: the head fires once its countdown runs out, then the next waits. */
    private void tickSequence() {
        if (sequence.isEmpty()) {
            return;
        }
        if (--sequenceCountdown > 0) {
            return;
        }
        SequenceNote note = sequence.removeFirst();
        playNote(note.instrument(), note.note());
        sequenceCountdown = sequence.isEmpty() ? 0 : sequence.getFirst().delayTicks();
    }

    /** Plays any registered sound event. Returns {@code false} if the id is unknown. */
    public boolean playSound(ResourceLocation soundId, float volume, float pitch) {
        if (level == null || level.isClientSide) {
            return false;
        }
        var registry = level.registryAccess().registryOrThrow(Registries.SOUND_EVENT);
        var event = registry.getOptional(soundId);
        if (event.isEmpty()) {
            return false;
        }
        level.playSound(null, worldPosition, event.get(), SoundSource.RECORDS, volume, pitch);
        return true;
    }

    /**
     * Song-end continuation for the playlist: with loop on, discs cycle (inserted disc joins the
     * queue end, the next one starts); with loop off, the finished disc is dropped and the next
     * queued disc starts. Returns {@code false} when playback should stop.
     */
    private boolean continuePlayback() {
        if (discLoop) {
            if (!queue.isEmpty()) {
                queue.add(disc);
                disc = queue.removeFirst();
                setChanged();
                syncToClient();
            }
            return playDisc();
        }
        if (!queue.isEmpty()) {
            if (!disc.isEmpty()) {
                dropDiscToGround(disc);
            }
            disc = queue.removeFirst();
            setChanged();
            syncToClient();
            return playDisc();
        }
        return false;
    }

    /** Server ticker: counts down disc playback (advancing the playlist or restarting in loop mode) and drives synth notes. */
    public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, SpeakerBlockEntity be) {
        if (be.playing && --be.remainingTicks <= 0) {
            Computed.LOGGER.info(
                    "Speaker at {}: song end (loop={}, queued={})",
                    be.worldPosition,
                    be.discLoop,
                    be.queue.size());
            if (!be.continuePlayback()) {
                be.stopDiscPlayback();
            }
        }
        be.tickSequence();
        be.tickWaveNote();
        be.tickSweep();
    }

    public static boolean isMusicDisc(net.minecraft.world.level.Level level, ItemStack stack) {
        return JukeboxSong.fromStack(level.registryAccess(), stack).isPresent()
                || stack.has(DataComponents.JUKEBOX_PLAYABLE);
    }

    private void syncToClient() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }}
