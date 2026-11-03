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
 * 给 CRN 的显示器方块实体加两个字段：扣车、可选的信号系统 id 列表。
 *
 * 照 CRN 自己的做法：字段落在 BE 上，进存档、进同步 tag。
 * 服务端每 tick 自查 ctdapi 的 HoldStates，有变化就写字段并发包。
 */
@Mixin(AdvancedDisplayBlockEntity.class)
public abstract class AdvancedDisplayHoldMixin implements CtdHoldAccess {

    @Unique
    private String ctdHold = "";

    @Unique
    private String ctdSignalSystems = "";

    /** 调度给出的「请勿上车」标记。 */
    @Unique
    private boolean ctdNoBoarding = false;

    /** 调度发布的公告文本，空串表示无公告。 */
    @Unique
    private String ctdNotice = "";

    @Override
    public String ctdgetHold() {
        return ctdHold;
    }

    @Override
    public void ctdsetHold(String hold) {
        ctdHold = hold == null ? "" : hold;
    }

    @Override
    public String ctdgetSignalSystems() {
        return ctdSignalSystems;
    }

    @Override
    public boolean ctdgetNoBoarding() {
        return ctdNoBoarding;
    }

    @Override
    public void ctdsetNoBoarding(boolean v) {
        ctdNoBoarding = v;
    }

    @Override
    public String ctdgetNotice() {
        return ctdNotice;
    }

    @Override
    public void ctdsetNotice(String v) {
        ctdNotice = v == null ? "" : v;
    }

    public void ctdsetNoBoardingDummy(boolean v) {
        ctdNoBoarding = v;
    }

    @Override
    public void ctdsetSignalSystems(String ids) {
        ctdSignalSystems = ids == null ? "" : ids;
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"), require = 0)
    private void ctd$save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries, CallbackInfo ci) {
        tag.putString("CtdHold", ctdHold);
        tag.putString("CtdSignalSystems", ctdSignalSystems);
        tag.putBoolean("CtdNoBoarding", ctdNoBoarding);
        tag.putString("CtdNotice", ctdNotice);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"), require = 0)
    private void ctd$load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries, CallbackInfo ci) {
        ctdHold = tag.getString("CtdHold");
        ctdSignalSystems = tag.getString("CtdSignalSystems");
        ctdNoBoarding = tag.getBoolean("CtdNoBoarding");
        ctdNotice = tag.getString("CtdNotice");
    }

    @Inject(method = "getUpdateTag", at = @At("RETURN"), require = 0)
    private void ctd$updateTag(net.minecraft.core.HolderLookup.Provider registries,
                               CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag tag = cir.getReturnValue();
        if (tag != null) {
            tag.putString("CtdHold", ctdHold);
            tag.putString("CtdSignalSystems", ctdSignalSystems);
        tag.putBoolean("CtdNoBoarding", ctdNoBoarding);
        tag.putString("CtdNotice", ctdNotice);
        }
    }

    @Inject(method = "loadWithComponents", at = @At("TAIL"), require = 0)
    private void ctd$loadWithComponents(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries,
                                        CallbackInfo ci) {
        ctdHold = tag.getString("CtdHold");
        ctdSignalSystems = tag.getString("CtdSignalSystems");
        ctdNoBoarding = tag.getBoolean("CtdNoBoarding");
        ctdNotice = tag.getString("CtdNotice");

        AdvancedDisplayBlockEntity self = (AdvancedDisplayBlockEntity) (Object) this;
        if (self.getLevel() != null && self.getLevel().isClientSide && !ctdSignalSystems.isEmpty()) {
            top.hibermod.ctdhost.client.screen.CtdSignalSystemData.update(ctdSignalSystems);
        }
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

        // 顺手写一份信号系统 id 列表：客户端下拉框靠它填充。
        StringBuilder sb = new StringBuilder();
        for (var sys : top.hibermod.ctdapi.signal.SignalRegistries.systems()) {
            if (sb.length() > 0) sb.append((char) 44);
            sb.append(sys.id().toString());
        }
        String wantSys = sb.toString();
        if (!wantSys.equals(ctdSignalSystems)) {
            ctdSignalSystems = wantSys;
            self.setChanged();
            level.sendBlockUpdated(self.getBlockPos(), self.getBlockState(), self.getBlockState(), 3);
        }

        String notice = top.hibermod.ctdapi.status.Announcements
            .get(s.station(), s.platform());
        if (!notice.equals(ctdNotice)) {
            ctdNotice = notice;
            self.setChanged();
            level.sendBlockUpdated(self.getBlockPos(), self.getBlockState(), self.getBlockState(), 3);
        }

        boolean noBoard = top.hibermod.ctdapi.status.NotBoardingStates
            .isBlocked(s.station(), s.platform());
        if (noBoard != ctdNoBoarding) {
            ctdNoBoarding = noBoard;
            self.setChanged();
            level.sendBlockUpdated(self.getBlockPos(), self.getBlockState(), self.getBlockState(), 3);
        }

        boolean held = top.hibermod.ctdapi.status.HoldStates.isHeld(s.station(), s.platform());
        String want = held ? (s.station().isEmpty() ? "本台" : s.station()) : "";
        if (!want.equals(ctdHold)) {
            ctdHold = want;
            self.setChanged();
            level.sendBlockUpdated(self.getBlockPos(), self.getBlockState(), self.getBlockState(), 3);
        }
    }
}