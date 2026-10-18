package dev.propulsionteam.computed.content;

import dev.propulsionteam.computed.Computed;
import dev.propulsionteam.computed.content.blocks.ComputerBlock;
import dev.propulsionteam.computed.content.blocks.ComputerBlockEntity;
import dev.propulsionteam.computed.content.blocks.ComputerBlockItem;
import dev.propulsionteam.computed.content.blocks.SpeakerBlock;
import dev.propulsionteam.computed.content.blocks.SpeakerBlockEntity;
import dev.propulsionteam.computed.content.items.LinkCardItem;
import dev.propulsionteam.computed.content.items.PortableMonitorItem;
import dev.propulsionteam.computed.content.items.ProgramDiskItem;
import dev.propulsionteam.computed.integration.geckolib.GeckoLibSupport;
import dev.propulsionteam.computed.content.monitors.MonitorBlock;
import dev.propulsionteam.computed.content.monitors.MonitorBlockEntity;
import dev.propulsionteam.computed.content.monitors.MonitorBlockItem;
import dev.propulsionteam.computed.menu.ComputerPeripheralMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ComputedRegistries {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Computed.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Computed.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Computed.MODID);
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, Computed.MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Computed.MODID);
    public static final DeferredRegister<net.minecraft.sounds.SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, Computed.MODID);

    /** Base tones for the speaker synthesizer; pitch-shifted per requested frequency. */
    public static final DeferredHolder<net.minecraft.sounds.SoundEvent, net.minecraft.sounds.SoundEvent> SPEAKER_WAVE_SINE =
            SOUND_EVENTS.register("speaker_wave_sine",
                    () -> net.minecraft.sounds.SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(Computed.MODID, "speaker_wave_sine")));
    public static final DeferredHolder<net.minecraft.sounds.SoundEvent, net.minecraft.sounds.SoundEvent> SPEAKER_WAVE_SQUARE =
            SOUND_EVENTS.register("speaker_wave_square",
                    () -> net.minecraft.sounds.SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(Computed.MODID, "speaker_wave_square")));
    public static final DeferredHolder<net.minecraft.sounds.SoundEvent, net.minecraft.sounds.SoundEvent> SPEAKER_WAVE_SAW =
            SOUND_EVENTS.register("speaker_wave_saw",
                    () -> net.minecraft.sounds.SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(Computed.MODID, "speaker_wave_saw")));
    public static final DeferredHolder<net.minecraft.sounds.SoundEvent, net.minecraft.sounds.SoundEvent> SPEAKER_WAVE_TRIANGLE =
            SOUND_EVENTS.register("speaker_wave_triangle",
                    () -> net.minecraft.sounds.SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(Computed.MODID, "speaker_wave_triangle")));
    public static final DeferredHolder<net.minecraft.sounds.SoundEvent, net.minecraft.sounds.SoundEvent> SPEAKER_WAVE_NOISE =
            SOUND_EVENTS.register("speaker_wave_noise",
                    () -> net.minecraft.sounds.SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(Computed.MODID, "speaker_wave_noise")));

    public static final DeferredHolder<MenuType<?>, MenuType<?>> COMPUTER_PERIPHERAL_MENU =
            MENU_TYPES.register(
                    "computer_peripheral",
                    () -> {
                        @SuppressWarnings("unchecked")
                        final MenuType<ComputerPeripheralMenu>[] holder = new MenuType[1];
                        holder[0] = IMenuTypeExtension.create((windowId, inv, buf) -> {
                            BlockPos pos = buf.readBlockPos();
                            BlockEntity be = inv.player.level().getBlockEntity(pos);
                            if (!(be instanceof ComputerBlockEntity computer)) {
                                throw new IllegalStateException("No computer block entity at " + pos);
                            }
                            return new ComputerPeripheralMenu(holder[0], windowId, inv, computer);
                        });
                        return holder[0];
                    });

    public static final DeferredBlock<ComputerBlock> COMPUTER_BLOCK =
            BLOCKS.register(
                    "computer",
                    props -> new ComputerBlock(
                            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()));
    public static final DeferredItem<BlockItem> COMPUTER_BLOCK_ITEM =
            ITEMS.register(
                    "computer",
                    () -> new ComputerBlockItem(COMPUTER_BLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ComputerBlockEntity>> COMPUTER_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register(
                    "computer",
                    () -> BlockEntityType.Builder.of(
                            ComputerBlockEntity::new,
                            COMPUTER_BLOCK.get()).build(null));

    public static final DeferredBlock<MonitorBlock> MONITOR_BLOCK =
            BLOCKS.register(
                    "monitor",
                    props -> new MonitorBlock(
                            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()));
    public static final DeferredItem<BlockItem> MONITOR_BLOCK_ITEM =
            ITEMS.register(
                    "monitor",
                    () -> new MonitorBlockItem(MONITOR_BLOCK.get(), new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MonitorBlockEntity>> MONITOR_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register(
                    "monitor",
                    () -> BlockEntityType.Builder.of(MonitorBlockEntity::new, MONITOR_BLOCK.get()).build(null));

    public static final DeferredBlock<SpeakerBlock> SPEAKER_BLOCK =
            BLOCKS.register(
                    "speaker",
                    props -> new SpeakerBlock(
                            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()));
    public static final DeferredItem<BlockItem> SPEAKER_BLOCK_ITEM =
            ITEMS.register(
                    "speaker",
                    () -> new BlockItem(SPEAKER_BLOCK.get(), new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SpeakerBlockEntity>> SPEAKER_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register(
                    "speaker",
                    () -> BlockEntityType.Builder.of(SpeakerBlockEntity::new, SPEAKER_BLOCK.get()).build(null));

    public static final DeferredItem<ProgramDiskItem> PROGRAM_DISK_ITEM = ITEMS.register(
            "program_disk", () -> new ProgramDiskItem(new Item.Properties().stacksTo(16)));

    public static final DeferredItem<LinkCardItem> LINK_CARD_ITEM =
            ITEMS.register("link_card", () -> new LinkCardItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<PortableMonitorItem> PORTABLE_MONITOR_ITEM = ITEMS.register(
            "portable_monitor",
            () -> GeckoLibSupport.isLoaded()
                    ? GeckoLibSupport.createPortableMonitor(new Item.Properties().stacksTo(1))
                    : new PortableMonitorItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB =
            CREATIVE_MODE_TABS.register(
                    "main",
                    () -> CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.computed"))
                            .icon(() -> COMPUTER_BLOCK_ITEM.get().getDefaultInstance())
                            .displayItems((parameters, output) -> {
                                output.accept(COMPUTER_BLOCK_ITEM.get());
                                output.accept(MONITOR_BLOCK_ITEM.get());
                                output.accept(SPEAKER_BLOCK_ITEM.get());
                                output.accept(PROGRAM_DISK_ITEM.get());
                                output.accept(dev.propulsionteam.computed.content.items.ExamplePrograms.widgetDemoDisk());
                                output.accept(LINK_CARD_ITEM.get());
                                output.accept(PORTABLE_MONITOR_ITEM.get());
                            })
                            .build());

    private ComputedRegistries() {}

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        MENU_TYPES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
    }
}
