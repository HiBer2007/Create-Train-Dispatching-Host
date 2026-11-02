package top.hibermod.ctdhost.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity;
import net.minecraft.nbt.CompoundTag;

import top.hibermod.ctdhost.client.screen.CtdHoldAccess;

/**
 * 给 CRN 的显示器方块实体加「扣车」字段。
 *
 * <p>照 CRN 自己的做法：字段落在 BE 上，进存档、进同步 tag。
 * 这样客户端 BER 直接读 BE 就能拿到，不需要我们另建同步通道。
 */
@Mixin(AdvancedDisplayBlockEntity.class)
public abstract class AdvancedDisplayHoldMixin implements CtdHoldAccess {

    @Unique
    private String ctdHold = "";

    @Override
    public String ctdgetHold() {
        return ctdHold;
    }

    @Override
    public void ctdsetHold(String hold) {
        ctdHold = hold == null ? "" : hold;
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"), require = 0)
    private void ctd$save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries, CallbackInfo ci) {
        tag.putString("CtdHold", ctdHold);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"), require = 0)
    private void ctd$load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries, CallbackInfo ci) {
        ctdHold = tag.getString("CtdHold");
    }

    @Inject(method = "getUpdateTag", at = @At("RETURN"), require = 0)
    private void ctd$updateTag(net.minecraft.core.HolderLookup.Provider registries,
                               CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag tag = cir.getReturnValue();
        if (tag != null) tag.putString("CtdHold", ctdHold);
    }

    @Inject(method = "loadWithComponents", at = @At("TAIL"), require = 0)
    private void ctd$loadWithComponents(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries,
                                        CallbackInfo ci) {
        ctdHold = tag.getString("CtdHold");
    }
}