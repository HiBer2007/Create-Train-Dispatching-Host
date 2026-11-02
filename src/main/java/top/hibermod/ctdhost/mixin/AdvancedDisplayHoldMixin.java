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
 * 照 CRN 自己的做法：字段落在 BE 上，进存档、进同步 tag。
 * 服务端每 tick 自查 ctdapi 的 HoldStates，有变化就写字段并发包。
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

    /**
     * 服务端每 tick 自查：按自己的「车站 + 站台」从 HoldStates 取扣车，
     * 有变化就写字段并发包。这样宿主不必扫描世界里的显示器。
     */
    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void ctd$syncHold(CallbackInfo ci) {
        AdvancedDisplayBlockEntity self = (AdvancedDisplayBlockEntity) (Object) this;
        var level = self.getLevel();
        if (level == null || level.isClientSide) return;
        if (!(self.getSettings() instanceof top.hibermod.ctdhost.client.screen.CtdPlatformSettings s)) return;

        boolean held = top.hibermod.ctdapi.status.HoldStates.isHeld(s.station(), s.platform());
        String want = held ? (s.station().isEmpty() ? "本站台" : s.station()) : "";
        if (!want.equals(ctdHold)) {
            ctdHold = want;
            self.setChanged();
            level.sendBlockUpdated(self.getBlockPos(), self.getBlockState(), self.getBlockState(), 3);
        }
    }
}