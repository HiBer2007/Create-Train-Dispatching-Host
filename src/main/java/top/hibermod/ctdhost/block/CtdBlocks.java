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

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SwitchMachineBlockEntity>>
        SWITCH_MACHINE_BE = BLOCK_ENTITIES.register("switch_machine",
            () -> BlockEntityType.Builder.of(SwitchMachineBlockEntity::new,
                SWITCH_MACHINE.get()).build(null));

    /** 物品栏里的方块（供别的代码引用）。 */
    public static Item itemOf(Block block) {
        return block.asItem();
    }
}
