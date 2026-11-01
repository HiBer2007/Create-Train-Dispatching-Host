package top.hibermod.ctdhost.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.Block;

/**
 * 金属梁段：高杆信号机的杆。
 *
 * 它不存状态，只提供「向上连到哪」的几何。
 * 灯头会沿着同一直线上的梁段往上找，直到找到信号机本体或顶端。
 */
public class CtdSignalMastBlock extends Block {

    public static final MapCodec<CtdSignalMastBlock> CODEC = simpleCodec(CtdSignalMastBlock::new);

    public CtdSignalMastBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    public static Properties defaultProperties() {
        return Properties.of()
            .mapColor(net.minecraft.world.level.material.MapColor.METAL)
            .strength(1.5f)
            .noOcclusion();
    }
}