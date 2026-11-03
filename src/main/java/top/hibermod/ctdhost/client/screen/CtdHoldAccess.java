package top.hibermod.ctdhost.client.screen;

/**
 * 给 CRN 的显示器方块实体挂两个字段：扣车、可选信号系统列表。
 */
public interface CtdHoldAccess {

    String ctdgetHold();

    void ctdsetHold(String hold);

    String ctdgetSignalSystems();

    void ctdsetSignalSystems(String ids);

    boolean ctdgetNoBoarding();

    void ctdsetNoBoarding(boolean v);

}