package top.hibermod.ctdhost.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.trains.graph.DimensionPalette;
import com.simibubi.create.content.trains.signal.SignalBoundary;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import top.hibermod.ctdapi.signal.SignalSystemHolder;

/**
 * 给 Create 的信号机加上「所属信号制度」。
 *
 * <h2>为什么必须 mixin</h2>
 * 这个属性是信号机的，不是我们外挂表的。只有存进它自己的 NBT 与网络包，
 * 它才会跟着拆装、存档、同步一起走；外挂表在信号机被拆掉之后只会留下脏数据，
 * 而客户端永远读不到。
 *
 * <h2>为什么要两个方向都注入</h2>
 * NBT（存档）与 {@link FriendlyByteBuf}（同步）是两条独立路径。
 * 只写 NBT，客户端拿到的制度永远是空的——表现为「GUI 里选了，
 * 界面重新打开又变回去了」。
 */
@Mixin(SignalBoundary.class)
public abstract class SignalBoundaryMixin implements SignalSystemHolder {

    @Unique
    @Nullable
    private ResourceLocation ctd$system;

    @Override
    public @Nullable ResourceLocation ctd$signalSystem() {
        return ctd$system;
    }

    @Override
    public void ctd$setSignalSystem(@Nullable ResourceLocation systemId) {
        this.ctd$system = systemId;
        // 边点本身没有 setChanged：它随图的同步一起发出去，
        // 下次 tick 就会带上新值
    }

    @Inject(method = "write(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;Lcom/simibubi/create/content/trains/graph/DimensionPalette;)V",
            at = @At("TAIL"))
    private void ctd$writeNbt(CompoundTag nbt, HolderLookup.Provider registries,
                              DimensionPalette dimensions, CallbackInfo ci) {
        if (ctd$system != null) {
            nbt.putString("CtdSignalSystem", ctd$system.toString());
        }
    }

    @Inject(method = "read(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;ZLcom/simibubi/create/content/trains/graph/DimensionPalette;)V",
            at = @At("TAIL"))
    private void ctd$readNbt(CompoundTag nbt, HolderLookup.Provider registries, boolean migration,
                             DimensionPalette dimensions, CallbackInfo ci) {
        ctd$system = nbt.contains("CtdSignalSystem")
            ? ResourceLocation.tryParse(nbt.getString("CtdSignalSystem"))
            : null;
    }

    @Inject(method = "write(Lnet/minecraft/network/FriendlyByteBuf;Lcom/simibubi/create/content/trains/graph/DimensionPalette;)V",
            at = @At("TAIL"))
    private void ctd$writeBuf(FriendlyByteBuf buffer, DimensionPalette dimensions, CallbackInfo ci) {
        buffer.writeBoolean(ctd$system != null);
        if (ctd$system != null) {
            buffer.writeResourceLocation(ctd$system);
        }
    }

    @Inject(method = "read(Lnet/minecraft/network/FriendlyByteBuf;Lcom/simibubi/create/content/trains/graph/DimensionPalette;)V",
            at = @At("TAIL"))
    private void ctd$readBuf(FriendlyByteBuf buffer, DimensionPalette dimensions, CallbackInfo ci) {
        ctd$system = buffer.readBoolean() ? buffer.readResourceLocation() : null;
    }
}
