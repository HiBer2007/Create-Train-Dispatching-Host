package top.hibermod.ctdhost.block;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import top.hibermod.ctdhost.CtdHost;

/**
 * 方块与方块实体的注册。
 *
 * <p>宿主允许注册方块（API 层连 assets 都不许有，见 {@code mods/README.md} 的边界）。
 */
public final class CtdBlocks {

    private CtdBlocks() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CtdHost.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CtdHost.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CtdHost.MOD_ID);

    /**
     * 自动道岔机。
     *
     * <p>**只有装了它的岔口**才允许信号系统自动扳动；没装的岔口走固定方向
     * （有直行就直行，否则始终左转）。
     */
    public static final DeferredBlock<SwitchMachineBlock> SWITCH_MACHINE =
        BLOCKS.register("switch_machine", () -> new SwitchMachineBlock(SwitchMachineBlock.defaultProperties()));

    public static final DeferredItem<BlockItem> SWITCH_MACHINE_ITEM =
        ITEMS.registerSimpleBlockItem(SWITCH_MACHINE);

    // ── K5：CTD 自己的信号机本体 ───────────────────────────────────────
    // 继承 Create 的 SignalBlock：边界、红石强制红、链式解析都照旧，只换外观与渲染。
    public static final DeferredBlock<CtdSignalBlock> CTD_SIGNAL =
        BLOCKS.register("ctd_signal", () -> new CtdSignalBlock(CtdSignalBlock.defaultProperties()));

    public static final DeferredItem<BlockItem> CTD_SIGNAL_ITEM =
        ITEMS.registerSimpleBlockItem(CTD_SIGNAL);

    // ── K5：灯头与金属梁 ───────────────────────────────────────────────
    public static final DeferredBlock<CtdSignalHeadBlock> SIGNAL_HEAD =
        BLOCKS.register("signal_head", () -> new CtdSignalHeadBlock(CtdSignalHeadBlock.defaultProperties()));
    public static final DeferredItem<BlockItem> SIGNAL_HEAD_ITEM =
        ITEMS.registerSimpleBlockItem(SIGNAL_HEAD);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CtdSignalHeadBlockEntity>> SIGNAL_HEAD_BE =
        BLOCK_ENTITIES.register("signal_head",
            () -> BlockEntityType.Builder.of(CtdSignalHeadBlockEntity::create, SIGNAL_HEAD.get()).build(null));


    // 必须有自己的 BE 类型：BlockEntityType 会校验 validBlocks，
    // 复用 Create 的 TRACK_SIGNAL 会在放置时报「Invalid block entity」。
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CtdSignalBlockEntity>> CTD_SIGNAL_BE =
        BLOCK_ENTITIES.register("ctd_signal",
            () -> BlockEntityType.Builder.<CtdSignalBlockEntity>of(
                CtdSignalBlockEntity::create,
                CTD_SIGNAL.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SwitchMachineBlockEntity>>
        SWITCH_MACHINE_BE = BLOCK_ENTITIES.register("switch_machine",
            () -> BlockEntityType.Builder.of(SwitchMachineBlockEntity::new,
                SWITCH_MACHINE.get()).build(null));

    /** 物品栏里的方块（供别的代码引用）。 */
    public static Item itemOf(Block block) {
        return block.asItem();
    }
}
