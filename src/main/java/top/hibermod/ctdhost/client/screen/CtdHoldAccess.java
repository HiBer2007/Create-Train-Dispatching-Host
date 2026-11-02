package top.hibermod.ctdhost.client.screen;

/**
 * 给 CRN 的显示器方块实体挂一个「扣车」字段。
 *
 * <p>为什么不用服务端静态表：CRN 的显示器**自己携带数据**（`AdvancedDisplayBlockEntity`
 * 里有 `NBT_TRAIN_STOPS` 这种字段），随 BE 一起同步给客户端。
 * 我们照这个机制加一个自己的字段，就自动获得了同步——不需要自造方块、也不需要自己发包。
 */
public interface CtdHoldAccess {

    String ctdgetHold();

    void ctdsetHold(String hold);
}