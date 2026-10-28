package top.hibermod.ctdhost.item;

import java.util.List;

import com.simibubi.create.content.trains.entity.Train;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import top.hibermod.ctdhost.operation.TrainScripts;

/**
 * 脚本时刻表：把某个调度脚本绑定到一列车上。
 *
 * <h2>它是什么</h2>
 * 一张「票」——右键列车即让该车按票面脚本调度。票面存的是**内核 id**
 * （{@code DispatchKernels} 里注册的那个），不是文件路径：
 * 脚本热重载时 id 不变，票因此不会失效。
 *
 * <h2>它不是什么</h2>
 * 不是时刻表本体。具体的运行图由脚本自己决定，这张票只回答
 * 「这列车归哪个调度程序管」。
 *
 * <h2>为什么用 {@code CustomData} 而不是自定义 DataComponent</h2>
 * 只存一个字符串，用原版的 {@code minecraft:custom_data} 就够了，
 * 不必为此注册一个新的组件类型——少一个注册项就少一处版本迁移的负担。
 */
public class ScriptTimetableItem extends Item {

    public static final String SCRIPT_KEY = "ctdhost:script";

    public ScriptTimetableItem(Properties properties) {
        super(properties);
    }

    /** 票面脚本 id；未设置返回 {@code null}。 */
    public static ResourceLocation scriptOf(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag tag = data.copyTag();
        String s = tag.getString(SCRIPT_KEY);
        return s.isEmpty() ? null : ResourceLocation.tryParse(s);
    }

    /** 改写票面脚本。 */
    public static ItemStack withScript(ItemStack stack, ResourceLocation kernelId) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack,
            tag -> tag.putString(SCRIPT_KEY, kernelId.toString()));
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        ResourceLocation id = scriptOf(stack);
        if (id == null) {
            return Component.translatable("item.ctdhost.script_timetable.blank");
        }
        return Component.translatable("item.ctdhost.script_timetable.named", id.toString());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        ResourceLocation id = scriptOf(stack);
        if (id == null) {
            tooltip.add(Component.translatable("item.ctdhost.script_timetable.hint.blank")
                .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("item.ctdhost.script_timetable.hint.bound",
                id.toString()).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("item.ctdhost.script_timetable.hint.usage")
            .withStyle(ChatFormatting.DARK_GRAY));
    }

    /**
     * 右键使用：把票面脚本绑到**最近的一列车**上。
     *
     * <p>为什么不直接点列车实体：Create 的列车不是 {@code Entity}，
     * 而是一组 {@code CarriageContraptionEntity}。要走「点实体」这条路，
     * 得给 {@code AbstractContraptionEntity#interact} 加注入，而那条注入
     * 无法在无头环境验证。先用「最近的车」这个规则：行为可预测、可测，
     * 且不依赖任何 Create 内部结构。后续可以换成实体交互而不改语义。
     */
    @Override
    public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext ctx) {
        net.minecraft.world.level.Level level = ctx.getLevel();
        if (level.isClientSide) return net.minecraft.world.InteractionResult.SUCCESS;

        ResourceLocation id = scriptOf(ctx.getItemInHand());
        if (id == null) return net.minecraft.world.InteractionResult.PASS;

        Train train = nearestTrain(level, ctx.getClickedPos().getCenter(), 48.0);
        if (train == null) {
            if (ctx.getPlayer() != null) {
                ctx.getPlayer().displayClientMessage(
                    Component.translatable("item.ctdhost.script_timetable.no_train")
                        .withStyle(ChatFormatting.RED), true);
            }
            return net.minecraft.world.InteractionResult.FAIL;
        }

        TrainScripts.bind(train.id, id);
        if (ctx.getPlayer() != null) {
            ctx.getPlayer().displayClientMessage(
                Component.translatable("item.ctdhost.script_timetable.bound", id.toString(),
                    train.name == null ? train.id.toString() : train.name.getString())
                    .withStyle(ChatFormatting.GREEN), true);
        }
        return net.minecraft.world.InteractionResult.CONSUME;
    }

    /** 找最近的列车（按车厢实体位置算）。 */
    private static Train nearestTrain(net.minecraft.world.level.Level level,
                                      net.minecraft.world.phys.Vec3 from, double maxDistance) {
        Train best = null;
        double bestDist = maxDistance * maxDistance;
        for (Train train : com.simibubi.create.Create.RAILWAYS.trains.values()) {
            if (train == null || train.carriages == null) continue;
            for (var carriage : train.carriages) {
                var entity = carriage.anyAvailableEntity();
                if (entity == null) continue;
                double d = entity.position().distanceToSqr(from);
                if (d < bestDist) {
                    bestDist = d;
                    best = train;
                }
            }
        }
        return best;
    }

    /**
     * 把票面脚本绑到一列车上。
     *
     * @return 是否绑定成功（票面为空或列车无效时返回 false）
     */
    public static boolean applyTo(ItemStack stack, Train train) {
        ResourceLocation id = scriptOf(stack);
        if (id == null || train == null || train.id == null) return false;
        TrainScripts.bind(train.id, id);
        return true;
    }
}
